#include "HttpClient.hpp"

#include "Asset.hpp"
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

namespace fs = std::experimental::filesystem;

namespace Confab {

class ClientRecord : public Record {
public:
    /*! Default constructor not supported, use makeEmptyRecord().
     */
    ClientRecord() = delete;

    /*! Construct a record that wraps a Pistache response string.
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

    std::ofstream outFile(path, std::ofstream::binary | std::ofstream::trunc);
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

    for (uint64 i = 0; i < numChunks; ++i) {
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

uint64_t HttpClient::postInlineAsset(Asset::Type type, const std::string& name, const std::string& fileExtension,
    uint64_t author, uint64_t deprecates, uint64_t size, const uint8_t* inlineData) {
    Asset asset(type);
    asset.setName(name);
    asset.setFileExtension(fileExtension);
    asset.setAuthor(author);
    asset.setDeprecates(deprecates);
    uint8_t* assetInlineData = asset.setInlineData(size);
    std::memcpy(assetInlineData, inlineData, size);

    // For short assets we add some random salt to the hash, to help avoid hash collisions.
    asset.setSalt(m_distribution(m_randomDevice));
    uint64_t key = XXH64(inlineData, size, asset.salt());
    asset.setKey(key);


}

uint64_t HttpClient::postFileAsset(Asset:Type type, const std::string& name, uint64_t author, uint64_t deprecates,
        const fs::path& assetFile) {
    return 0;
}

void HttpClient::shutdown() {
    m_client->shutdown();
}

}  // namespace Confab

