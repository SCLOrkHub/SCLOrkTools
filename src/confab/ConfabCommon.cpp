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
DEFINE_string(data_directory, "../data/confab", "Path where confab will store the m_database and log files. A zero or "
    "negative size will disable the cache");

// Command line flags for the m_database.
DEFINE_bool(create_new_m_database, false, "If true confab will make a new m_database, if -1 confab will expect the "
    "m_database to already exist.");
DEFINE_int32(m_database_cache_size_mb, 16, "Size in megabytes of the memory cache the m_database should use.");

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

    if (!m_database->open((FLAGS_data_directory + "/db").c_str(), FLAGS_create_new_m_database,
        FLAGS_m_database_cache_size_mb * 1024 * 1024)) {
        return -1;
    }

    // If a new m_database we write the configuration information for the first time. If an existing m_database we validate
    // that the version written is equal to or older than our current version.
    if (FLAGS_create_new_m_database) {
        // Verify that no existing configuration information is present.
        auto configRecord = m_database->load(Confab::Config::getConfigKey());
        if (!configRecord->empty()) {
            LOG(ERROR) << "Create new m_database specified by m_database has an existing config key.";
            return -1;
        }

        Confab::Config config(Confab::confabVersion);

        if (!m_database->store(Confab::Config::getConfigKey(), config.flatten())) {
            LOG(ERROR) << "Error writing config information to m_database.";
            return -1;
        } else {
            LOG(INFO) << "Wrote new config record to m_database.";
        }
    } else {
        auto configRecord = m_database->load(Confab::Config::getConfigKey());
        if (configRecord->empty()) {
            LOG(ERROR) << "Error reaading configuration information from m_database.";
            return -1;
        }
        auto config = Confab::Config::LoadConfig(configRecord);

        if (config.version() > Confab::confabVersion) {
            LOG(ERROR) << "Database records confab version " << config.version().toString() << " which is newer than "
                << "confab version " << Confab::confabVersion.toString();
            return -1;
        }

        if (config.version() < Confab::confabVersion) {
            LOG(INFO) << "Updating confab version in m_database " << config.version().toString() << " to confab version "
                << Confab::confabVersion.toString();
            Confab::Config currentConfig(Confab::confabVersion);
            if (!m_database->store(Confab::Config::getConfigKey(), currentConfig.flatten())) {
                LOG(ERROR) << "Error writing updated Config record to m_database.";
                return -1;
            }
        }
    }

    std::shared_ptr<Confab::AssetDatabase> assetDatabase(new Confab::AssetDatabase(m_database));
}

}  // namespace Confab

