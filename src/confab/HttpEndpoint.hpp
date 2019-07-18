#ifndef SRC_CONFAB_HTTP_ENDPOINT_HPP_
#define SRC_CONFAB_HTTP_ENDPOINT_HPP_

#include <memory>

namespace Confab {

/*! Class for listening and responding to HTTP messages from multiple downstream Confab instances.
 */
class HttpEndpoint {
public:

    /*! Constructs an HttpHandler to listen on the port with the supplied number of threads.
     *
     * \param listenPort The TCP port to listen on for HTTP requests.
     * \param numThreads The number of threads to use to listen on the port.
     */
    HttpEndpoint(int listenPort, int numThreads);

    /*! Destructs an HttpHandler. Declared here to let us use std::unique_ptr with forward-declared classes.
     */
    ~HttpEndpoint();

    /*! Starts a thread that will listen on the provided TCP port and process incoming requests for storage and
     * retrieval of assets.
     */
    void startServerThread();

    /*! Stops serving threads, closes ports.
     */
    void shutdown();

private:
    class HttpHandler;

    int m_listenPort;
    int m_numThreads;
    std::unique_ptr<HttpHandler> m_handler;
};

}  // namespace Confab

#endif  // SRC_CONFAB_HTTP_ENDPOINT_HPP_

