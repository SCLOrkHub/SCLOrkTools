#ifndef SRC_CONFAB_SRC_CACHE_MANAGER_HPP_
#define SRC_CONFAB_SRC_CACHE_MANAGER_HPP_

#include <chrono>
#include <experimental/filesystem>
#include <memory>
#include <mutex>
#include <queue>
#include <string>
#include <unordered_map>
#include <utility>

namespace fs = std::experimental::filesystem;

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
    CacheManager(const fs::path& cachePath, size_t maxSize, std::shared_ptr<HttpClient> httpClient);

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
     * \return The path of the existing entry, or an empty path if entry does not exist.
     */
    fs::path checkCache(uint64_t key);

    /*! If needed, makes room by evicting old entries first, then downloads AssetData chunks of the provided Asset
     * until complete, then returns a path to the newly created cache entry, or an empty path on error. Note that it
     * does not checkCache first, meaning it will clobber any existing file and re-download.
     *
     * \param key The Asset key to download AssetData chunks for.
     * \param fileSize The size of the Asset in bytes.
     * \param chunks The number of chunks to download.
     * \param fileExtension The extension to append to the filename when complete, including the dot.
     * \return The path to the file, or an empty path on error.
     */
    fs::path download(uint64_t key, size_t fileSize, uint64_t chunks, const std::string& fileExtension);

private:
    /*! Evict items from the cache until the size of the cache is smaller than the maximum size plus the addedBytes.
     *
     * \param addedBytes The number of bytes to ensure the cache will have room for without exceeding the maximum size
     *                   parameter.
     */
    void makeRoomFor(size_t addedBytes);

    fs::path m_cachePath;
    size_t m_maxSize;
    std::shared_ptr<HttpClient> m_httpClient;

    size_t m_currentSize;

    // Protects both priority queue and map.
    std::mutex m_mutex;

    // We monitor access time of cache entries by updating the modified time for each request for a cache asset. This
    // queue allows for quick extraction of the oldest entry by access time for efficient cache eviction.
    using TimePath = std::pair<std::chrono::system_clock::time_point, fs::path>;
    std::priority_queue<TimePath> m_timeQueue;

    // The asset files are stored with extensions, so this map keeps the extension, if any, associated with the asset
    // key. Even with no extension, presence in this map indicates presence in the cache.
    using ExtensionMap = std::unordered_map<uint64_t, fs::path>;
    ExtensionMap m_extensionMap;
};

}  // namespace Confab

#endif  // SRC_CONFAB_SRC_CACHE_MANAGER_HPP_

