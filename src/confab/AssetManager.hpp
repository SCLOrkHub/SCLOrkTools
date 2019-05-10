#ifndef SRC_CONFAB_ASSET_MANAGER_HPP_
#define SRC_CONFAB_ASSET_MANAGER_HPP_

#include "Asset.hpp"
#include "SizedPointer.hpp"

#include <functional>
#include <memory>

namespace Confab {

class Database;

/*! Class responsible for the creation and retrieval of assets in filesystem and database.
 */
class AssetManager {
public:
    /*! Construct an AssetManager.
     *
     * \param database A pointer to the database object.
     */
    AssetManager(std::shared_ptr<Database> database);

    /*! Add a new asset in a file to the database.
     *
     * Adds a new asset by file to the database. Note for larger files this may take significant time, as the hash must
     * first be computed, requiring a full file traversal, and then the file is ingested into the database, requiring
     * a second file traversal.
     *
     * \param type One of the enumerated types in Asset::Type.
     * \param filePath A string with the path to the file to add.
     * \param callback A function to callback with the Asset key, once computed, or 0 if error.
     */
    void addAssetFile(Asset::Type type, const std::string& filePath, std::function<void(uint64_t)> callback);

    /*! Computes the hash of a file in 4K increments.
     *
     * Note for large files this can take significant time. Single-chunk hashes should be computed with
     * computeHashMemory() as that is more efficient for smaller data sizes.
     *
     * \param filePath The path to the file to hash.
     * \param expectedSize The size of the file to hash, for comparison with number of bytes actually hashed.
     * \param salt A starting value for the hasher. For smaller Assets this may be helpful to reduce the possibility of
     *             of hash collisions.
     * \return The computed hash, or 0 on error.
     * \sa computeHashMemory()
     */
    uint64_t computeHashFile(const std::string& filePath, size_t expectedSize, uint64_t salt = 0);

    /*! Computes the hash of a single in-memory data chunk.
     *
     * Note for chunks larger than kDataChunkSize computeHashFile is more efficient.
     *
     * \param data A pointer to the data to hash.
     * \param size The size of the data to hash, must be less than or equal to kDataChunkSize
     * \param salt A starting value for the hasher. For smaller Assets this may be helpful to reduce the possibility of
     *             of hash collisions.
     * \return The computed hash, or 0 on error.
     * \sa computeHashFile()
     */
    uint64_t computeHashMemory(const uint8_t* data, size_t size, uint64_t salt = 0);

    /*! The size in bytes of the key associated with an Asset in the database.
     *
     * Currently 9 bytes, counting one byte for the kAsset prefix, followed by 8 bytes of the Asset key.
     */
    static const size_t kAssetDatabaseKeySize = 9;

    /*! The size in bytes of the key associated with an AssetData entry in the database.
     *
     * Currently 17 bytes, counting one byte for the kData prefix, followed by 8 bytes of the Asset key, followed by
     * 8 bytes indicated the chunk number (starting from 1).
     */
    static const size_t kAssetDataDatabaseKeySize = 17;

    /*! Byte prefixes to prepend to Asset or AssetData keys for database.
     */
    enum KeyPrefix : uint8_t { kAsset = 0xaa, kData = 0xdd };

    /*! Writes a byte sequence in keyOut suitable for storing or retrieving an Asset record from the database.
     *
     * \param key The key to format.
     * \param keyOut A pointer to where to store the key sequence, must be at least kAssetDatabaseKeySize in size.
     */
    void makeAssetDatabaseKey(uint64_t key, SizedPointer keyOut);

    /*! Writes a byte sequence in keyOut suitable for storing or retrieving an AssetData record from the database.
     *
     * \param key The key to format.
     * \param chunkNumber The number in the sequence of chunks to include in the key.
     * \param keyOut A pointer to where to store the key sequence, must be at least kAssetDataDatabaseKeySize in size.
     */
    void makeAssetDataDatabaseKey(uint64_t key, uint64_t chunkNumber, SizedPointer keyOut);

    /*! Converts a 64-bit binary key into a human-readable hexadecimal string.
     *
     * \param key A binary key.
     * \return A hexadecimal string of key.
     */
    static std::string keyToString(uint64_t key);

    /// @cond UNDOCUMENTED
    AssetManager() = delete;
    AssetManager(const AssetManager&) = delete;
    AssetManager& operator=(const AssetManager&) = delete;
    ~AssetManager() = default;
    /// @endcond UNDOCUMENTED

private:

    std::shared_ptr<Database> m_database;
};

}  // namespace Confab

#endif  // SRC_CONFAB_ASSET_MANAGER_HPP_

