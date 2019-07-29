#include "ConfabCommon.hpp"
#include "Constants.hpp"
#include "HttpEndpoint.hpp"
#include "common/Version.hpp"

#include "gflags/gflags.h"
#include "glog/logging.h"

#include <signal.h>

// Command line flags for the HTTP server.
DEFINE_int32(http_listen_port, 9080, "HTTP port on localhost to listen to incoming HTTP requests from confab peers.");
DEFINE_int32(http_listen_threads, 1, "Number of thread to use for listening to HTTP requests.");

int main(int argc, char* argv[]) {
    Confab::ConfabCommon common;
    if (!common.initialize(argc, argv)) {
        return -1;
    }

    LOG(INFO) << "Starting confab-server v" << Confab::confabVersion.toString() << " on pid " << getpid();

    LOG(INFO) << "Starting HTTP on port " << FLAGS_http_listen_port << ".";
    Confab::HttpEndpoint httpEndpoint(FLAGS_http_listen_port, FLAGS_http_listen_threads, common.assetDatabase());

    httpEndpoint.startServerThread();

    sigset_t signals;
    sigemptyset(&signals);
    sigaddset(&signals, SIGINT);
    int signal = 0;
    // Block until SIGINT.
    int status = sigwait(&signals, &signal);
    if (status == 0) {
        LOG(INFO) << "got signal " << signal;
    } else {
        LOG(ERROR) << "got error from sigwait " << status;
    }

    LOG(INFO) << "termination signal received, exiting normally.";

    httpEndpoint.shutdown();
    common.shutdown();
    return 0;
}

