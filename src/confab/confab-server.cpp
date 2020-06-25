#include "ChatServer.hpp"
#include "Constants.hpp"
#include "common/Version.hpp"

#include "fmt/core.h"
#include "gflags/gflags.h"
#include "spdlog/spdlog.h"
#include "spdlog/sinks/stdout_color_sinks.h"

#include <pthread.h>
#include <signal.h>
#include <sys/types.h>
#include <unistd.h>

// Command line flags for the HTTP server.
DEFINE_int32(chatPort, 61000, "OSC TCP port for incoming chat messgaes");

int main(int argc, char* argv[]) {
    gflags::ParseCommandLineFlags(&argc, &argv, true);

    auto logger = spdlog::stdout_color_mt("console");
    spdlog::set_default_logger(logger);
    spdlog::info("Starting confab-server v{} on pid {}", Confab::confabVersion.toString(), getpid());

    // Create thread masks for ignoring SIGINT signals here, as the OSC handler will catch the SIGINT and terminate the
    // program itself on that.
    sigset_t signals;
    sigemptyset(&signals);
    sigaddset(&signals, SIGHUP);
    sigaddset(&signals, SIGINT);
    sigaddset(&signals, SIGTERM);
    if (pthread_sigmask(SIG_BLOCK, &signals, nullptr) != 0) {
        spdlog::error("error setting pthread thread mask to ignore SIGINT.");
        return -1;
    }

    Confab::ChatServer chatServer;
    if (!chatServer.create(fmt::format("{}", FLAGS_chatPort))) {
        spdlog::error("Failed to create chat server on port {}", FLAGS_chatPort);
        return -1;
    }

    if (!chatServer.run()) {
        spdlog::error("Failed to run ChatServer thread.");
        return -1;
    }

    // Block until SIGINT
    sigemptyset(&signals);
    sigaddset(&signals, SIGINT);
    sigaddset(&signals, SIGTERM);
    sigaddset(&signals, SIGHUP);
    int signal = 0;
    // Block until termination signal sent.
    int status = sigwait(&signals, &signal);
    if (status == 0) {
        spdlog::info("Termination signal received, exiting normally.");
    } else {
        spdlog::error("got error from sigwait {}", status);
    }

    chatServer.stop();
    chatServer.destroy();
    return 0;
}

