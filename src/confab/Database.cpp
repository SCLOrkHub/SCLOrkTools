#include "Database.hpp"

#include "ConfabVersion.hpp"
#include "common/Version.hpp"

#include <glog/logging.h>
#include <yaml-cpp/yaml.h>

#include <boost/iostreams/device/array.hpp>
#include <boost/iostreams/stream.hpp>
#include <string>

namespace {
    const char* kConfabConfigKey = "confab-db-config";
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
    leveldb::Status status = m_database->Get(leveldb::ReadOptions(), kConfabConfigKey, &config);
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
    configIterator->Seek(kConfabConfigKey);
    if (!configIterator->Valid() || configIterator->key() != std::string(kConfabConfigKey)) {
        LOG(FATAL) << "Failure finding database config key.";
        return false;
    }

    Common::Version configVersion(0, 0, 0);

    YAML::Node config;
    try {
        leveldb::Slice configSlice = configIterator->value();
        boost::iostreams::array_source configSource{configSlice.data(), configSlice.size()};
        boost::iostreams::stream<boost::iostreams::array_source> configStream{configSource};

        config = YAML::Load(configStream);
        configVersion = Common::Version(config["version_major"].as<int>(), config["version_minor"].as<int>(),
            config["version_patch"].as<int>());
        m_databaseVersion = config["db_version"].as<uint8_t>();
    } catch (YAML::Exception exception) {
        LOG(FATAL) << "Error parsing database config key YAML. " << exception.msg;
        return false;
    }

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

void Database::close() {
    delete m_database;
    m_database = nullptr;
}

bool Database::writeConfigData() {
    CHECK(m_database) << "Database should already be open.";

    YAML::Emitter out;

    out << YAML::BeginMap;
    out << YAML::Key << "version_major" << YAML::Value << Confab::kConfabVersionMajor;
    out << YAML::Key << "version_minor" << YAML::Value << Confab::kConfabVersionMinor;
    out << YAML::Key << "version_patch" << YAML::Value << Confab::kConfabVersionPatch;
    out << YAML::Key << "db_version" << YAML::Value << static_cast<int>(Confab::kConfabDatabaseVersion);

    leveldb::Status status = m_database->Put(leveldb::WriteOptions(), kConfabConfigKey, out.c_str());
    if (!status.ok()) {
        LOG(FATAL) << "Error writing config data to database. LevelDB status: " << status.ToString();
        return false;
    }

    return true;
}

}  // namespace Confab

