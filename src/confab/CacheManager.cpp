#include "CacheManager.hpp"

#include "Asset.hpp"
#include "HttpClient.hpp"

#include "glog/logging.h"


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

    for (auto& path : fs::directory_iterator(m_cachePath)) {
        if (fs::is_regular_file(path)) {
            size_t fileSize = fs::file_size(path);
            std::chrono::time_point writeTime = fs::last_write_time(path);
            uint64_t key = Asset::stringToKey(path.stem());
            bool valid = true;
            if (validate) {
                // TODO: compute hash of file when requested, delete file on failure.
            }
            if (valid) {
                LOG(INFO) << "adding " << path << " to cache record, " << fileSize << " bytes.";
                m_currentSize += fileSize;
                m_timeQueue.insert(std::make_pair(writeTime, path));
                fs::path extension = path.extension();
                m_extensionMap.insert(std::make_pair(key, extension);
            }
        } else {
            LOG(INFO) << "CacheManager finds non-file entry: " << path << " in cache.";
        }
    }

    LOG(INFO) << "CacheManager found " << m_extensionMap.size() << " entries, total " << m_currentSize << " bytes,"
        " starting eviction process.";

    makeRoomFor(0);
}

fs::path CacheManager::checkCache(uint64_t key) {
    auto extensionPair = m_extensionMap.find(key);
    if (extensionPair == m_extensionMap.end()) {
        return fs::path();
    }

    fs::path cachePath = m_cachePath + "/" + Asset::keyToString(key) + extensionPair.second();
    LOG(INFO) << "cache hit for Asset " << Asset::keyToString(key) << " at " << cachePath;

    // Update file write time to reflect the access of this cached asset. NOTE that this means the data in m_timeQueue
    // is now invalid, leading to a need for re-verification of write times in the queue when identifying eviction
    // candidates.
    fs::last_write_time(cachePath, extensionPairstd::system_clock::now());
    return cachePath;
}

fs::path CacheManager::download(uint64_t key, size_t fileSize, const fs::path& fileExtension) {
    // TODO
}

void CacheManager::makeRoomFor(size_t addedBytes) {
    while (m_currentSize + addedBytes > m_maxSize) {
        auto oldest = m_timeQueue.top();
        m_timeQueue.pop();
        // Validate access time against reality, as this entry may have been accessed since queue insertion.
        auto realWriteTime = fs::last_write_time(oldest.second());
        if (realWriteTime == oldest.first()) {
            size_t oldestSize = fs::file_size(oldest.second());
            LOG(INFO) << "evicting " << oldest.second() << " from cache, " << oldestSize << " bytes.";
            m_currentSize = m_currentSize - oldestSize;
            fs::remove(oldest.second());
            // Also remove this cached asset from the map.
            m_extensionMap.erase(Asset::stringToKey(oldest.second().stem()));
        } else {
            LOG(INFO) << "updating access time in queue on asset " << oldest.second();
            m_timeQueue.insert(std::make_pair(realWriteTime, oldest.second()));
        }
    }

    LOG(INFO) << "eviction process complete, totals now " << m_extensionMap.size() << " entries, total " << 
        m_currentSize << " bytes.";
}

}  // namespace Confab

