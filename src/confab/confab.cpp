#include "AssetManager.hpp"
#include "Config.hpp"
#include "Constants.hpp"
#include "Database.hpp"
#include "HttpEndpoint.hpp"
#include "OscHandler.hpp"

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
DEFINE_int32(http_listen_port, 9080, "HTTP port on localhost to listen to incoming HTTP requests from confab peers.");
DEFINE_int32(http_listen_threads, 1, "Number of thread to use for listening to HTTP requests.");
DEFINE_int32(osc_listen_port, 4248, "UDP port on localhost to listen for incoming OSC commands from SuperCollider.");
DEFINE_int32(osc_respond_port, 4249, "UDP port on localhost to send response messages to SuperCollider.");

DEFINE_string(data_directory, "../data/confab", "Path where confab will store the database and log files. A zero or "
    "negative size will disable the cache");

const char* kConfigKey = "confab-db-config";

int main(int argc, char* argv[]) {
    gflags::ParseCommandLineFlags(&argc, &argv, true);

    // Manually configure glog flag to get it to log to the data directory.
    FLAGS_log_dir = FLAGS_data_directory + "/log";
    FLAGS_alsologtostderr = FLAGS_chatty;
    google::InitGoogleLogging(argv[0]);

    // Check for existing pid sentinel file, meaning a version of confab is already running on this data directory.
    fs::path pidPath(FLAGS_data_directory + "/pid");
    if (fs::exists(pidPath)) {
        LOG(ERROR) << "Pid sentinel file " << pidPath << " already exists, exiting.";
        return -1;
    } else {
        // Write sentinel file.
        std::ofstream pidFile;
        pidFile.open(pidPath);
        if (!pidFile) {
            LOG(ERROR) << "Error opening pid file " << pidPath << " for writing.";
            return -1;
        }
        pidFile << getpid();
        pidFile.close();
    }

    LOG(INFO) << "Starting confab v" << Confab::confabVersion.toString() << " on pid " << getpid();

    std::shared_ptr<Confab::Database> database(new Confab::Database());
    if (!database->open((FLAGS_data_directory + "/db").c_str(), FLAGS_create_new_database,
        FLAGS_database_cache_size_mb * 1024 * 1024)) {
        return -1;
    }

    // If a new database we write the configuration information for the first time. If an existing database we validate
    // that the version written is equal to or older than our current version.
    if (FLAGS_create_new_database) {
        // Verify that no existing configuration information is present.
        auto configRecord = database->load(Confab::Config::getConfigKey());
        if (!configRecord->empty()) {
            LOG(ERROR) << "Create new database specified by database has an existing config key.";
            return -1;
        }

        Confab::Config config(Confab::confabVersion);

        if (!database->store(Confab::Config::getConfigKey(), config.flatten())) {
            LOG(ERROR) << "Error writing config information to database.";
            return -1;
        } else {
            LOG(INFO) << "Wrote new config record to database.";
        }
    } else {
        auto configRecord = database->load(Confab::Config::getConfigKey());
        if (configRecord->empty()) {
            LOG(ERROR) << "Error reaading configuration information from database.";
            return -1;
        }
        auto config = Confab::Config::LoadConfig(configRecord);

        if (config.version() > Confab::confabVersion) {
            LOG(ERROR) << "Database records confab version " << config.version().toString() << " which is newer than "
                << "confab version " << Confab::confabVersion.toString();
            return -1;
        }

        if (config.version() < Confab::confabVersion) {
            LOG(INFO) << "Updating confab version in database " << config.version().toString() << " to confab version "
                << Confab::confabVersion.toString();
            Confab::Config currentConfig(Confab::confabVersion);
            if (!database->store(Confab::Config::getConfigKey(), currentConfig.flatten())) {
                LOG(ERROR) << "Error writing updated Config record to database.";
                return -1;
            }
        }
    }

    // Create thread masks for ignoring SIGINT signals here, as the OSC handler will catch the SIGINT and terminate the
    // program itself on that.
    sigset_t set;
    sigemptyset(&set);
    sigaddset(&set, SIGTERM);
    if (pthread_sigmask(SIG_BLOCK, &set, nullptr) != 0) {
        LOG(ERROR) << "error setting pthread thread mask to ignore SIGINT.";
        return -1;
    }

    std::shared_ptr<Confab::AssetManager> assetManager(new Confab::AssetManager(database));

    LOG(INFO) << "Starting HTTP on port " << FLAGS_http_listen_port << ".";
    Confab::HttpEndpoint httpEndpoint(FLAGS_http_listen_port, FLAGS_http_listen_threads, assetManager);
    httpEndpoint.startServerThread();

    LOG(INFO) << "Opening up OSC ports for listen on " << FLAGS_osc_listen_port << " and respond on "
        << FLAGS_osc_respond_port;
    Confab::OscHandler osc(FLAGS_osc_listen_port, FLAGS_osc_respond_port, assetManager);

    // It is possible to install signal handlers for SIGINT and SIGTERM using std::signal, but putting a condition
    // variable call, or any thread synchronization primitives, in an asynchronous system event handler is considered
    // unsafe. One recommended safe way to resolve this is to have the main thread open a socket or other file
    // descriptor and block on that socket until a signal is received, and then can signal that socket in the signal
    // handler. Because we are already processing data on the UDP socket inside of osc, we rather wait until that
    // socket catches a SIGINT.
    // NOTE: other threads created to handle web APIs will need to have signal masking or this will break.
    osc.listenUntilSigInt();

    LOG(INFO) << "Termination signal caught, stopping confab normally.";
    httpEndpoint.shutdown();
    database->close();
    // Delete pid sentinel file.
    fs::remove(pidPath);

    return 0;
}

