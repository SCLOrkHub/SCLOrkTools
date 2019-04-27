#ifndef SRC_CONFAB_DATABASE_H_
#define SRC_CONFAB_DATABASE_H_

#include "leveldb/db.h"

namespace Confab {

/*! \brief Encapsulates a LevelDB database for use in Confab.
 *
 * The Database object allows semantic-level manipulation of the asset database. It provides functionality to store
 * asset metadata, files, and other entries as needed by the system. It includes convenience routines for common
 * database use cases in the confab program.
 */
class Database {
public:
    /*! \brief Constructs an empty Database object.
     *
     * Database objects start out closed and in a default empty state. To open or create the database file tree call
     * createNew() or open().
     *
     * \sa createNew(), open(), ~Database()
     */
    Database();

    /*! \brief Destructs a Database object.
     *
     * Will flush and close the database if those functions weren't called explicitly.
     *
     * \sa flush(), close()
     */
    ~Database();

    /*! \brief Open or create Database LevelDB file tree.
     *
     * Will attempt to open or create the LevelDB database directory specified in the \a path argument.
     *
     * \param path A path to a directory where the Confab LevelDB database is stored.
     * \param createNew If true, open() will attempt to create a new database, and will treat an existing database
     *                  as an error condition. If false, open() will expect a valid database to exist at \a path.
     * \return true on success, or false on error.
     */
    bool open(const char* path, bool createNew);

    /*! \brief Close the database.
     *
     * Will flush any pending transactions and then close the Database file tree.
     */
    void close();

private:
    leveldb::DB* m_database;
};

}  // namespace Confab

#endif  // SRC_CONFAB_DATABASE_H_

