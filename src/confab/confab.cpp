#include "CacheManager.hpp"
#include "ConfabCommon.hpp"
#include "Constants.hpp"
#include "HttpClient.hpp"
#include "OscHandler.hpp"
#include "common/Version.hpp"

#include "gflags/gflags.h"
#include "glog/logging.h"

#include <experimental/filesystem>
#include <future>
#include <memory>

DEFINE_bool(validate_file_cache, true, "If true confab will check the hash of every file in the cache, removing any "
        "files that are detected corrupt.");

DEFINE_int32(max_cache_size_gb, 4, "Maximum size of Asset file cache in gigabytes");
DEFINE_int32(osc_listen_port, 4248, "UDP port on localhost to listen for incoming OSC commands from SuperCollider.");
DEFINE_int32(osc_respond_port, 4249, "UDP port on localhost to send response messages to SuperCollider.");

DEFINE_string(server_url, "http://sclork-s01.local:9080", "Address for HTTP communication with Confab server.");

int main(int argc, char* argv[]) {
    Confab::ConfabCommon common;
    if (!common.initialize(argc, argv)) {
        return -1;
    }

    LOG(INFO) << "Starting confab v" << Confab::confabVersion.toString() << " on pid " << getpid();

    std::shared_ptr<Confab::HttpClient> httpClient(new Confab::HttpClient(FLAGS_server_url));
    uint64_t maxCache = static_cast<uint64_t>(FLAGS_max_cache_size_gb) * 1024ULL * 1024ULL * 1024ULL;
    std::shared_ptr<Confab::CacheManager> cacheManager(new Confab::CacheManager(FLAGS_data_directory + "/cache",
        maxCache, httpClient));
    std::async(std::launch::async, [&cacheManager] {
        cacheManager->checkExistingEntries(FLAGS_validate_file_cache);
    });

    LOG(INFO) << "Opening up OSC ports for listen on " << FLAGS_osc_listen_port << " and respond on "
        << FLAGS_osc_respond_port;
    Confab::OscHandler osc(FLAGS_osc_listen_port, FLAGS_osc_respond_port, common.assetDatabase(), httpClient,
        cacheManager);
    osc.run();

    common.waitForTerminationSignal();

    LOG(INFO) << "Termination signal caught, stopping confab normally.";
    osc.shutdown();
    httpClient->shutdown();
    common.shutdown();
    return 0;
}

