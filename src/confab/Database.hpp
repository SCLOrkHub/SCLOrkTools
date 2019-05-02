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
     * This method also calls the underlying LevelDB validation functions, which for a large database can take some
     * time.
     *
     * \return true on success, or false on error.
     */
    bool validate();

    /*! Non-owning pointer wrapper for returning results from Database queries with no copies.
     */
    template<class T>
    class SlicePtr<T> : public std::unique_ptr<T> {
    public:
        /*! Construct a SlicePtr along with objects needed for reclamation.
         *
         * \param p Pointer to wrap.
         * \param iterator The LevelDB Iterator from the query that is keeping slice valid.
         * \param slice The Slice the Iterator returned.
         */
        SlicePtr(T* p, leveldb::Iterator* iterator, leveldb::Slice slice) :
            std::unique_ptr<T>(p),
            m_iterator(iterator),
            m_slice(slice) { }

        /* Destruct a SlicePtr. Deletes the Iterator, so the non-owning pointer will no longer be valid.
         */
        ~SlicePtr() override {
            delete m_iterator;
        }

        size_t size() const { return m_slice.size(); }

    private:
        leveldb::Iterator* m_iterator;
        leveldb::Slice* m_slice;
    };

    /*! Search for an Asset record associated with key and return it.
     *
     * \param key A 64-bit key uniquely identifying this asset.
     * \return A pointer to an Asset object, or nullptr if not found. Free by calling release(key) after use.
     */
    SlicePtr<const Data::Asset*> find(uint64_t key);

    /*! Larger assets store their data in a separate record. Search for a data record with key and return it.
     *
     * \param key A 64-bit key uniquely identifying this asset.
     * \param size A pointer to a container for the size of the data returned.
     * \return A pointer to the asset data, and stores the size of the data in size. Returns nullptr on error. Free
     *          by calling release(key) after use.
     */
    SlicePtr<const uint8_t*> findData(uint64_t key, size_t* size);

    /*! Close the database, and delete any internal references to it.
     *
     */
    void close();

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

