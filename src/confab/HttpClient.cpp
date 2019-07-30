#include "HttpClient.hpp"

#include "Asset.hpp"
#include "Constants.hpp"
#include "Record.hpp"
#include "schemas/FlatAsset_generated.h"
#include "schemas/FlatAssetData_generated.h"

#include "glog/logging.h"
#include "libbase64.h"
#include "pistache/net.h"
#include "pistache/http.h"
#include "pistache/client.h"
#include "xxhash.h"

#include <cstring>
#include <experimental/filesystem>
#include <inttypes.h>
#include <fstream>
#include <limits>

namespace fs = std::experimental::filesystem;

namespace Confab {

/*! Record class to hold the string values returned by Pistache and allow us to deserialize records from them
 * without additional copies.
 */
class ClientRecord : public Record {
public:
    /*! Default constructor not supported, use makeEmptyRecord().
     */
    ClientRecord() = delete;

    /*! Construct a record that wraps a Pistache response string.
     *
     * \param bytes The decoded buffer to wrap.
     * \param size The size of the buffer in bytes.
     */
    ClientRecord(const uint8_t* bytes, size_t size) :
        m_data(bytes, size) {
    }

    /*! Deletes a ClientRecord, does nothing because the underlying string is not owned by this class.
     */
    ~ClientRecord() override { }

    /*! True if this ClientRecord has no results.
     *
     * \return true if this Record is empty, false if it has content.
     */
    bool empty() const override { return m_data.data() == nullptr; }

    /*! A pointer to the data contents of this Record.
     *
     * \return A pointer to the data contents of this Record.
     */
    const SizedPointer data() const override {
        return m_data;
    }

