#ifndef SRC_CONFAB_DATABASE_HPP_
#define SRC_CONFAB_DATABASE_HPP_

#include <leveldb/db.h>

#include <memory>

namespace Confab {

namespace Data {
    class Asset;
}  // namespace Data

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

    /*! Sets up a new LevelDB database for use with Confab.
     *
     * \return true on success, or false on error.
     * \sa [Configuration Key Design](@ref Confab-Design-Document-Database-Design-Database-Configuration-Key)
     */
    bool initializeEmpty();

    /*! Perform basic sanity checks on an open database.
     *
     * This method also calls the underlying LevelDB validation functions, which for a large database may take some
     * time.
     *
     * \return true on success, or false on error.
     */
    bool validate();

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
        explicit SlicePtr(T* pointer, leveldb::Iterator* iterator) :
            m_pointer(pointer),
            m_iterator(iterator) { }

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
            m_iterator(source.m_iterator) {
            source.m_pointer = nullptr;
            source.m_iterator = nullptr;
        }

        /*! Destruct a SlicePtr. Deletes the Iterator, so the non-owning pointer will no longer be valid.
         */
        ~SlicePtr() {
            delete m_iterator;
        }

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

    private:
        T* m_pointer;
        leveldb::Iterator* m_iterator;
    };

    /*! Search for an Asset record associated with key and return it.
     *
     * \param key A key uniquely identifying this asset.
     * \return A pointer to an Asset object, or nullptr if not found. Free by calling release(key) after use.
     */
    SlicePtr<const Data::Asset> findAsset(uint64_t key);

    /*! Larger assets store their data in a separate record. Search for a data record with key and return it.
     *
     * \param key A key uniquely identifying this asset.
     * \return A pointer to the asset data, and stores the size of the data in size. Returns nullptr on error. Free
     *          by calling release(key) after use.
     */
    SlicePtr<const uint8_t> findData(uint64_t key);

    /*! Close the database, and delete any internal references to it.
     *
     */
    void close();

    /*! Converts a 64-bit binary key into a human-readable hexadecimal string.
     *
     * \param key A binary key.
     * \return A hexadecimal string of key.
     */
    std::string keyToString(uint64_t key) const;

    /*! Size of database asset and asset data keys in bytes.
     */
    static const size_t kAssetKeySize = 9;

    /*! Given a key value, format it into the asset key used for accessing the database. (public for testing only)
     *
     * \param key The asset key.
     * \return A byte array usable as a key in a database key lookup for an Asset object.
     */
    std::array<uint8_t, kAssetKeySize> makeAssetKey(uint64_t key) const;

    /*! Given a key value, format it into the data key used for accessing the database. (public for testing only)
     *
     * \param key The asset key.
     * \return A byte array usable as a key in a database key lookup for an AssetData object.
     */
    std::array<uint8_t, kAssetKeySize> makeDataKey(uint64_t key) const;

    /*! Returns the key used to store the database configuration information under. (public for testing only)
     *
     * \return The string key used to store database configuration information.
     */
    const char* makeConfigKey() const;

private:
    /*! Write the most recent version of the config key and value to the database.
     *
     * \return true on success, or false on error.
     */
    bool writeConfigData();

    leveldb::DB* m_database;
};

}  // namespace Confab

#endif  // SRC_CONFAB_DATABASE_HPP_

