#include "ConfabCommon.hpp"

#include "AssetManager.hpp"
#include "Config.hpp"
#include "Constants.hpp"
#include "Database.hpp"
#include "common/Version.hpp"

#include "gflags/gflags.h"
#include "glog/logging.h"

#include <experimental/filesystem>
#include <fstream>
#include <memory>
#include <pthread.h>
#include <signal.h>

namespace fs = std::experimental::filesystem;

DEFINE_bool(chatty, false, "If true confab will log everything to stderr as well as to log files.");

DEFINE_bool(create_new_database, false, "If true confab will make a new database, if false confab will expect the "
    "database to already exist.");
DEFINE_int32(database_cache_size_mb, 16, "Size in megabytes of the memory cache the database should use.");
DEFINE_string(data_directory, "../data/confab", "Path where confab will store the database and log files. A zero or "
    "negative size will disable the cache");

const char* kConfigKey = "confab-db-config";

namespace Confab {

bool ConfabCommon::initialize(int argc, char* argv[]) {
    gflags::ParseCommandLineFlags(&argc, &argv, true);
    initializeLogging(argv[0]);
    if (!checkSentinelFile()) {
        return false;
    }
    if (!initializeDatabase()) {
        return false;
    }
    m_assetManager.reset(new AssetManager(m_database));
    return setThreadMask();
}

void ConfabCommon::shutdown() {
    m_database->close();
    // Delete pid sentinel file.
    fs::path pidPath(FLAGS_data_directory + "/pid");
    fs::remove(pidPath);
}

void ConfabCommon::initializeLogging(char* binaryName) {
    // Manually configure glog flag to get it to log to the data directory.
    FLAGS_log_dir = FLAGS_data_directory + "/log";
    FLAGS_alsologtostderr = FLAGS_chatty;
    google::InitGoogleLogging(binaryName);
}

bool ConfabCommon::checkSentinelFile() {
    // Check for existing pid sentinel file, meaning a version of confab is already running on this data directory.
    fs::path pidPath(FLAGS_data_directory + "/pid");
    if (fs::exists(pidPath)) {
        LOG(ERROR) << "Pid sentinel file " << pidPath << " already exists, exiting.";
        return false;
    } else {
        // Write sentinel file.
        std::ofstream pidFile;
        pidFile.open(pidPath);
        if (!pidFile) {
            LOG(ERROR) << "Error opening pid file " << pidPath << " for writing.";
            return false;
        }
        pidFile << getpid();
        pidFile.close();
    }
    return true;
}

bool ConfabCommon::initializeDatabase() {
    if (!m_database->open((FLAGS_data_directory + "/db").c_str(), FLAGS_create_new_database,
        FLAGS_database_cache_size_mb * 1024 * 1024)) {
        return -1;
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
            LOG(INFO) << "Updating confab version in database " << config.version().toString() << " to confab version "
                << Confab::confabVersion.toString();
            Confab::Config currentConfig(Confab::confabVersion);
            if (!m_database->store(Confab::Config::getConfigKey(), currentConfig.flatten())) {
                LOG(ERROR) << "Error writing updated Config record to database.";
                return false;
            }
        }
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

}  // namespace Confab

