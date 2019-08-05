#ifndef SRC_CONFAB_DATABASE_HPP_
#define SRC_CONFAB_DATABASE_HPP_

#include "Record.hpp"
#include "SizedPointer.hpp"

#include <memory>

namespace leveldb {
    class DB;
    class Iterator;
    class WriteBatch;
}

namespace Confab {

/*! Encapsulates a LevelDB database for use in Confab.
 *
 * The Database object is a low-level abstraction around the LevelDB database, adding very little logic of its own. It
 * provide a level of indirection around the LevelDB API as well as allows for easy mocking or faking for testing of
 * objects dependent on this one.
 *
 * TODO: consider just morphing this object with AssetDatabase.
 *
 * \sa [Database Design Document](@ref Confab-Design-Document-Database-Design)
 */
class Database {
public:
    /*! Container class allowing for multiple writes to the database with Atomicity.
     */
    class Batch {
    public:
        /*! Constructs a empty Batch.
         */
        Batch();

        /*! Destructs a Batch.
         */
        ~Batch();

        /*! Adds a Database store operation to the Batch. Will not actually execute any stores until the
         * Database::write() function is called. Note that *all* SizedPointers provided to Batch via store() must remain
         * valid until Database::write() returns.
         *
         * \param key A pointer to the key data to store the provided data under.
         * \param value The value data to store the provided key with.
         */
        void store(const SizedPointer& key, const SizedPointer& value);

        /*! Internal accessor for the contained LevelDB batch object, for use by the Database object.
         *
         * \return A pointer to the LevelDB batch object.
         */
        leveldb::WriteBatch* batch() { return m_batch.get(); }

    private:
        std::unique_ptr<leveldb::WriteBatch> m_batch;
    };

    /*! Constructs an empty Database object.
     *
     * \param database An pointer to an existing LevelDB database object (or a mock for testing), or nullptr. Note that
     *                 Database will take ownership of this pointer.
     * \sa open(), ~Database()
     */
    Database(leveldb::DB* database = nullptr);

    /*! Destructs a Dataase.
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

    /*! Loads the data associated with the provided key.
     *
     * \param key A pointer to the key data to search the database for.
     * \return A Record providing a non-owning pointer to the data associated with key, or an empty record if the key
     *         was not found in the database.
     */
    const RecordPtr load(const SizedPointer& key);

    /*! Saves the provided data associated with the key. Overwrites old data that may have been present under that key.
     *
     * \param key A pointer to the key to associate with the provided data.
     * \param value A pointer to the data to associate with the provided key.
     * \return Will be true on success, false on error.
     */
    bool store(const SizedPointer& key, const SizedPointer& value);

    /*! Executes the provided Batch object atomically on the database. Note that the state of the Batch object is
     * invalid after this call, and it should be deleted.
     *
     * \param batch A Batch object with commands previously recorded.
     * \return true on atomic success (all operations succeeded), false on error.
     */
    bool write(Database::Batch& batch);

    /*! Close the database, and delete any internal references to it.
     *
     */
    void close();

private:
    std::unique_ptr<leveldb::DB> m_database;
};

}  // namespace Confab

#endif  // SRC_CONFAB_DATABASE_HPP_

