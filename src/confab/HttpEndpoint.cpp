#include "HttpEndpoint.hpp"

#include "Asset.hpp"
#include "AssetManager.hpp"

#include "glog/logging.h"
#include "pistache/endpoint.h"
#include "pistache/router.h"

namespace Confab {

/*! Handler class for processing incoming HTTP requests. Uses the Pistache Router to connect specific REST-style API
 * queries from clients to private method calls within this class.
 */
class HttpEndpoint::HttpHandler {
public:
    /*! Constructs an empty handler.
     *
     * \param listenPort The TCP port to listen on for HTTP requests.
     * \param numThreads The number of threads to use to listen on the port.
     * \param assetManager A pointer to the shared AssetManager instance.
     */
    HttpHandler(int listenPort, int numThreads, std::shared_ptr<AssetManager> assetManager) :
        m_listenPort(listenPort),
        m_numThreads(numThreads),
        m_assetManager(assetManager) { }

    /*! Setup HTTP URL routes and initialize server.
     */
    void setupRoutes() {
        Pistache::Address address(Pistache::Ipv4::any(), Pistache::Port(m_listenPort));
        m_server.reset(new Pistache::Http::Endpoint(address));
        auto opts = Pistache::Http::Endpoint::options().threads(m_numThreads);
        m_server->init(opts);

        Pistache::Rest::Routes::Get(m_router, "/asset/:key", Pistache::Rest::Routes::bind(
            &HttpEndpoint::HttpHandler::getAsset, this));
        Pistache::Rest::Routes::Post(m_router, "/asset/:key", Pistache::Rest::Routes::bind(
            &HttpEndpoint::HttpHandler::postAsset, this));
    }

    /*! Starts a thread that will listen on the provided TCP port and process incoming requests for storage and
     * retrieval of assets.
     */
    void startServerThread() {
        m_server->setHandler(m_router.handler());
        m_server->serveThreaded();
    }

    /*! Stops serving threads, closes ports.
     */
    void shutdown() {
        m_server->shutdown();
    }

private:
    void getAsset(const Pistache::Rest::Request& request, Pistache::Http::ResponseWriter response) {
        auto keyString = request.param(":key").as<std::string>();
        LOG(INFO) << "processing HTTP GET request for /asset/" << keyString;
        uint64_t key = Asset::stringToKey(keyString);
        m_assetManager->findAsset(key, [&keyString, &response](uint64_t loadedKey, RecordPtr record) {
            if (record->empty()) {
                LOG(INFO) << "HTTP get request for Asset " << keyString << "not found, returning 404.";
                response.headers()
                    .add<Pistache::Http::Header::Server>("confab");
                response.send(Pistache::Http::Code::Not_Found);
            } else {
                LOG(INFO) << "HTTP get request returning Asset data for " << keyString;
                response.headers()
                    .add<Pistache::Http::Header::Server>("confab")
                    .add<Pistache::Http::Header::ContentType>(MIME(Application, OctetStream));
                auto stream = response.stream(Pistache::Http::Code::Ok);
                stream.write(reinterpret_cast<const char*>(record->data().data()), record->data().size());
                stream.ends();
            }
        });
    }

    void postAsset(const Pistache::Rest::Request& request, Pistache::Http::ResponseWriter response) {
        auto keyString = request.param(":key").as<std::string>();
        LOG(INFO) << "processing HTTP POST request for /asset/" << keyString;

        uint64_t key = Asset::stringToKey(keyString);
        SizedPointer postedData(reinterpret_cast<const uint8_t*>(response.body().c_str()), response.body().size());
        m_assetManager->storeAsset(key, postedData, [&keyString, &response](bool status) {
            response.headers().add<Pistache::Http::Header::Server>("confab");
            if (status) {
                LOG(INFO) << "sending OK response after storing asset " << keyString;
                response.send(Pistache::Http::Code::Ok);
            } else {
                LOG(ERROR) << "sending error response after failure to store asset " << keyString;
                response.send(Pistache::Http::Code::Internal_Server_Error);
            }
        });
    }

    int m_listenPort;
    int m_numThreads;
    std::shared_ptr<AssetManager> m_assetManager;
    std::shared_ptr<Pistache::Http::Endpoint> m_server;
    Pistache::Rest::Router m_router;
};

HttpEndpoint::HttpEndpoint(int listenPort, int numThreads, std::shared_ptr<AssetManager> assetManager) :
    m_handler(new HttpHandler(listenPort, numThreads, assetManager)) {
}

HttpEndpoint::~HttpEndpoint() {
}

void HttpEndpoint::startServerThread() {
    m_handler->setupRoutes();
    m_handler->startServerThread();
}

void HttpEndpoint::shutdown() {
    m_handler->shutdown();
}

}  // namespace Confab

