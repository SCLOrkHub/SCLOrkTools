#include "HttpClient.hpp"

#include "Asset.hpp"
#include "Constants.hpp"
#include "schemas/FlatAsset_generated.h"
#include "schemas/FlatAssetData_generated.h"

#include "glog/logging.h"
#include "pistache/net.h"
#include "pistache/http.h"
#include "pistache/client.h"
#include "xxhash.h"

#include <cstring>
#include <experimental/filesystem>
#include <inttypes.h>
#include <fstream>
#include <limits>
#include <string_view>

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
     * \param responseString The string to wrap.
     */
    ClientRecord(const std::string& responseString) :
        m_data(reinterpret_cast<const uint8_t*>(responseString.c_str()), responseString.size()) {
    }

    /*! Deletes a ClientRecord, does nothing because the underlying string is not owned by this class.
     */
    ~ClientRecord() override { }

    /*! True if this ClientRecord has no results.
     *
     * \return true if this Record is empty, false if it has content.
     */
    bool empty() const override { return m_data.empty() }

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
}

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

    auto response = m_client->get(request).send();
    response.then([&key, &callback, &request](Pistache::Http::Response response) {
        if (response.code() == Pistache::Http::Code::Ok) {
            LOG(INFO) << "received Ok response for Asset request " << request;
            callback(key, RecordPtr(new ClientRecord(response.body()));
        } else {
            LOG(ERROR) << "error code " << response.code() << "on Asset request " << request;
            callback(key, makeEmptyRecord());
        }
    });

    response.wait();
}

bool HttpClient::getAssetData(uint64_t key, uint64_t fileSize, uint64_t numChunks, const fs::path& path) {
    auto keyString = Asset::keyToString(key);
    LOG(INFO) << "downloading Asset data for " << keyString << ", " << numChunks << " chunks "
        << fileSize << " bytes, into file " << path;

    std::ofstream outFile(path, std::ios::out | std::ios::binary | std::ios::trunc);
    if (!outFile) {
        LOG(ERROR) << "error opening file " << path << " for writing.";
        return false;
    }

    uint64_t totalSize = 0;
    uint64_t digest = 0;
    std::string requestBase = m_serverAddress + "/asset_data/" + keyString + "/";
    XXH64_state_t* hashState = XXH64_createState();
    XXH64_reset(hashState, 0);
    bool ok = true;

    for (uint64_t i = 0; i < numChunks; ++i) {
        char numBuf[32];
        snprintf(numBuf, 32, "%" PRIu64, i);
        LOG(INFO) << "issuing AssetData request for " << keyString << " chunk " << i;

        std::string request = requestBase + std::string(numBuf);
        auto response = m_client->get(request).send();
        response.then([&keyString, &totalSize, &digest, &hashState, &i, &request](Pistache::Http::Response response) {
            if (response.code() == Pistache::Http::Code::Ok) {
                LOG(INFO) << "received chunk " << i << " for Asset " << keyString;
                const Data::FlatAssetData* flatAssetData = Data::GetFlatAssetData(response.body().c_str());
                const uint8_t* chunkData = flatAssetData->data()->data();
                size_t chunkDataSize = flatAssetData->data()->size();
                // Update hash digest.
                XXH64_update(hashState, chunkData, chunkDataSize);
                digest = XXH64_digest(hashState);
                if (digest != flatAssetData->hash()) {
                    LOG(ERROR) << "Asset download " << keyString << " chunk " << i << " failed validation".
                    ok = false;
                } else {
                    totalSize += chunkDataSize;
                    outFile.write(chunkData, chunkDataSize);
                }
            } else {
                LOG(ERROR) << "error code " << response.code() << "on Asset request " << request;
                ok = false;
            }
        });

        response.wait();
        if (!ok) break;
    }

    XXH64_freeState(hashState);
    outFile.close();

    // Final digest should match key, and total file size should also match.
    if (key != digest || fileSize != totalSize) {
        LOG(ERROR) << "asset Data mismatch, key: " << keyString << " computed hash: " << Asset::keyToString(digest)
            << " recorded size: " << fileSize << " downloaded bytes: " << totalSize;
        ok = false;
    }

    if (!ok) {
        LOG(ERROR) << "asset Data retrieval failed for Asset " << keyString << ", removing file at " << path;
        fs::remove(path);
    }

    return ok;
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
    flatbuffers::FlatBufferBuilder builder(kPageSize);
    asset.flatten(builder, inlineData);

    std::string request = m_serverAddress + "/asset/" + Asset::keyToString(key);
    LOG(INFO) << "sending POST for new inline asset " << request;

    bool ok = true;
    auto response = m_client->post(request)
        .body(std::string_view(builder.GetBufferPointer(), builder.GetSize()))
        .send();
    response.then([&request, &ok](Pistache::Http::Response response) {
        if (response.code() == Pistache::Http::Code::Ok) {
            LOG(INFO) << "received ok response for inline asset post " << request;
        } else {
            LOG(ERROR) << "error code " << response.code() << " on inline Asset post " << request;
            ok = false;
        });
    });

    response.wait();
    if (!ok) {
        return 0;
    }

    return key;
}

