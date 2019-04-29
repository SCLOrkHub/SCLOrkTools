#include "ConfabVersion.hpp"
#include "Database.hpp"

#include <gflags/gflags.h>
#include <glog/logging.h>

DEFINE_bool(create_new_database, false, "If true confab will make a new database, if false confab will expect the "
    "database to already exist.");

DEFINE_string(data_directory, "../data/confab", "Path where confab will store the database and log files.");

int main(int argc, char* argv[]) {
    gflags::ParseCommandLineFlags(&argc, &argv, true);

    // Manually configure glog flag to get it to log to the data directory.
    FLAGS_log_dir = FLAGS_data_directory + "/log";
    google::InitGoogleLogging(argv[0]);

    LOG(INFO) << "Starting confab v" << Confab::kConfabVersionMajor << "." << Confab::kConfabVersionMinor << "."
              << Confab::kConfabVersionSub;

    Confab::Database database;
    if (!database.open((FLAGS_data_directory + "/db").c_str(), FLAGS_create_new_database)) {
        return -1;
    }
    if (!database.validate()) {
        return -1;
    }

    LOG(INFO) << "Stopping confab normally.";
    return 0;
}
