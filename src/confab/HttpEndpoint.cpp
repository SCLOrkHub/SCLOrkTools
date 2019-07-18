#include "HttpEndpoint.hpp"

#include "glog/logging.h"
#include "pistache/endpoint.h"


namespace Confab {

/*! Handler class for processing incoming HTTP requests.
 */
class HttpEndpoint::HttpHandler : public Pistache::Http::Handler {
public:
    HTTP_PROTOTYPE(HttpEndpoint::HttpHandler)

    /*! Function invoked on incoming HTTP requests, provides a response based on request.
     *
     * \param request The request URL and details.
     * \param response The object used to respond to the incoming request with data and status.
     */
    void onRequest(const Pistache::Http::Request& request, Pistache::Http::ResponseWriter response) {
        response.send(Pistache::Http::Code::Ok, "Hello, World!\n");
    }
};

HttpEndpoint::HttpEndpoint(int listenPort, int numThreads) :
    m_listenPort(listenPort),
    m_numThreads(numThreads) {
}

HttpEndpoint::~HttpEndpoint() {
}

void HttpEndpoint::startServerThread() {
    Pistache::Address address(Pistache::Ipv4::any(), Pistache::Port(m_listenPort));
    auto opts = Pistache::Http::Endpoint::options().threads(m_numThreads);
    m_server.reset(new Pistache::Http::Endpoint(address));
    m_handler.reset(new HttpHandler);
    m_server->init(opts);
    m_server->setHandler(m_handler);
    m_server->serveThreaded();
}

void HttpEndpoint::shutdown() {
    m_server->shutdown();
}

}  // namespace Confab
