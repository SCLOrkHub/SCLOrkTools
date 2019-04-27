#include "Database.hpp"

#include <iostream>  // TODO: replace with glog

namespace Confab {

Database::Database() :
    m_database(nullptr) {
}

Database::~Database() {
    if (m_database != nullptr) {
        close();
    }
}

bool Database::open(const char* path, bool createNew) {
    leveldb::Options options;
    options.create_if_missing = createNew;
    options.error_if_exists = createNew;
    leveldb::Status status = leveldb::DB::Open(options, path, &m_database);
    if (!status.ok()) {
        std::cerr << status.ToString() << std::endl;
        return false;
    }

    // TODO: serialize a confab version or something interesting into the database if it's being created, if it's
    // being opened validate that the key exists.

    return true;
}

void Database::close() {
    delete m_database;
    m_database = nullptr;
}

}  // namespace Confab

