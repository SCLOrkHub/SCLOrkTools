#ifndef SRC_CONFAB_DATABASE_HPP_
#define SRC_CONFAB_DATABASE_HPP_

#include <leveldb/db.h>

#include <memory>

namespace Common {
    class Version;
}

namespace Confab {

namespace Data {
    class FlatAsset;
    class FlatAssetData;
    class FlatConfig;
}

/*! Non-owning pointer wrapper for returning results from Database queries with no copies.
 *
 * Uses semantics similar to std::unique_ptr<T>. Note: specializations should always be const.
 */
template<class T>
class SlicePtr {
public:
    /*! Construct a SlicePtr along with objects needed for reclamation.
     *
     * \param pointer Pointer to wrap.
     * \param iterator The LevelDB Iterator from the query that is keeping slice valid.
     */
    explicit SlicePtr(T* pointer, std::unique_ptr<leveldb::Iterator> iterator) :
        m_pointer(pointer),
        m_iterator(std::move(iterator)) { }

    /*! Convenience ctor to make an empty SlicePtr.
     *
     * \param p A null pointer.
     */
    explicit SlicePtr(std::nullptr_t p) :
        m_pointer(nullptr),
        m_iterator(nullptr) { }

    /*! Disabled copy constructor, for unique_ptr type semantics.
     *
     * \param p A SlicePtr.
     */
    SlicePtr(const SlicePtr<T>& p) = delete;

    /*! Move ctor, invalidates source pointer.
     *
     * \param source The SlicePtr to copy.
     */
    SlicePtr(SlicePtr<T>&& source) :
        m_pointer(source.m_pointer),
        m_iterator(std::move(source.m_iterator)) {
        source.m_pointer = nullptr;
    }

    /*! Destruct a SlicePtr. Deletes the Iterator, so the non-owning pointer will no longer be valid.
     */
    ~SlicePtr() = default;

    /*! Dereference operator.
     *
     * \return A reference to contents pointed to by the raw pointer.
     */
    T& operator*() { return *m_pointer; }

    /*! Structure dereference operator.
     *
     * \return The raw pointer.
     */
    T* operator->() { return m_pointer; }

    /*! Get the size of the data pointed to by T.
     *
     * \return Size in bytes of *T.
     */
    size_t size() const {
        if (m_iterator != nullptr) {
            return m_iterator->value().size();
        }
        return 0;
    }

    /*! Equality comparison with nullptr.
     *
     * \param p The nullptr.
     * \return True if data pointer is nullptr, false otherwise.
     */
    bool operator==(const std::nullptr_t p) const {
        return m_pointer == nullptr;
    }

    /*! Inequality comparison with nullptr.
     *
     * \param p The nullptr.
     * \return False if data pointer is nullptr, true otherwise.
     */
    bool operator!=(const std::nullptr_t p) const {
        return m_pointer != nullptr;
    }

private:
    T* m_pointer;
    std::unique_ptr<leveldb::Iterator> m_iterator;
};

using ConfigPtr = SlicePtr<const Data::FlatConfig>;
using AssetPtr = SlicePtr<const Data::FlatAsset>;
using DataPtr = SlicePtr<const Data::FlatAssetData>;

/*! Encapsulates a LevelDB database for use in Confab.
 *
 * The Database object allows semantic-level manipulation of the asset database. It provides functionality to store
 * asset metadata, files, and other entries as needed by the system. It includes convenience routines for common
 * database use cases in the confab program.
 *
 * \sa [Database Design Document](@ref Confab-Design-Document-Database-Design)
 */
class Database {
public:
    /*! Constructs an empty Database object.
     *
     * \param database An pointer to an existing LevelDB database object (or a mock for testing), or nullptr. Note that
     *                 Database will take ownership of this pointer.
     * \sa open(), ~Database()
     */
    Database(leveldb::DB* database = nullptr);

    /*! Close and then destruct an open Database.
     *
     * \sa close()
     */
    ~Database();

    /*! Open or create Database LevelDB database file tree.
     *
     * \param path A path to a directory where the Confab LevelDB database is stored.
     * \param createNew If true, open() will attempt to create a new database, and will treat an existing or already
     *                  initialized database as an error condition. If false, open() will expect a valid database to
     *                  exist at \a path.
     * \param cacheSize Size in bytes of the LRU memory cache to request from LevelDB. A size <= 0 will disable the
     *                  cache.
     * \return true on success, or false on error.
     */
    bool open(const char* path, bool createNew, int cacheSize);

    /*! Returns the singleton FlatConfig object stored in the database, or nullptr if not found.
     *
     * \return Deserialized config object or nullptr.
     */
    ConfigPtr findConfig();

    /*! Write the most recent version of the config key and value to the database.
     *
     * \param version Confab Version number to save in the database.
     * \return true on success, or false on error.
     */
    bool writeConfig(const Common::Version& version);

    /*! Search for an Asset record associated with key and return it.
     *
     * \param key A key uniquely identifying this asset.
     * \return A pointer to a FlatAsset object, or nullptr if not found.
     */
    SlicePtr<const Data::FlatAsset> findAsset(uint64_t key);

    /*! Larger assets store their data in a separate record. Search for a data record with key and return it.
     *
     * \param key A key uniquely identifying this asset.
     * \return A pointer to the FlatAssetData, and stores the size of the data in size, or nullptr if not found.
     */
    SlicePtr<const Data::FlatAssetData> findAssetData(uint64_t key);

    /*! Close the database, and delete any internal references to it.
     *
     */
    void close();

    /*! The array type of asset and asset data keys.
     */
    using AssetKey = std::array<uint8_t, 9>;

    /*! Byte prefixes to prepend to hash keys for database.
     */
    enum KeyPrefix : uint8_t { kAsset = 0xaa, kData = 0xdd };

    /*! Converts a 64-bit binary key into a human-readable hexadecimal string.
     *
     * \param key A binary key.
     * \return A hexadecimal string of key.
     */
    static std::string keyToString(uint64_t key);

    /*! Given a key value, format it into the asset key used for accessing the database. (public for testing only)
     *
     * \param key The asset key.
     * \return A byte array usable as a key in a database key lookup for an Asset object.
     */
    static AssetKey makeAssetKey(uint64_t key);

    /*! Given a key value, format it into the data key used for accessing the database. (public for testing only)
     *
     * \param key The asset key.
     * \return A byte array usable as a key in a database key lookup for an AssetData object.
     */
    static AssetKey makeDataKey(uint64_t key);

private:

    leveldb::DB* m_database;
};

}  // namespace Confab

#endif  // SRC_CONFAB_DATABASE_HPP_

