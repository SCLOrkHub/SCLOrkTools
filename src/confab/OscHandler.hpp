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

class AssetManager;

/*! Class for listening and responding to OSC messages from a single SuperCollider client.
 */
class OscHandler {
public:
    /*! Constructs an OSCHandler configured to communicate on the provided UDP ports.
     *
     * \param listenPort The localhost UDP port to listen to.
     * \param sendPort The localhost UDP port to send responding OSC messages back to SuperCollider on.
     * \param assetManager A shared reference to the Asset Manager instance this OscHandler should use for Assets.
     */
    OscHandler(int listenPort, int sendPort, std::shared_ptr<Confab::AssetManager> assetManager);

    /*! Destructs an OSCHandler. Declared here to let us use std::unique_ptr with forward-declared classes.
     */
    ~OscHandler();

    /*! Process incoming OSC messages until terminated with a SIGINT.
     *
     * This function won't return until the program receives a SIGINT.
     */
    void listenUntilSigInt();

private:
    class OscListener;

    /*! Searches for an asset with provided id. Should run as a task.
     */
    void findAsset(uint64_t assetId);

    /*! Processes an asset addition request for a given file path. Should run as a task.
     */
    void addAssetFile(Asset::Type type, int serialNumber, std::string filePath);

    /*! Processes an asset addition request for a short string. Should run as a task.
     */
    void addAssetString(Asset::Type type, int serialNumber, std::string assetString);

    int m_listenPort;
    int m_sendPort;

    std::thread::id m_mainThreadID;
    std::unique_ptr<UdpTransmitSocket> m_transmitSocket;
    std::unique_ptr<OscListener> m_listener;
    std::unique_ptr<UdpListeningReceiveSocket> m_listenSocket;
};

}  // namespace Confab

#endif  // SRC_CONFAB_OSC_HANDLER_HPP_

