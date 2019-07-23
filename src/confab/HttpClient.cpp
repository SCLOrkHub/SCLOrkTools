#include "HttpClient.hpp"

#include "AssetManager.hpp"

#include "pistache/net.h"
#include "pistache/http.h"
#include "pistache/client.h"

namespace Confab {

HttpClient::HttpClient(const std::string& serverAddress) :
    m_serverAddress(serverAddress),
    m_client(new Pistache::Http::Client) {
    auto opts = Pistache::Http::Client::options()
        .keepAlive(true)
        .maxConnectionsPerHost(16)
        .threads(2);
    m_client->init(opts);
}

HttpClient::~HttpClient() {
}

void HttpClient::getAsset(uint64_t key, std::function<void(uint64_t, RecordPtr)> callback) {
    std::string request = m_serverAddress + "/asset/" + AssetManager::keyToString(key);

}

void HttpClient::shutdown() {
    m_client->shutdown();
}

}  // namespace Confab

