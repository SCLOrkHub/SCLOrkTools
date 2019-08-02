#include "Database.hpp"

#include "glog/logging.h"
#include "leveldb/cache.h"
#include "leveldb/db.h"
#include "leveldb/write_batch.h"

#include <cstring>

namespace Confab {

/*! The DatabaseRecord is a Database-specific implementation of the non-copying backing store.
 *
 * It maintains a leveldb::Iterator pointer, which is where the Database data is sourced from. It deletes this pointer
 * in its own destructor.
 */
class DatabaseRecord : public Record {
public:

    /*! Default constructor not supported, use makeEmptyRecord().
     */
    DatabaseRecord() = delete;

    /*! Construct a record pointing at a Database load result.
     *
     * \param iterator The LevelDB data access iterator pointing at the desired results.
     */
    DatabaseRecord(leveldb::Iterator* iterator) : m_iterator(iterator) {
    }

    /*! Deletes a DatabaseRecord.
     */
    ~DatabaseRecord() override {
        delete m_iterator;
        m_iterator = nullptr;
    }

    /*! True if this Record has no results.
     *
     * \return A boolean which is true if this Record is pointing at nothing.
     */
    bool empty() const override { return m_iterator == nullptr; }

    /*! A pointer to the data associated with the key in the Database.
     *
     * \return A non-owning pointer to the data. Record will take care of the deletion of this pointer.
     */
    const SizedPointer data() const override {
        return SizedPointer(m_iterator->value().data(), m_iterator->value().size());
    }

    /*! The key associated with this Record.
     *
     * \return A non-owning pointer to the key data.
     */
    const SizedPointer key() const override {
        return SizedPointer(m_iterator->key().data(), m_iterator->key().size());
    }

private:
    leveldb::Iterator* m_iterator;
};

Database::Batch::Batch() : m_batch(new leveldb::WriteBatch) {
}

Database::Batch::~Batch() {
}

void Database::Batch::store(const SizedPointer& key, const SizedPointer& value) {
    auto keySlice = leveldb::Slice(key.dataChar(), key.size());
    auto valueSlice = leveldb::Slice(value.dataChar(), value.size());
    m_batch->Put(keySlice, valueSlice);
}

Database::Database(leveldb::DB* database) :
    m_database(database) {
}

Database::~Database() {
}

bool Database::open(const char* path, bool createNew, int cacheSize) {
    leveldb::Options options;
    options.create_if_missing = createNew;
    options.error_if_exists = createNew;
    if (cacheSize > 0) {
        options.block_cache = leveldb::NewLRUCache(cacheSize);
    }

    leveldb::DB* database = nullptr;

    leveldb::Status status = leveldb::DB::Open(options, path, &database);
    if (!status.ok()) {
        LOG(ERROR) << "Failure opening or creating database at '" << path << "'. LevelDB status: " << status.ToString();
        return false;
    } else {
        LOG(INFO) << "Opened database file at '" << path << "'.";
    }

    m_database.reset(database);

    return true;
}

const RecordPtr Database::load(const SizedPointer& key) {
    CHECK(m_database) << "Database should already be open.";

    leveldb::Iterator* iterator(m_database->NewIterator(leveldb::ReadOptions()));
    auto keySlice = leveldb::Slice(key.dataChar(), key.size());
    iterator->Seek(keySlice);

    if (!iterator->Valid() ||
        iterator->key().size() != key.size() ||
        (std::memcmp(key.dataChar(), iterator->key().data(), key.size()) != 0)) {
        return makeEmptyRecord();
    }

    return RecordPtr(new DatabaseRecord(iterator));
}

bool Database::store(const SizedPointer& key, const SizedPointer& value) {
    CHECK(m_database) << "Database should already be open.";

    auto keySlice = leveldb::Slice(key.dataChar(), key.size());
    auto valueSlice = leveldb::Slice(value.dataChar(), value.size());
    auto status = m_database->Put(leveldb::WriteOptions(), keySlice, valueSlice);

    return status.ok();
}

bool Database::write(Database::Batch& batch) {
    auto status = m_database->Write(leveldb::WriteOptions(), batch.batch());
    return status.ok();
}

void Database::close() {
    m_database.reset();
}

}  // namespace Confab

