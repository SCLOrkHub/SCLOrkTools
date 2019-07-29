#include "ConfabCommon.hpp"

#include "AssetDatabase.hpp"
#include "Config.hpp"
#include "Constants.hpp"
#include "Database.hpp"
#include "common/Version.hpp"

#include "glog/logging.h"

#include <fstream>
#include <memory>
#include <pthread.h>
#include <signal.h>

namespace fs = std::experimental::filesystem;

// Command line flags for logging.
DEFINE_bool(chatty, false, "If true confab will log everything to stderr as well as to log files.");
DEFINE_string(data_directory, "../data/confab", "Path where confab will store the database and log files");

// Command line flags for the database.
DEFINE_bool(create_new_database, false, "If true confab will make a new database, if false confab will expect the "
    "database to already exist.");
DEFINE_int32(database_cache_size_mb, 4, "Size in megabytes of the memory cache the database should use.");

const char* kConfigKey = "confab-db-config";

namespace Confab {

bool ConfabCommon::initialize(int argc, char* argv[]) {
    gflags::ParseCommandLineFlags(&argc, &argv, true);
    initializeLogging(argv[0]);

    // TODO: create/validate data directory.

    if (!checkSentinelFile()) {
        return false;
    }
    setThreadMask();
    return openDatabase();
}

void ConfabCommon::shutdown() {
    m_database->close();
    // Delete pid sentinel file.
    fs::remove(m_pidPath);
}

void ConfabCommon::initializeLogging(char* binaryName) {
    // Manually configure glog flag to get it to log to the data directory.
    FLAGS_log_dir = FLAGS_data_directory + "/log";
    FLAGS_alsologtostderr = FLAGS_chatty;
    google::InitGoogleLogging(binaryName);
}

bool ConfabCommon::checkSentinelFile() {
    // Check for existing pid sentinel file, meaning a version of confab is already running on this data directory.
    m_pidPath = FLAGS_data_directory + "/pid";
    if (fs::exists(m_pidPath)) {
        LOG(ERROR) << "Pid sentinel file " << m_pidPath << " already exists, exiting.";
        return false;
    } else {
        // Write sentinel file.
        std::ofstream pidFile;
        pidFile.open(m_pidPath);
        if (!pidFile) {
            LOG(ERROR) << "Error opening pid file " << m_pidPath << " for writing.";
            return false;
        }
        pidFile << getpid();
        pidFile.close();
    }
    return true;
}

bool ConfabCommon::setThreadMask() {
    // Create thread masks for ignoring SIGINT signals here, as the OSC handler will catch the SIGINT and terminate the
    // program itself on that.
    sigset_t set;
    sigemptyset(&set);
    sigaddset(&set, SIGTERM);
    if (pthread_sigmask(SIG_BLOCK, &set, nullptr) != 0) {
        LOG(ERROR) << "error setting pthread thread mask to ignore SIGINT.";
        return false;
    }
    return true;
}

bool ConfabCommon::openDatabase() {
    m_database.reset(new Confab::Database);

    if (!m_database->open((FLAGS_data_directory + "/db").c_str(), FLAGS_create_new_database,
        FLAGS_database_cache_size_mb * 1024 * 1024)) {
        return false;
    }

    // If a new database we write the configuration information for the first time. If an existing database we validate
    // that the version written is equal to or older than our current version.
    if (FLAGS_create_new_database) {
        // Verify that no existing configuration information is present.
        auto configRecord = m_database->load(Confab::Config::getConfigKey());
        if (!configRecord->empty()) {
            LOG(ERROR) << "Create new database specified by database has an existing config key.";
            return false;
        }

        Confab::Config config(Confab::confabVersion);

        if (!m_database->store(Confab::Config::getConfigKey(), config.flatten())) {
            LOG(ERROR) << "Error writing config information to database.";
            return false;
        } else {
            LOG(INFO) << "Wrote new config record to database.";
        }
    } else {
        auto configRecord = m_database->load(Confab::Config::getConfigKey());
        if (configRecord->empty()) {
            LOG(ERROR) << "Error reaading configuration information from database.";
            return false;
        }
        auto config = Confab::Config::LoadConfig(configRecord);

        if (config.version() > Confab::confabVersion) {
            LOG(ERROR) << "Database records confab version " << config.version().toString() << " which is newer than "
                << "confab version " << Confab::confabVersion.toString();
            return false;
        }

        if (config.version() < Confab::confabVersion) {
            LOG(INFO) << "Updating confab version in database " << config.version().toString()
                << " to confab version " << Confab::confabVersion.toString();
            Confab::Config currentConfig(Confab::confabVersion);
            if (!m_database->store(Confab::Config::getConfigKey(), currentConfig.flatten())) {
                LOG(ERROR) << "Error writing updated Config record to database.";
                return false;
            }
        }
    }

    m_assetDatabase.reset(new Confab::AssetDatabase(m_database));
    return true;
}

}  // namespace Confab

