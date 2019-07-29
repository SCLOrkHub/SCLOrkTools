#include "CacheManager.hpp"

#include "Asset.hpp"
#include "Constants.hpp"
#include "HttpClient.hpp"
#include "schemas/FlatAsset_generated.h"
#include "schemas/FlatAssetData_generated.h"

#include "glog/logging.h"
#include "xxhash.h"

#include <array>
#include <fstream>

namespace Confab {

CacheManager::CacheManager(const fs::path& cachePath, size_t maxSize, std::shared_ptr<HttpClient> httpClient) :
    m_cachePath(cachePath),
    m_maxSize(maxSize),
    m_httpClient(httpClient),
    m_currentSize(0) {
}

void CacheManager::checkExistingEntries(bool validate) {
    LOG(INFO) << "CacheManager starting file enumeration within path: " << m_cachePath;

    // Reset current state to empty.
    m_currentSize = 0;
    while (!m_timeQueue.empty()) {
        m_timeQueue.pop();
    }
    m_extensionMap.clear();

    for (auto& entry : fs::directory_iterator(m_cachePath)) {
        fs::path path = entry.path();
        if (fs::is_regular_file(path)) {
            size_t fileSize = fs::file_size(path);
            std::chrono::time_point writeTime = fs::last_write_time(path);
            uint64_t key = Asset::stringToKey(path.stem());
            bool valid = true;
            if (validate) {
                XXH64_state_t* hashState = XXH64_createState();
                XXH64_reset(hashState, 0);
                std::array<char, kDataChunkSize> fileChunk;
                std::ifstream inFile(path);
                if (!inFile) {
                    LOG(ERROR) << "error opening cache file: " << path << " for hash validation.";
                    valid = false;
                } else {
                    size_t bytesRemaining = fileSize;
                    while (inFile && bytesRemaining > 0) {
                        inFile.read(fileChunk.data(), kDataChunkSize);
                        size_t bytesRead = inFile.gcount();
                        bytesRemaining -= bytesRead;
                        XXH64_update(hashState, fileChunk.data(), bytesRead);
                    }
                    uint64_t hash = XXH64_digest(hashState);
                    if (hash != key || bytesRemaining > 0) {
                        LOG(ERROR) << "error validating cache file: " << path << " computed hash of "
                            << Asset::keyToString(hash) << " with " << bytesRemaining << " bytes unread.";
                        valid = false;
                    } else {
                        LOG(INFO) << "validated cache file " << path << ".";
                    }
                }
                XXH64_freeState(hashState);
            }
            if (valid) {
                std::lock_guard<std::mutex> lock(m_mutex);
                LOG(INFO) << "adding " << path << " to cache record, " << fileSize << " bytes.";
                m_currentSize += fileSize;
                m_timeQueue.push(std::make_pair(writeTime, path));
                fs::path extension = path.extension();
                m_extensionMap.insert(std::make_pair(key, extension));
            } else {
                LOG(WARNING) << "removing invalid cache file " << path;
                fs::remove(path);
            }
        } else {
            LOG(INFO) << "found non-file entry: " << path << " in cache.";
        }
    }

    LOG(INFO) << "CacheManager found " << m_extensionMap.size() << " entries, total " << m_currentSize << " bytes,"
        " starting eviction process.";

    makeRoomFor(0);
}

fs::path CacheManager::checkCache(uint64_t key) {
    fs::path cachePath;

    {
        std::lock_guard<std::mutex> lock(m_mutex);
        auto extensionPair = m_extensionMap.find(key);
        if (extensionPair == m_extensionMap.end()) {
            LOG(INFO) << "cache miss for Asset " << Asset::keyToString(key);
            return fs::path();
        }
        cachePath = m_cachePath;
        cachePath += fs::path(Asset::keyToString(key) + extensionPair->second);
    }

    LOG(INFO) << "cache hit for Asset " << Asset::keyToString(key) << " at " << cachePath;

    // Update file write time to reflect the access of this cached asset. NOTE that this means the data in m_timeQueue
    // is now invalid, leading to a need for re-verification of write times in the queue when identifying eviction
    // candidates.
    fs::last_write_time(cachePath, std::chrono::system_clock::now());
    return cachePath;
}

fs::path CacheManager::download(uint64_t key, size_t fileSize, uint64_t chunks, const std::string& fileExtension) {
    fs::path filePath = m_cachePath;
    filePath += fs::path(Asset::keyToString(key) + fileExtension);
    LOG(INFO) << "downloading Asset data for " << Asset::keyToString(key) << ", " << chunks << " chunks "
        << fileSize << " bytes, into file " << filePath;

    std::ofstream outFile(filePath, std::ios::out | std::ios::binary | std::ios::trunc);
    if (!outFile) {
        LOG(ERROR) << "error opening file " << filePath << " for writing.";
        return fs::path();
    }

    size_t downloadedSize = 0;
    uint64_t digest = 0;
    XXH64_state_t* hashState = XXH64_createState();
    XXH64_reset(hashState, 0);
    bool ok = true;

    // Download AssetData chunk-by-chunk sequentially, validate each chunk, then write to file.
    for (auto i = 0; i < chunks; ++i) {
        m_httpClient->getAssetData(key, i, [&filePath, &outFile, &downloadedSize, &digest, &hashState, &ok](
            uint64_t chunkKey, uint64_t chunkNumber, RecordPtr assetDataRecord) {
            if (assetDataRecord->empty()) {
                LOG(ERROR) << "error downloading chunk " << chunkNumber << " for file " << filePath;
                ok = false;
            } else {
                // Validate incremental hash of incoming data.
                const Data::FlatAssetData* flatAssetData = Data::GetFlatAssetData(assetDataRecord->data().data());
                const uint8_t* chunkData = flatAssetData->data()->data();
                size_t chunkDataSize = flatAssetData->data()->size();
                XXH64_update(hashState, chunkData, chunkDataSize);
                digest = XXH64_digest(hashState);
                if (digest != flatAssetData->hash()) {
                    LOG(ERROR) << "incremental hash validation for asset download " << filePath << " chunk number "
                        << chunkNumber << " failed, computed " << Asset::keyToString(digest) << ", expected "
                        << Asset::keyToString(flatAssetData->hash());
                    ok = false;
                } else {
                    downloadedSize += chunkDataSize;
                    outFile.write(reinterpret_cast<const char*>(chunkData), chunkDataSize);
                }
            }
        });
        if (!ok) break;
    }

    XXH64_freeState(hashState);
    outFile.close();

    if (ok && (key != digest || fileSize != downloadedSize)) {
        LOG(ERROR) << "asset Data mismatch, key: " << Asset::keyToString(key) << " computed hash: "
            << Asset::keyToString(digest) << " recorded size: " << fileSize << " downloaded bytes: " << downloadedSize;
        ok = false;
    }

    if (!ok) {
        LOG(WARNING) << "failed to download " << filePath << " removing file.";
        fs::remove(filePath);
        return fs::path();
    }

    std::chrono::time_point writeTime = fs::last_write_time(filePath);

    // Add filePath to cache tracking data structures.
    {
        std::lock_guard<std::mutex> lock(m_mutex);
        m_currentSize += fileSize;
        LOG(INFO) << "adding " << filePath << " to cache record, " << fileSize << " bytes, cache now " << m_currentSize
            << " bytes.";
        m_timeQueue.push(std::make_pair(writeTime, filePath));
        m_extensionMap.insert(std::make_pair(key, fileExtension));
    }

    return filePath;
}

void CacheManager::makeRoomFor(size_t addedBytes) {
    while (m_currentSize + addedBytes > m_maxSize) {
        fs::path fileToRemove;
        {
            std::lock_guard<std::mutex> lock(m_mutex);
            auto oldest = m_timeQueue.top();
            m_timeQueue.pop();
            // Validate access time against reality, as this entry may have been accessed since queue insertion.
            auto realWriteTime = fs::last_write_time(oldest.second);
            if (realWriteTime == oldest.first) {
                size_t oldestSize = fs::file_size(oldest.second);
                LOG(INFO) << "evicting " << oldest.second << " from cache, " << oldestSize << " bytes.";
                m_currentSize = m_currentSize - oldestSize;
                fileToRemove = oldest.second; 
                // Also remove this cached asset from the map.
                m_extensionMap.erase(Asset::stringToKey(oldest.second.stem()));
            } else {
                LOG(INFO) << "updating access time in queue on asset " << oldest.second;
                m_timeQueue.push(std::make_pair(realWriteTime, oldest.second));
            }
        }
        if (!fileToRemove.empty()) {
            fs::remove(fileToRemove);
        }
    }

    LOG(INFO) << "eviction process complete, totals now " << m_extensionMap.size() << " entries, total " << 
        m_currentSize << " bytes.";
}

}  // namespace Confab

