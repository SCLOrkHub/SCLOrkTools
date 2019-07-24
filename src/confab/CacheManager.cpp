#include "CacheManager.hpp"

#include "Asset.hpp"
#include "HttpClient.hpp"

#include "glog/logging.h"

namespace fs = std::experimental::filesystem;

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
    while (!m_queue.empty()) {
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
                m_queue.emplace(std::make_pair(writeTime, path));
                fs::path extension = path.extension();
                m_extensionMap.insert(std::make_pair(key, extension);
            }
        } else {
            LOG(INFO) << "CacheManager finds non-file entry: " << path << " in cache.";
        }
    }

    LOG(INFO) << "CacheManager found " << m_extensionMap.size() << " entries, total " << m_currentSize << " bytes.";
}

void CacheManager::checkCache(uint64_t key) {
}



}  // namespace Confab

