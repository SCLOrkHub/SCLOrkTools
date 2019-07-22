#include "AssetManager.hpp"
#include "ConfabCommon.hpp"
#include "Constants.hpp"
#include "HttpEndpoint.hpp"
#include "common/Version.hpp"

#include "gflags/gflags.h"
#include "glog/logging.h"

DEFINE_int32(http_listen_port, 9080, "HTTP port on localhost to listen to incoming HTTP requests from confab peers.");
DEFINE_int32(http_listen_threads, 1, "Number of thread to use for listening to HTTP requests.");

int main(int argc, char* argv[]) {
    Confab::ConfabCommon common;
    if (!common.initialize(argc, argv)) {
        return -1;
    }

    LOG(INFO) << "Starting confab-server v" << Confab::confabVersion.toString() << " on pid " << getpid();

    LOG(INFO) << "Starting HTTP on port " << FLAGS_http_listen_port << ".";
    Confab::HttpEndpoint httpEndpoint(FLAGS_http_listen_port, FLAGS_http_listen_threads, common.assetManager());
    httpEndpoint.startServerThread();

    // BLOCK UNTIL SIGINT

    httpEndpoint.shutdown();
    common.shutdown();
    return 0;
}