    /*! A pointer to the key contents of this Record.
     *
     * \return Always empty.
     */
    const SizedPointer key() const override {
        return SizedPointer();
    }

private:
    const SizedPointer m_data;
};

HttpClient::HttpClient(const std::string& serverAddress) :
    m_serverAddress(serverAddress),
    m_client(new Pistache::Http::Client),
    m_distribution(0, std::numeric_limits<uint64_t>::max()) {
    auto opts = Pistache::Http::Client::options()
        .keepAlive(true)
        .maxConnectionsPerHost(16)
        .threads(2);
    m_client->init(opts);
}

HttpClient::~HttpClient() {
}

void HttpClient::getAsset(uint64_t key, std::function<void(uint64_t, RecordPtr)> callback) {
    std::string request = m_serverAddress + "/asset/" + Asset::keyToString(key);
    LOG(INFO) << "issuing Asset request to " << request;

    auto promise = m_client->get(request).send();
    promise.then([&key, &callback, &request](Pistache::Http::Response response) {
        if (response.code() == Pistache::Http::Code::Ok) {
            LOG(INFO) << "received Ok response for Asset request " << request;
            uint8_t decoded[kPageSize];
            size_t decodedSize;
            base64_decode(response.body().c_str(), response.body().size(), reinterpret_cast<char*>(decoded),
                &decodedSize, 0);
            // Verify the Asset record as returned by the server.
            RecordPtr flatAsset(new ClientRecord(decoded, decodedSize));
            auto verifier = flatbuffers::Verifier(decoded, decodedSize);
            if (Data::VerifyFlatAssetBuffer(verifier)) {
                callback(key, flatAsset);
            } else {
                LOG(ERROR) << "failed to verify server-provided data for Asset request " << request;
                callback(key, makeEmptyRecord());
            }
        } else {
            LOG(ERROR) << "error code " << response.code() << " on Asset request " << request;
            callback(key, makeEmptyRecord());
        }
    }, Pistache::Async::IgnoreException);

    Pistache::Async::Barrier barrier(promise);
    barrier.wait();
}

void HttpClient::getAssetData(uint64_t key, uint64_t chunk,
    std::function<void(uint64_t, uint64_t, RecordPtr)> callback) {
    char numBuf[32];
    snprintf(numBuf, 32, "%" PRIu64, chunk);
    std::string request = m_serverAddress + "/asset_data/" + Asset::keyToString(key) + "/" + std::string(numBuf);
    LOG(INFO) << "issuing AssetData request to " << request;

    auto promise = m_client->get(request).send();
    promise.then([&key, &chunk, &callback, &request](Pistache::Http::Response response) {
        if (response.code() == Pistache::Http::Code::Ok) {
            LOG(INFO) << "received Ok response for AssetData request " << request;
            uint8_t decoded[kPageSize];
            size_t decodedSize;
            base64_decode(response.body().c_str(), response.body().size(), reinterpret_cast<char*>(decoded),
                &decodedSize, 0);
            RecordPtr flatAssetData(new ClientRecord(decoded, decodedSize));
            auto verifier = flatbuffers::Verifier(decoded, decodedSize);
            if (Data::VerifyFlatAssetDataBuffer(verifier)) {
                callback(key, chunk, flatAssetData);
            } else {
                LOG(ERROR) << "failed to verify server-provided data for AssetData request " << request;
                callback(key, chunk, makeEmptyRecord());
            }
        } else {
            LOG(ERROR) << "error code " << response.code() << " on AssetData request " << request;
            callback(key, chunk, makeEmptyRecord());
        }
    }, Pistache::Async::IgnoreException);

    Pistache::Async::Barrier barrier(promise);
    barrier.wait();
}

uint64_t HttpClient::postInlineAsset(Asset::Type type, const std::string& name, uint64_t author, uint64_t deprecates,
        uint64_t size, const uint8_t* inlineData) {
    if (size > kSingleChunkDataSize) {
        LOG(ERROR) << "attempt to post inline Asset of size " << size << " greater than max of "
            << kSingleChunkDataSize;
        return 0;
    }

    Asset asset(type);
    asset.setName(name);
    asset.setAuthor(author);
    asset.setDeprecates(deprecates);
    // For short assets we add some random salt to the hash, to help avoid hash collisions.
    asset.setSalt(m_distribution(m_randomDevice));
    uint64_t key = XXH64(inlineData, size, asset.salt());
    asset.setKey(key);
    asset.setSize(size);
    flatbuffers::FlatBufferBuilder builder(kPageSize);
    asset.flatten(builder, inlineData);

    std::string request = m_serverAddress + "/asset/" + Asset::keyToString(key);
    LOG(INFO) << "sending POST for new inline asset " << request << ", " << builder.GetSize() << " bytes";

    char base64[kPageSize];
    size_t encodedSize = 0;
    base64_encode(reinterpret_cast<const char*>(builder.GetBufferPointer()), builder.GetSize(), base64, &encodedSize,
        0);
    CHECK_LT(encodedSize, kPageSize) << "encoded inline asset larger than page size";

    bool ok = true;
    auto promise = m_client->post(request)
        .header<Pistache::Http::Header::ContentType>(MIME(Application, OctetStream))
        .header<Pistache::Http::Header::ContentLength>(encodedSize)
        .body(std::string(base64, encodedSize))
        .send();
    promise.then([&request, &ok](Pistache::Http::Response response) {
        if (response.code() == Pistache::Http::Code::Ok) {
            LOG(INFO) << "received ok response for inline asset post " << request;
        } else {
            LOG(ERROR) << "error code " << response.code() << " on inline Asset post " << request;
            ok = false;
        }
    }, Pistache::Async::IgnoreException);

    Pistache::Async::Barrier barrier(promise);
    barrier.wait();

    if (!ok) {
        return 0;
    }

    return key;
}

uint64_t HttpClient::postFileAsset(Asset::Type type, const std::string& name, uint64_t author, uint64_t deprecates,
        const fs::path& assetFile) {
    // Some Assets like images make sense to serialize to a file, regardless of size, because SuperCollider has no
    // concept of loading an image from a binary blob of memory.
    size_t fileSize = fs::file_size(assetFile);
    if (fileSize == 0) {
        LOG(ERROR) << "rejecting addition of zero-size file at " << assetFile;
        return 0;
    }

    // First we must hash the file. This means we will be traversing this file twice, first for a hash and then second
    // for the upload. This could be avoided at the cost of loading the file entirely in to memory, or possibly by
    // designing some incremental upload that specifies the Asset key *last*, but in the interest of simplicity the
    // current design is to just traverse the file twice. We even recompute the hash twice because storage of the
    // intermediate hashes for large files is on the order of megabytes.
    std::array<char, kDataChunkSize> fileChunk;
    std::ifstream inFile(assetFile, std::ios::in | std::ios::binary);
    if (!inFile) {
        LOG(ERROR) << "error opening file: " << assetFile << " for hash computation";
        return 0;
    }
    XXH64_state_t* hashState = XXH64_createState();
    XXH64_reset(hashState, 0);
    inFile.read(fileChunk.data(), kDataChunkSize);
    size_t bytesRead = inFile.gcount();
    size_t bytesRemaining = fileSize - bytesRead;

    while (inFile && bytesRemaining > 0) {
        XXH64_update(hashState, fileChunk.data(), bytesRead);
        inFile.read(fileChunk.data(), kDataChunkSize);
        bytesRead = inFile.gcount();
        bytesRemaining -= bytesRead;
    }

    // Compute any remaining hash bytes.
    if (bytesRead > 0) {
        XXH64_update(hashState, fileChunk.data(), bytesRead);
    }

    uint64_t key = XXH64_digest(hashState);
    // Free this now, we can re-allocate later, just to avoid potentially leaking it on the various exit paths from this
    // function.
    XXH64_freeState(hashState);

    if (bytesRemaining > 0) {
        LOG(ERROR) << "file read error for " << assetFile << ", " << bytesRemaining << " bytes left unread.";
        return 0;
    }

    std::string keyString = Asset::keyToString(key);
    LOG(INFO) << "computed key " << keyString << " for asset file " << assetFile;

    Asset asset(type);
    asset.setKey(key);
    asset.setName(name);
    asset.setFileExtension(assetFile.extension());
    asset.setAuthor(author);
    asset.setDeprecates(deprecates);
    asset.setSize(fileSize);
    asset.setChunks((fileSize / kDataChunkSize) + 1);
    flatbuffers::FlatBufferBuilder builder(kPageSize);
    asset.flatten(builder);

    char base64[kPageSize];
    size_t encodedSize = 0;
    base64_encode(reinterpret_cast<char*>(builder.GetBufferPointer()), builder.GetSize(), base64, &encodedSize, 0);
    CHECK_LT(encodedSize, kPageSize) << "encoded file asset record larger than page.";
    LOG(INFO) << "sending POST of file asset " << keyString << ", " << encodedSize << " bytes.";

    std::string request = m_serverAddress + "/asset/" + keyString;
    bool ok = true;
    auto promise = m_client->post(request)
        .body(std::string(base64, encodedSize))
        .send();
    promise.then([&request, &ok](Pistache::Http::Response response) {
        if (response.code() == Pistache::Http::Code::Ok) {
            LOG(INFO) << "received ok response on file asset post " << request;
        } else {
            LOG(ERROR) << "error code " << response.code() << " on file Asset post " << request;
            ok = false;
        }
    }, Pistache::Async::IgnoreException);

    Pistache::Async::Barrier barrier(promise);
    barrier.wait();

    if (!ok) {
        LOG(INFO) << "error posting new file asset " << assetFile << " with key " << keyString;
        return 0;
    }

    // Now we upload the individual data chunks of the file. We will be reading the bytes directly into the
    // FlatBufferBuilder object, to skip a copy.
    hashState = XXH64_createState();
    XXH64_reset(hashState, 0);
    inFile.clear();
    inFile.seekg(0, std::ios::beg);
    bytesRemaining = fileSize;
    size_t chunk = 0;
    uint64_t chunkHash = 0;

    while (ok && inFile && bytesRemaining > 0) {
        builder.Clear();
        uint8_t* flatData = nullptr;
        size_t flatDataSize = std::min(kDataChunkSize, bytesRemaining);
        auto flatAssetData = builder.CreateUninitializedVector(flatDataSize, &flatData);
        inFile.read(reinterpret_cast<char*>(flatData), flatDataSize);
        bytesRead = inFile.gcount();
        bytesRemaining -= bytesRead;
        if (bytesRead != flatDataSize) {
            LOG(ERROR) << "error re-reading asset file " << assetFile << " expected " << flatDataSize << " bytes, got "
                << bytesRead << " bytes instead.";
            ok = false;
        } else {
            Data::FlatAssetDataBuilder assetDataBuilder(builder);
            assetDataBuilder.add_data(flatAssetData);
            XXH64_update(hashState, flatData, bytesRead);
            chunkHash = XXH64_digest(hashState);
            assetDataBuilder.add_hash(chunkHash);
            auto assetData = assetDataBuilder.Finish();
            builder.Finish(assetData);

            base64_encode(reinterpret_cast<char*>(builder.GetBufferPointer()), builder.GetSize(), base64, &encodedSize,
                0);
            CHECK_LT(encodedSize, kPageSize) << "stack overrun on encode!";

            LOG(INFO) << "sending POST of asset data for " << keyString << " chunk " << chunk << ", " << encodedSize
                << " bytes.";
            char numBuf[32];
            snprintf(numBuf, 32, "%" PRIu64, chunk);
            request = m_serverAddress + "/asset_data/" + keyString + "/" + std::string(numBuf);
            promise = m_client->post(request)
                .body(std::string(base64, encodedSize))
                .send();
            promise.then([&request, &ok](Pistache::Http::Response response) {
                if (response.code() == Pistache::Http::Code::Ok) {
                    LOG(INFO) << "recived ok response on file asset chunk post " << request;
                } else {
                    LOG(ERROR) << "error code " << response.code() << " on file asset chunk post " << request;
                    ok = false;
                }
            }, Pistache::Async::IgnoreException);

            Pistache::Async::Barrier chunkBarrier(promise);
            chunkBarrier.wait();
            ++chunk;
        }
    }

    XXH64_freeState(hashState);

    // Digest hash of final chunk should match the overall hash of the file.
    if (!ok || bytesRemaining > 0 || chunkHash != key) {
        LOG(ERROR) << "error uploading file " << assetFile << " to server.";
        return 0;
    }

    LOG(INFO) << "completed successful upload of file Asset " << keyString << " from " << assetFile;
    return key;
}

void HttpClient::shutdown() {
    m_client->shutdown();
}

}  // namespace Confab

