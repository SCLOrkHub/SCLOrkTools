#include "Database.hpp"

#include "ConfabVersion.hpp"

#include <glog/logging.h>
#include <yaml-cpp/yaml.h>

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

bool Database::open(const char* path, bool createNew) {
    leveldb::Options options;
    options.create_if_missing = createNew;
    options.error_if_exists = createNew;
    leveldb::Status status = leveldb::DB::Open(options, path, &m_database);
    if (!status.ok()) {
        LOG(ERROR) << "Failure opening or creating database at '" << path << "'. LevelDB status: " << status.ToString();
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
        LOG(ERROR) << "Attempt to initialize database with existing configuration key.";
        return false;
    } else if (!status.IsNotFound()) {
        LOG(ERROR) << "Error looking up configuration key. LevelDB status: " << status.ToString();
        return false;
    }

    YAML::Emitter out;
    out << YAML::BeginMap;
    out << YAML::Key << "version_major" << YAML::Value << Confab::kConfabVersionMajor;
    out << YAML::Key << "version_minor" << YAML::Value << Confab::kConfabVersionMinor;
    out << YAML::Key << "version_sub" << YAML::Value << Confab::kConfabVersionSub;
    out << YAML::Key << "db_version" << YAML::Value << static_cast<int>(Confab::kConfabDatabaseVersion);

    status = m_database->Put(leveldb::WriteOptions(), kConfabConfigKey, out.c_str());
    if (!status.ok()) {
        LOG(ERROR) << "Error writing config key to database. LevelDB status: " << status.ToString();
        return false;
    }

    return true;
}

bool Database::validate() {
    CHECK(m_database) << "Database should already be open.";

    // Retrieve the configuration data, which should already be present in the database.
    std::string configYAML;
    leveldb::Status status = m_database->Get(leveldb::ReadOptions(), kConfabConfigKey, &configYAML);
    if (!status.ok()) {
        LOG(ERROR) << "Failure reading database config key. LevelDB status: " << status.ToString();
        return false;
    }

    YAML::Node config;
    try {
        config = YAML::Load(configYAML);
    } catch (YAML::ParserException exception) {
        LOG(ERROR) << "Error parsing database config key YAML. " << exception.msg;
        return false;
    }

    int versionMajor = -1;
    int versionMinor = -1;
    int versionSub = -1;
    try {
        versionMajor = config["version_major"].as<int>();
        versionMinor = config["version_minor"].as<int>();
        versionSub = config["version_sub"].as<int>();
        m_databaseVersion = config["db_version"].as<uint8_t>();
    } catch (YAML::Exception exception) {
        LOG(ERROR) << "Database config value missing required field. " << exception.msg;
        return false;
    }

    return true;
}

void Database::close() {
    delete m_database;
    m_database = nullptr;
}

}  // namespace Confab

