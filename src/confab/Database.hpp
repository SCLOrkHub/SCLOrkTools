#ifndef SRC_CONFAB_DATABASE_HPP_
#define SRC_CONFAB_DATABASE_HPP_

#include <leveldb/db.h>

namespace Confab {

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

