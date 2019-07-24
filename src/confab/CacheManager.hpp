#ifndef SRC_CONFAB_SRC_CACHE_MANAGER_HPP_
#define SRC_CONFAB_SRC_CACHE_MANAGER_HPP_

#include <chrono>
#include <experimental/filesystem>
#include <memory>
#include <queue>
#include <string>
#include <unordered_map>
#include <utility>

namespace Confab {

class HttpClient;

/*! Manages a file cache of file-based Assets, using an LRU eviction strategy to maintain a fixed maximum size.
 *  Uses HttpClient to download AssetData chunks not in the cache.
 */
class CacheManager {
public:
    /*! Constructs a cache manager to manage cache files out of the provided directory.
     *
     * \param cachePath A path to the cache directory.
     * \param maxSize The size in bytes at which to start evicting least recently used cache entries.
     * \param httpClient A pointer to the HttpClient object, for downloaded resources not in cache.
     */
    CacheManager(const std::experimental::filesystem::path& cachePath, size_t maxSize, std::shared_ptr<HttpClient>
            httpClient);

    /*! Enumerates any existing files, and computes the total size of the cache so far. Can take significant time
     *  depending on the number of files in the cache and their size, particularly with validation enabled.
     *
     * \param validate Hash every file in the cache to ensure validity, deleting any invalid entries. Can add
     *                 significant time to the cache initialization process.
     */
    void checkExistingEntries(bool validate);

    /*! Returns a path to an existing file cache entry, and updates modification time of that entry, if it exists,
     * or an empty path if no such record exists.
     *
     * \param key The Asset key associated with this cache entry.
     */
    std::experimental::filesystem::path checkCache(uint64_t key);

private:
    std::experimental::filesystem::path m_cachePath;
    size_t m_maxSize;
    std::shared_ptr<HttpClient> m_httpClient;

    size_t m_currentSize;

    // We monitor access time of cache entries by updating the modified time for each request for a cache asset. This
    // queue allows for quick extraction of the oldest entry by access time for efficient cache eviction.
    using TimePath = std::pair<std::chrono::time_point, std::experimental::filesystem::path>;
    std::priority_queue<TimePath> m_timeQueue;

    // The asset files are stored with extensions, so this map keeps the extension, if any, associated with the asset
    // key. Even with no extension, presence in this map indicates presence in the cache.
    using ExtensionMap = std::unordered_map<uint64_t, std::filesystem::path>;
    ExtensionMap m_extensionMap;
};

}  // namespace Confab

#endif  // SRC_CONFAB_SRC_CACHE_MANAGER_HPP_

