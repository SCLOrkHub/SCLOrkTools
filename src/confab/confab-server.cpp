#include "Constants.hpp"
#include "common/Version.hpp"

#include "gflags/gflags.h"
#include "spdlog/spdlog.h"
#include "spdlog/sinks/stdout_color_sinks.h"

#include <sys/types.h>
#include <unistd.h>

// Command line flags for the HTTP server.
DEFINE_int32(chat_port, 61000, "OSC TCP port for incoming chat messgaes");

int main(int argc, char* argv[]) {
    gflags::ParseCommandLineFlags(&argc, &argv, true);

    auto logger = spdlog::stdout_color_mt("console");
    spdlog::set_default_logger(logger);
    spdlog::info("Starting confab-server v{} on pid {}", Confab::confabVersion.toString(), getpid());



    spdlog::info("Termination signal received, exiting normally.");
    return 0;
}

