#include "ChatServer.hpp"
#include "Constants.hpp"
#include "common/Version.hpp"

#include "fmt/core.h"
#include "gflags/gflags.h"
#include "spdlog/sinks/basic_file_sink.h"
#include "spdlog/sinks/stdout_color_sinks.h"
#include "spdlog/spdlog.h"

#include <memory>
#include <pthread.h>
#include <signal.h>
#include <sys/types.h>
#include <unistd.h>

// Command line flags for the HTTP server.
DEFINE_int32(chatPort, 61010, "OSC TCP port for incoming chat messgaes");
DEFINE_int32(timeout, 10, "The timeout in seconds before automatically disconnecting an unresponsive client.");
DEFINE_int32(maxMessagesPerRequest, 3, "Maximum number of messages to respond to per update request.");
DEFINE_string(logFile, "", "A path to log to a file to. If not provided, file logging is disabled.");

int main(int argc, char* argv[]) {
    gflags::ParseCommandLineFlags(&argc, &argv, true);

    auto consoleLog = std::make_shared<spdlog::sinks::stdout_color_sink_mt>();
    consoleLog->set_level(spdlog::level::info);

    std::shared_ptr<spdlog::sinks::basic_file_sink_mt> fileLog;
    if (FLAGS_logFile.size() > 0) {
        spdlog::info("additionally logging to file at {}", FLAGS_logFile);
        fileLog = std::make_shared<spdlog::sinks::basic_file_sink_mt>(FLAGS_logFile.data(), false);
        fileLog->set_level(spdlog::level::info);
        spdlog::default_logger()->sinks().push_back(fileLog);
    }

    spdlog::info("====== starting confab-server v{} on pid {}", Confab::confabVersion.toString(), getpid());

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

    Confab::ChatServer chatServer(FLAGS_timeout, FLAGS_maxMessagesPerRequest);
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

