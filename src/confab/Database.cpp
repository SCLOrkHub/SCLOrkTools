#include "Database.hpp"

#include <glog/logging.h>
#include <leveldb/cache.h>
#include <leveldb/db.h>

#include <cstring>

namespace Confab {

Database::Record::Record() :
    m_iterator(nullptr) {
}

Database::Record::Record(std::shared_ptr<leveldb::Iterator> iterator) :
    m_iterator(iterator) {
}

Database::Record::Record(nullptr_t nullPointer) {
}

bool Database::Record::empty() const {
    return !m_iterator;
}

const SizedPointer Database::Record::data() const {
    CHECK(!empty());
    return SizedPointer(m_iterator->value().data(), m_iterator->value().size());
}


const SizedPointer Database::Record::key() const {
    CHECK(!empty());
    return SizedPointer(m_iterator->key().data(), m_iterator->key().size());
}

Database::Database(leveldb::DB* database) :
    m_database(database) {
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

const Database::Record Database::load(const SizedPointer& key) {
    CHECK(m_database) << "Database should already be open.";

    std::shared_ptr<leveldb::Iterator> iterator(m_database->NewIterator(leveldb::ReadOptions()));
    auto keySlice = leveldb::Slice(key.dataChar(), key.size());
    iterator->Seek(keySlice);

    if (!iterator->Valid() ||
        iterator->key().size() != key.size() ||
        (std::memcmp(key.dataChar(), iterator->key().data(), key.size()) != 0)) {
        return Record(nullptr);
    }

    return Record(iterator);
}

bool Database::store(const SizedPointer& key, const SizedPointer& data) {
    CHECK(m_database) << "Database should already be open.";

    auto keySlice = leveldb::Slice(key.dataChar(), key.size());
    auto dataSlice = leveldb::Slice(data.dataChar(), data.size());
    auto status = m_database->Put(leveldb::WriteOptions(), keySlice, dataSlice);

    return status.ok();
}

void Database::close() {
    m_database.reset();
}

}  // namespace Confab

