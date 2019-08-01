#ifndef SRC_CONFAB_OSC_HANDLER_HPP_
#define SRC_CONFAB_OSC_HANDLER_HPP_

#include "Asset.hpp"

#include <memory>
#include <string>
#include <thread>

// Forward declarations from OscPack library.
class UdpListeningReceiveSocket;
class UdpTransmitSocket;

namespace Confab {

class AssetDatabase;
class CacheManager;
class HttpClient;

/*! Class for listening and responding to OSC messages from a single SuperCollider client.
 */
class OscHandler {
public:
    /*! Constructs an OSCHandler configured to communicate on the provided UDP ports.
     *
     * \param listenPort The localhost UDP port to listen to.
     * \param sendPort The localhost UDP port to send responding OSC messages back to SuperCollider on.
     * \param assetDatabase The shared reference to the AssetDatabase instance, for caching smaller Assets locally.
     * \param httpClient A shared reference to the HttpClient instance this OscHandler should use for Asset queries.
     * \param cacheManager A shared reference to the CacheManager instnace this OscHandler should use for Cache queries.
     */
    OscHandler(int listenPort, int sendPort, std::shared_ptr<AssetDatabase> assetDatabase,
        std::shared_ptr<HttpClient> httpClient, std::shared_ptr<CacheManager> cacheManager);

    /*! Destructs an OSCHandler. Declared here to let us use std::unique_ptr with forward-declared classes.
     */
    ~OscHandler();

    /*! Launch a thread to run the OSC message processing loop. Call shutdown() to stop the processing loop.
     */
    void run();

    /*! Stops the processing loop and closes the open ports.
     */
    void shutdown();

private:
    class OscListener;

    /*! Searches for an asset with provided id. Should run as a task.
     */
    void findAsset(uint64_t assetId);

    /*! Searches for an asset with provided name. Should run as a task.
     */
    void findNamedAsset(std::string name);

    /*! Downloads an asset file to cache, provides path back to caller. Should run as a task.
     */
    void loadAsset(uint64_t key);

    /*! Processes an asset addition request for a given file path. Should run as a task.
     */
    void addAssetFile(Asset::Type type, int serialNumber, std::string name, uint64_t author, uint64_t deprecates,
        std::string filePath);

    /*! Processes an asset addition request for a short string. Should run as a task.
     */
    void addAssetString(Asset::Type type, int serialNumber, std::string name, uint64_t author, uint64_t deprecates,
        std::string assetString);

    /*! Utility method, sends an Asset back to SuperCollider via OSC.
     */
    void sendAsset(uint64_t requstedKey, RecordPtr record);

    int m_listenPort;
    int m_sendPort;
    std::shared_ptr<AssetDatabase> m_assetDatabase;
    std::shared_ptr<HttpClient> m_httpClient;
    std::shared_ptr<CacheManager> m_cacheManager;

    std::unique_ptr<UdpTransmitSocket> m_transmitSocket;
    std::unique_ptr<OscListener> m_listener;
    std::unique_ptr<UdpListeningReceiveSocket> m_listenSocket;
};

}  // namespace Confab

#endif  // SRC_CONFAB_OSC_HANDLER_HPP_