uint64_t HttpClient::postFileAsset(Asset:Type type, const std::string& name, uint64_t author, uint64_t deprecates,
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
    size_t bytesRemaining = fileSize;

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
        LOG(ERROR) << "file read error for " << assetFile << ", some bytes left unread.";
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

    std::string request = m_serverAddress + "/asset/" + keyString;
    bool ok = true;
    auto response = m_client->post(request)
        .body(std::string_view(builder.GetBufferPointer(), builder.GetSize()))
        .send();
    response.then([&request, &ok](Pistache::Http::Response response) {
        if (response.code() == Pistache::Http::Code::Ok) {
            LOG(INFO) << "received ok response on file asset post " << request;
        } else {
            LOG(ERROR) << "error code " << response.code() << " on file Asset post " << request;
            ok = false;
        }
    });

    response.wait();

    if (!ok) {
        LOG(INFO) << "error posting new file asset " << assetFile << " with key " << keyString;
        return 0;
    }

    // Now we upload the individual data chunks of the file. We will be reading the bytes directly into the
    // FlatBufferBuilder object, to skip a copy.
    hashState = XXH64_createState();
    XXH64_reset(hashState, 0);
    inFile.seekg(0, ios::beg);
    bytesRemaining = fileSize;
    size_t chunk = 0;
    uint64_t chunkHash = 0;

    while (ok && inFile && bytesRemaining > 0) {
        builder.Clear();
        uint8* flatData = nullptr;
        size_t flatDataSize = std::min(kDataChunkSize, bytesRemaining);
        auto flatAssetData = builder.CreateUninitializedVector(flatDataSize, &flatData);
        inFile.read(flatData, flatDataSize);
        bytesRead = inFile.gcount();
        if (bytesRead != flatDataSize) {
            LOG(ERROR) << "error re-reading asset file " << assetFile << " expected " << flatDataSize << " bytes, got "
                << bytesRead << " bytes instead.";
            ok = false;
        } else {
            Data::FlatAssetDataBuilder assetDataBuilder(builder);
            assetDataBuilder.set_data(flatAssetData);
            XXH64_update(hashState, flatData, bytesRead);
            chunkHash = XXH64_digest(hashState);
            assetDataBuilder.set_hash(chunkHash);
            auto assetData = assetDataBuilder.Finish();
            builder.Finish(assetData);

            LOG(INFO) << "sending POST of asset data for " << keyString << " chunk " << chunk;
            char numBuf[32];
            snprintf(numBuf, 32, "%" PRIu64, chunk);
            request = m_serverAddress + "/asset_data/" + keyString + "/" + std::string(numBuf);
            auto response = m_client->post(request)
                .body(std::string_view(builder.GetBufferPointer(), builder.GetSize()))
                .send();
            response.then([&request, &ok](Pistache::Http::Response response) {
                if (response.code() == Pistache::Http::Code::Ok) {
                    LOG(INFO) << "recived ok response on file asset chunk post " << request;
                } else {
                    LOG(ERROR) << "error code " << response.code() << " on file asset chunk post " << request;
                    ok = false;
                }
            });

            response.wait();
            ++chunk;
        }
    }

    XXH64_freeState(hashState);

    // Digest hash of final chunk should match the overall hash of the file.
    if (!ok || bytesRemaining > 0 || chunkHash != key) {
        LOG(ERROR) << "error uploading file " << assetFile << " to server.";
        return 0;
    }

    return key;
}

void HttpClient::shutdown() {
    m_client->shutdown();
}

}  // namespace Confab

