#include "ConfabCommon.hpp"

#include "AssetManager.hpp"
#include "Config.hpp"
#include "Constants.hpp"
#include "Database.hpp"
#include "common/Version.hpp"

#include "gflags/gflags.h"
#include "glog/logging.h"

#include <fstream>
#include <memory>
#include <pthread.h>
#include <signal.h>

namespace fs = std::experimental::filesystem;

// Command line flags for logging.
DEFINE_bool(chatty, false, "If true confab will log everything to stderr as well as to log files.");

namespace Confab {

bool ConfabCommon::initialize(int argc, char* argv[]) {
    gflags::ParseCommandLineFlags(&argc, &argv, true);
    initializeLogging(argv[0]);

    // TODO: create/validate data directory.

    if (!checkSentinelFile()) {
        return false;
    }
    return setThreadMask();
}

void ConfabCommon::shutdown() {
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
        LOG(ERROR) << "Pid sentinel file " << pidPath << " already exists, exiting.";
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

}  // namespace Confab

