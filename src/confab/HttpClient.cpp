#include "HttpClient.hpp"

#include "Asset.hpp"

#include "pistache/net.h"
#include "pistache/http.h"
#include "pistache/client.h"

namespace Confab {

class ClientRecord : public Record {
public:
    /*! Default constructor not supported, use makeEmptyRecord().
     */
    ClientRecord() = delete;

    /*! Construct a record that wraps a Pistache response string.
     */
    ClientRecord(const std::string& responseString) :
        m_data(reinterpret_cast<const uint8_t*>(responseString.c_str()), responseString.size()) {
    }

    /*! Deletes a ClientRecord, does nothing because the underlying string is not owned by this class.
     */
    ~ClientRecord() override { }

    /*! True if this ClientRecord has no results.
     *
     * \return true if this Record is empty, false if it has content.
     */
    bool empty() const override { return m_data.empty() }

    /*! A pointer to the data contents of this Record.
     *
     * \return A pointer to the data contents of this Record.
     */
    const SizedPointer data() const override {
        return m_data;
    }

    /*! A pointer to the key contents of this Record.
     *
     * \return Always empty.
     */
    const SizedPointer key() const override {
        return SizedPointer();
    }

private:
    const SizedPointer m_data;
}

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
    std::string request = m_serverAddress + "/asset/" + Asset::keyToString(key);
    LOG(INFO) << "HttpClient issuing Asset request to " << request;

    auto response = m_client->get(request).send();
    response.then([&key, &callback, &request](Pistache::Http::Response response) {
        if (response.code() == Pistache::Http::Code::Ok) {
            LOG(INFO) << "received Ok response for Asset request " << request;
            callback(key, RecordPtr(new ClientRecord(response.body()));
        } else {
            LOG(ERROR) << "error code " << response.code() << "on Asset request " << request;
            callback(key, makeEmptyRecord());
        }
    });

    response.wait();
}

bool HttpClient::getAssetData(uint64_t key, size_t fileSize, const fs::path& path) {

}

void HttpClient::shutdown() {
    m_client->shutdown();
}

}  // namespace Confab

