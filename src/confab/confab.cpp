#include "AssetManager.hpp"
#include "ConfabCommon.hpp"
#include "Constants.hpp"
#include "OscHandler.hpp"
#include "common/Version.hpp"

#include "gflags/gflags.h"
#include "glog/logging.h"

DEFINE_int32(osc_listen_port, 4248, "UDP port on localhost to listen for incoming OSC commands from SuperCollider.");
DEFINE_int32(osc_respond_port, 4249, "UDP port on localhost to send response messages to SuperCollider.");

int main(int argc, char* argv[]) {
    Confab::ConfabCommon common;
    if (!common.initialize(argc, argv)) {
        return -1;
    }

    LOG(INFO) << "Starting confab v" << Confab::confabVersion.toString() << " on pid " << getpid();

    LOG(INFO) << "Opening up OSC ports for listen on " << FLAGS_osc_listen_port << " and respond on "
        << FLAGS_osc_respond_port;
    Confab::OscHandler osc(FLAGS_osc_listen_port, FLAGS_osc_respond_port, common.assetManager());

    // It is possible to install signal handlers for SIGINT and SIGTERM using std::signal, but putting a condition
    // variable call, or any thread synchronization primitives, in an asynchronous system event handler is considered
    // unsafe. One recommended safe way to resolve this is to have the main thread open a socket or other file
    // descriptor and block on that socket until a signal is received, and then can signal that socket in the signal
    // handler. Because we are already processing data on the UDP socket inside of osc, we rather wait until that
    // socket catches a SIGINT.
    osc.listenUntilSigInt();

    LOG(INFO) << "Termination signal caught, stopping confab normally.";
    common.shutdown();
    return 0;
}

