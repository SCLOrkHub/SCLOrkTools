#include "ConfabVersion.hpp"
#include "Database.hpp"

#include "common/Version.hpp"

// TODO: once reading/writing Asset records finalized, generalize technique to Config and remove this dependency.
#include "schemas/FlatConfig_generated.h"

#include <gflags/gflags.h>
#include <glog/logging.h>

DEFINE_bool(create_new_database, false, "If true confab will make a new database, if false confab will expect the "
    "database to already exist.");

DEFINE_int32(database_cache_size_mb, 16, "Size in megabytes of the memory cache the database should use.");

DEFINE_string(data_directory, "../data/confab", "Path where confab will store the database and log files. A zero or "
    "negative size will disable the cache");

int main(int argc, char* argv[]) {
    gflags::ParseCommandLineFlags(&argc, &argv, true);

    // Manually configure glog flag to get it to log to the data directory.
    FLAGS_log_dir = FLAGS_data_directory + "/log";
    google::InitGoogleLogging(argv[0]);

    LOG(INFO) << "Starting confab v" << Confab::confabVersion.toString();

    Confab::Database database;
    if (!database.open((FLAGS_data_directory + "/db").c_str(), FLAGS_create_new_database,
        FLAGS_database_cache_size_mb * 1024 * 1024)) {
        return -1;
    }

    // If a new database we write the configuration information for the first time. If an existing database we validate
    // that the version written is equal to or older than our current version.
    if (FLAGS_create_new_database) {
        // Verify that no existing configuration information is present.
        auto config = database.findConfig();
        if (config != nullptr) {
            LOG(ERROR) << "Create new database specified by database has an existing config key.";
            return -1;
        }

        database.writeConfig(Confab::confabVersion);

    } else {
        auto config = database.findConfig();

        auto databaseVersion = Common::Version(config->versionMajor(), config->versionMinor(), config->versionPatch());
        if (databaseVersion > Confab::confabVersion) {
            LOG(ERROR) << "Database records confab version " << databaseVersion.toString() << " which is newer than "
                << "confab version " << Confab::confabVersion.toString();
            return -1;
        }

        if (databaseVersion < Confab::confabVersion) {
            LOG(INFO) << "Updating confab version in database " << databaseVersion.toString() << " to confab version "
                << Confab::confabVersion.toString();
            database.writeConfig(Confab::confabVersion);
        }
    }

    LOG(INFO) << "Stopping confab normally.";
    return 0;
}
