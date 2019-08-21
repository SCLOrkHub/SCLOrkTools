#ifndef SRC_CONFAB_HTTP_CLIENT_HPP_
#define SRC_CONFAB_HTTP_CLIENT_HPP_

#include "Asset.hpp"
#include "Record.hpp"

#include <condition_variable>
#include <experimental/filesystem>
#include <functional>
#include <memory>
#include <mutex>
#include <random>
#include <string>
#include <thread>

namespace fs = std::experimental::filesystem;

namespace Pistache {
namespace Http {
class Client;
}   // namespace Http
}   // namespace Pistache

namespace Confab {

class State;

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

    /*! Uploads a new Asset with inline data to the server. Blocking.
     *
     * \param type The Asset type.
     * \param name The Asset name, can be "".
     * \param author An optional Asset key.
     * \param deprecates An optional Asset key.
     * \param listIds A comma-separated concatenated string of list ids to add this asset to.
     * \param size The size of the data pointed to by inlineData, should be smaller than kDataChunkSize
     * \param inlineData The inline Asset data to serialize.
     * \return The computed key for this Asset, or zero on error.
     */
    uint64_t postInlineAsset(Asset::Type type, const std::string& name, uint64_t author, uint64_t deprecates,
            const std::string& listIds, uint64_t size, const uint8_t* inlineData);

    /*! Uploads a new Asset along with all AssetData chunks in the file to the server. Blocking.
     *
     * \param type The Asset type.
     * \param name The Asset name, can be "".
     * \param author An optional Asset key.
     * \param deprecates An optional Asset key.
     * \param listIds A comma-separated concatenated string of list ids to add this asset to.
     * \param assetFile The path of the file to ingest.
     * \return The computed key for this Asset, or zero on error.
     */
    uint64_t postFileAsset(Asset::Type type, const std::string& name, uint64_t author, uint64_t deprecates,
            const std::string& listIds, const fs::path& assetFile);

    /*! Requests a list metadata entry from the server. Blocking.
     *
     * \param key The key of the list to retrieve.
     * \param callback The function to callback with a non-owning pointer to the FlatList structure, or empty on error.
     */
    void getList(uint64_t key, std::function<void(RecordPtr)> callback);

    /*! Requests a list metadata entry by name from the server. Blocking.
     *
     * \param name The name of the requested list.
     * \param callback The function to callback with a non-owning pointer to the FlatList structure, or empty on error.
     */
    void getNamedList(const std::string& name, std::function<void(RecordPtr)> callback);

    /*! Requests the most recent list items from the server. Blocking.
     *
     * \param key The key of the list to retrieve.
     * \param token The list token marker to start iterating from (can be 0 to start at beginning).
     * \param callback The function to callback with list items as a string of "<token> <asset key>\n" pairs.
     */
    void getListItems(uint64_t key, uint64_t token, std::function<void(const std::string&)> callback);

    /*! Uploads a new List to the server. Blocking.
     *
     * \param name The name of the list. If non-unique, will clobber old list name (but not old list).
     * \return The key identifier of the new named list, or 0 on error.
     */
    uint64_t postList(const std::string& name);

    /*! Gets latest status list of all clients on the network from the server.
     *
     * \param callback A function to call with a string of "<address>\t<status string>\n" pairs.
     */
    void getClientStatusPairs(std::function<void(const std::string&)> callback);

    /*! Closes any pending requests and shuts down.
     */
    void shutdown();

private:
    /*! Function to run on m_stateThread, updates state on server every update interval.
     */
    void updateStateLoop();

    const std::string m_serverAddress;
    std::unique_ptr<Pistache::Http::Client> m_client;
    std::random_device m_randomDevice;
    std::uniform_int_distribution<uint64_t> m_distribution;
    std::unique_ptr<State> m_state;
    std::mutex m_quitMutex;
    std::condition_variable m_quitCV;
    bool m_quit;
    std::thread m_stateThread;
};

}  // namespace Confab

#endif  // SRC_CONFAB_HTTP_CLIENT_HPP_

