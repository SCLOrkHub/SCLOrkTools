#include "Database.hpp"

#include <gtest/gtest.h>
#include <leveldb/db.h>

/*
class DatabaseTest : public ::testing::Test {
protected:
    void SetUp() override {
        m_mockLevelDB = new MockDB();
        m_database = new Confab::Database(m_mockLevelDB);
    }

    void TearDown() override {
        delete m_database;
        // Note is assumed that the Confab::Database has deleted the mock database.
    }

    MockDB* m_mockLevelDB;
    Confab::Database* m_database;
};

TEST_F(DatabaseTest, InitializeWritesConfigKey) {
    EXPECT_CALL(m_mockLevelDB, Get(_, m_database->makeConfigKey(), _));
}
*/

/*
 * Database test cases:
 * normal initalize writes a config key
 * initialize on a database that has a config key in it already
 * initialize with a read error
 * validate without a config key
 * validate with a read error
 * validate with an older version writes a newer version
 * validate with a newer version is an error
 * validate with same version does nothing
 * some testing of SlicePtr?
 * findAsset with various key scenarios - data miss, empty db, data hit, corrupt data
 * findData with same scenarios
 * makeAssetKey and makeDataKey are well-formed.
 */
