#ifndef SRC_CONFAB_HTTP_CLIENT_HPP_
#define SRC_CONFAB_HTTP_CLIENT_HPP_

#include "Record.hpp"

#include <experimental/filesystem>
#include <functional>
#include <memory>
#include <string>

namespace fs = std::experimental::filesystem;

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

    /*! Requests an asset metadata entry from the server. Blocks until an outcome is reached.
     *
     * \param key The asset key associated with this asset.
     * \param callback The function to call when the asset is downloaded, with the key of the provided asset along with
     *                 a pointer to the Asset or nullptr on error.
     */
    void getAsset(uint64_t key, std::function<void(uint64_t, RecordPtr)> callback);

    /*! Downloads, verifies, and concatendates AssetData records into the provided file path.
     *
     * \param key The asset key associated with these AssetData records.
     * \param fileSize Size of target file in bytes.
     * \param path The file path to save the asset data to.
     * \return true on success, false on error.
     */
    bool getAssetData(uint64_t key, size_t fileSize, const fs::path& path)

    /*! Closes any pending requests and shuts down.
     */
    void shutdown();

private:
    std::string m_serverAddress;
    std::unique_ptr<Pistache::Http::Client> m_client;
};

}  // namespace Confab

#endif  // SRC_CONFAB_HTTP_CLIENT_HPP_

