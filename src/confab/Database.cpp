#include "Database.hpp"

#include "ConfabVersion.hpp"
#include "common/Version.hpp"
#include "schemas/Asset_generated.h"
#include "schemas/Config_generated.h"

#include <glog/logging.h>
#include <leveldb/cache.h>

#include <string>

namespace {
    const char* kConfigKey = "confab-db-config";
    const uint64_t kAssetPrepend = 0xffffffffffffffff;
    const uint64_t kDataPrepend = 0x0000000000000000;
}

namespace Confab {

Database::Database(leveldb::DB* database) :
    m_database(nullptr) {
}

Database::~Database() {
    if (m_database != nullptr) {
        close();
    }
}

bool Database::open(const char* path, bool createNew, int cacheSize) {
    leveldb::Options options;
    options.create_if_missing = createNew;
    options.error_if_exists = createNew;
    if (cacheSize > 0) {
        options.block_cache = leveldb::NewLRUCache(cacheSize);
    }

    leveldb::Status status = leveldb::DB::Open(options, path, &m_database);
    if (!status.ok()) {
        LOG(FATAL) << "Failure opening or creating database at '" << path << "'. LevelDB status: " << status.ToString();
        return false;
    } else {
        LOG(INFO) << "Opened database file at '" << path << "'.";
    }

    if (createNew) {
        if (!initializeEmpty()) {
            return false;
        }
    }

    return true;
}

bool Database::initializeEmpty() {
    CHECK(m_database) << "Database should already be open.";

    // First attempt to retrieve the configuration data, to see if this database is already initialized.
    std::string config;
    leveldb::Status status = m_database->Get(leveldb::ReadOptions(), kConfigKey, &config);
    if (status.ok()) {
        LOG(FATAL) << "Attempt to initialize database with existing configuration key.";
        return false;
    } else if (!status.IsNotFound()) {
        LOG(FATAL) << "Error looking up configuration key. LevelDB status: " << status.ToString();
        return false;
    }

    return writeConfigData();
}

bool Database::validate() {
    CHECK(m_database) << "Database should already be open.";

    // Retrieve the configuration data, which should already be present in the database.
    leveldb::Iterator* configIterator = m_database->NewIterator(leveldb::ReadOptions());
    configIterator->Seek(kConfigKey);
    if (!configIterator->Valid() || configIterator->key() != std::string(kConfigKey)) {
        LOG(FATAL) << "Failure finding database config key.";
        return false;
    }

    leveldb::Slice configSlice = configIterator->value();
    // TODO: revisit flatbuffer verification.

    const Data::Config* config = Data::GetConfig(configSlice.data());
    Common::Version configVersion(config->versionMajor(), config->versionMinor(), config->versionPatch());

    config = nullptr;
    delete configIterator;

    // We treat a newer version of the database than the program as a fatal error, but an older version of the database
    // can likely just be upgraded to newest.
    if (configVersion > confabVersion) {
        LOG(FATAL) << "Database config version " << configVersion.toString() << " newer than confab version "
            << confabVersion.toString();
        return false;
    } else if (configVersion < confabVersion) {
        LOG(WARNING) << "Upgrading database version " << configVersion.toString() << " to confab version "
            << confabVersion.toString();
        return writeConfigData();
    } else {
        LOG(INFO) << "Database config version " << configVersion.toString() << " matches confab version.";
    }

    return true;
}

Database::SlicePtr<const Data::Asset*> Database::find(uint64_t key) {
    const Data::Asset* asset = nullptr;

    return nullptr;
}

Database::SlicePtr<const uint8_t*> Database::findData(uint64_t key, size_t* size) {
    CHECK(size) << "size argument should point to valid memory.";

    *size = 0;
    return nullptr;
}

void Database::release(uint64_t key) {
}

void Database::close() {
    delete m_database;
    m_database = nullptr;
}

bool Database::writeConfigData() {
    CHECK(m_database) << "Database should already be open.";

    flatbuffers::FlatBufferBuilder builder;
    auto config = Data::CreateConfig(builder, kConfabVersionMajor, kConfabVersionMinor, kConfabVersionPatch);
    builder.Finish(config);

    leveldb::Status status = m_database->Put(leveldb::WriteOptions(), kConfigKey, leveldb::Slice(
        reinterpret_cast<const char*>(builder.GetBufferPointer()), builder.GetSize()));
    if (!status.ok()) {
        LOG(FATAL) << "Error writing config data to database. LevelDB status: " << status.ToString();
        return false;
    }

    return true;
}

}  // namespace Confab

