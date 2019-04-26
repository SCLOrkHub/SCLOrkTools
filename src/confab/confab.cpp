#include <gflags/gflags.h>

#include <iostream>

DEFINE_string(database_file_path, "../../data/confab.db", "Path to the LevelDB database file to use for content");

int main(int argc, char* argv[]) {
    gflags::ParseCommandLineFlags(&argc, &argv, true);


}
