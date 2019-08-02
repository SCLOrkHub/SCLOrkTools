#ifndef SRC_CONFAB_HTTP_CLIENT_HPP_
#define SRC_CONFAB_HTTP_CLIENT_HPP_

#include "Asset.hpp"
#include "Record.hpp"

#include <experimental/filesystem>
#include <functional>
#include <memory>
#include <random>
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
     * This function and getAssetData rely on callbacks to return their data, both to allow the returning of multiple
     * arguments in a convenient fashion, but also more importantly to avoid a copy of the record out of the internal
     * HTTP data structures, that go out of scope when these functions return. By calling a callback, we are able to
     * expose the raw pointers inside of these data structures while they are still in scope.
     *
     * \param key The asset key associated with this asset.
     * \param callback The function to call when the asset is downloaded, with the key of the provided asset along with
     *                 a non-owning pointer to the FlatAsset or an empty Record on error.
     */
    void getAsset(uint64_t key, std::function<void(uint64_t, RecordPtr)> callback);

    /*! Requests an asset by name from the server. Blocks until return.
     *
     * \param name The name of the Asset to look up.
     * \param callback The function to call when the asset is downloaded, with a pointer to the requested Asset or an
     *                 empty Record on error.
     */
    void getNamedAsset(const std::string& name, std::function<void(RecordPtr)> callback);

    /*! Retrieves an asset data chunk from the server. Blocks until an outcome is resolved.
     *
     * \param key The asset key associated with these AssetData records.
     * \param chunk The chunk number to download.
     * \param callback The function to call when the FlatAssetData record is downloaded, with the key of the asset, the
     *                 chunk number, and the FlatAssetData record, or an empty Record on error.
     */
    void getAssetData(uint64_t key, uint64_t chunk, std::function<void(uint64_t, uint64_t, RecordPtr)> callback);

    /*! Uploads a new Asset with inline data to the server.
     *
     * \param type The Asset type.
     * \param name The Asset name, can be "".
     * \param author An optional Asset key.
     * \param deprecates An optional Asset key.
     * \param size The size of the data pointed to by inlineData, should be smaller than kDataChunkSize
     * \param inlineData The inline Asset data to serialize.
     * \return The computed key for this Asset, or zero on error.
     */
    uint64_t postInlineAsset(Asset::Type type, const std::string& name, uint64_t author, uint64_t deprecates,
            uint64_t size, const uint8_t* inlineData);

    /*! Uploads a new Asset along with all AssetData chunks in the file to the server.
     *
     * \param type The Asset type.
     * \param name The Asset name, can be "".
     * \param author An optional Asset key.
     * \param deprecates An optional Asset key.
     * \param assetFile The path of the file to ingest.
     * \return The computed key for this Asset, or zero on error.
     */
    uint64_t postFileAsset(Asset::Type type, const std::string& name, uint64_t author, uint64_t deprecates,
            const fs::path& assetFile);

    /*! Closes any pending requests and shuts down.
     */
    void shutdown();

private:
    const std::string m_serverAddress;
    std::unique_ptr<Pistache::Http::Client> m_client;
    std::random_device m_randomDevice;
    std::uniform_int_distribution<uint64_t> m_distribution;
};

}  // namespace Confab

#endif  // SRC_CONFAB_HTTP_CLIENT_HPP_

