#ifndef SRC_CONFAB_HTTP_CLIENT_HPP_
#define SRC_CONFAB_HTTP_CLIENT_HPP_

#include <memory>
#include <string>

namespace Pistache {
namespace Http {
class Client;
}   // namespace Http
}   // namespace Pistache

namespace Confab {

/*! Class responsible for communication with upstream confab instances.
 */
class HttpClient {
public:
    /*! Construct a new HttpClient for use in upstream communication.
     *
     * \param serverAddress The address part of the URLs that the client will construct, such as
     *                      "http://sclork-s01.local:9080".
     */
    HttpClient(const std::string& serverAddress);

    /*! Destructs an HttpClient.
     */
    ~HttpClient();

    /*! Closes any pending requests and shuts down.
     */
    void shutdown();

private:
    std::string m_serverAddress;
    std::unique_ptr<Pistache::Http::Client> m_client;
};

}  // namespace Confab

#endif  // SRC_CONFAB_HTTP_CLIENT_HPP_

