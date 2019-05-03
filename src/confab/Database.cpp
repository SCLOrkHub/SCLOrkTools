#include "Database.hpp"

#include "ConfabVersion.hpp"
#include "common/Version.hpp"
#include "schemas/Asset_generated.h"
#include "schemas/Config_generated.h"

#include <glog/logging.h>
#include <leveldb/cache.h>

#include <cstring>
#include <inttypes.h>
#include <string>
#include <utility>

namespace {
    const char* kConfigKey = "confab-db-config";
    const uint64_t kPrependMask = 0xffffffffffffff00;
    const uint64_t kAssetPrepend = 0x00000000000000aa;
    const uint64_t kDataPrepend = 0x00000000000000dd;
    const size_t kAssetKeySizeBytes = 9;
    const size_t kDataKeySizeBytes = 9;
}  // namespace

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

Database::SlicePtr<const Data::Asset> Database::findAsset(uint64_t key) {
    CHECK(m_database) << "Database should already be open.";

    std::array<uint64_t, 2> assetKey{ key, key };
    assetKey[0] = (assetKey[0] & kPrependMask) | kAssetPrepend;
    auto keySlice = leveldb::Slice(reinterpret_cast<const char*>(assetKey.data()), kAssetKeySizeBytes);

    DCHECK_EQ(keySlice.data()[0], 0xaa) << "Machine assumed to be little-endian.";

    auto iterator = m_database->NewIterator(leveldb::ReadOptions());
    iterator->Seek(keySlice);

    if (!iterator->Valid()) {
        LOG(ERROR) << "Invalid iterator on asset seek for key: " << keyToString(key);
        return SlicePtr<const Data::Asset>(nullptr);
    }

    if (std::memcmp(keySlice.data(), iterator->key().data(), kAssetKeySizeBytes) != 0) {
        LOG(INFO) << "Asset " << keyToString(key) << " not found in database.";
        return SlicePtr<const Data::Asset>(nullptr);
    }

    const Data::Asset* asset = Data::GetAsset(iterator->value().data());
    return SlicePtr<const Data::Asset>(asset, iterator);
}

Database::SlicePtr<const uint8_t> Database::findData(uint64_t key) {
    CHECK(m_database) << "Database should already be open.";

    std::array<uint64_t, 2> dataKey{ key, key };
    dataKey[0] = (dataKey[0] & kPrependMask) | kDataPrepend;
    auto keySlice = leveldb::Slice(reinterpret_cast<const char*>(dataKey.data()), kDataKeySizeBytes);

    DCHECK_EQ(keySlice.data()[0], 0xdd) << "Machine assumed to be little-endian.";

    auto iterator = m_database->NewIterator(leveldb::ReadOptions());
    iterator->Seek(keySlice);

    if (!iterator->Valid()) {
        LOG(ERROR) << "Invalid iterator on asset data seek for key: " << keyToString(key);
        return SlicePtr<const uint8_t>(nullptr);
    }

    if (std::memcmp(keySlice.data(), iterator->key().data(), kAssetKeySizeBytes) != 0) {
        LOG(INFO) << "Asset data " << keyToString(key) << " not found in database.";
        return SlicePtr<const uint8_t>(nullptr);
    }

    return SlicePtr<const uint8_t>(reinterpret_cast<const uint8_t*>(iterator->value().data()), iterator);
}

void Database::close() {
    delete m_database;
    m_database = nullptr;
}

std::string Database::keyToString(uint64_t key) const {
    std::array<char, 17> buf;
    snprintf(buf.data(), 17, PRIu64, key);
    return std::string(buf.data());
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

