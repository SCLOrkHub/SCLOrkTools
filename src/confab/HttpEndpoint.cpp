#include "HttpEndpoint.hpp"

#include "glog/logging.h"
#include "pistache/endpoint.h"
#include "pistache/router.h"

namespace Confab {

/*! Handler class for processing incoming HTTP requests. Uses the Pistache Router to connect specific 
 */
class HttpEndpoint::HttpHandler {
public:
    /*! Constructs an empty handler.
     *
     * \param listenPort The TCP port to listen on for HTTP requests.
     * \param numThreads The number of threads to use to listen on the port.
     *
     */
    HttpHandler(int listenPort, int numThreads) :
        m_listenPort(listenPort),
        m_numThreads(numThreads) { }

    /*! Setup HTTP URL routes and initialize server.
     */
    void setupRoutes() {
        Pistache::Address address(Pistache::Ipv4::any(), Pistache::Port(m_listenPort));
        m_server.reset(new Pistache::Http::Endpoint(address));
        auto opts = Pistache::Http::Endpoint::options().threads(m_numThreads);
        m_server->init(opts);

        Pistache::Rest::Routes::Get(m_router, "/asset/:key", Pistache::Rest::Routes::bind(
            &HttpEndpoint::HttpHandler::getAsset, this));
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
        LOG(INFO) << "processing HTTP request for /asset/" << keyString;
        response.send(Pistache::Http::Code::Ok, "Hello, World!\n");
    }

    int m_listenPort;
    int m_numThreads;
    std::shared_ptr<Pistache::Http::Endpoint> m_server;
    Pistache::Rest::Router m_router;
};

HttpEndpoint::HttpEndpoint(int listenPort, int numThreads) :
    m_handler(new HttpHandler(listenPort, numThreads)) {
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
