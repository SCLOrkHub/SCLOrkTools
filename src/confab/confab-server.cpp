#include "AssetDatabase.hpp"
#include "ConfabCommon.hpp"
#include "Config.hpp"
#include "Constants.hpp"
#include "Database.hpp"
#include "HttpEndpoint.hpp"
#include "common/Version.hpp"

#include "gflags/gflags.h"
#include "glog/logging.h"

// Command line flags for the HTTP server.
DEFINE_int32(http_listen_port, 9080, "HTTP port on localhost to listen to incoming HTTP requests from confab peers.");
DEFINE_int32(http_listen_threads, 1, "Number of thread to use for listening to HTTP requests.");

// Command line flags for the database.
DEFINE_bool(create_new_database, false, "If true confab will make a new database, if -1 confab will expect the "
    "database to already exist.");
DEFINE_int32(database_cache_size_mb, 16, "Size in megabytes of the memory cache the database should use.");

const char* kConfigKey = "confab-db-config";

int main(int argc, char* argv[]) {
    Confab::ConfabCommon common;
    if (!common.initialize(argc, argv)) {
        return -1;
    }

    LOG(INFO) << "Starting confab-server v" << Confab::confabVersion.toString() << " on pid " << getpid();

    std::shared_ptr<Confab::Database> database;

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

    std::shared_ptr<Confab::AssetDatabase> assetDatabase(new Confab::AssetDatabase(database));

    LOG(INFO) << "Starting HTTP on port " << FLAGS_http_listen_port << ".";
    Confab::HttpEndpoint httpEndpoint(FLAGS_http_listen_port, FLAGS_http_listen_threads, assetDatabase);
    httpEndpoint.startServerThread();

    // BLOCK UNTIL SIGINT

    httpEndpoint.shutdown();
    database->close();
    common.shutdown();
    return 0;
}

