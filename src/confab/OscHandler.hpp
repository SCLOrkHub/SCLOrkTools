#ifndef SRC_CONFAB_OSC_HANDLER_HPP_
#define SRC_CONFAB_OSC_HANDLER_HPP_

#include <memory>
#include <string>
#include <thread>

// Forward declaration from OscPack library.
class UdpListeningReceiveSocket;

namespace Confab {

/*! Class for listening and responding to OSC messages from a single SuperCollider client.
 */
class OscHandler {
public:
    /*! Constructs an OSCHandler configured to communicate on the provided UDP ports.
     *
     * \param listenPort The localhost UDP port to listen to.
     * \param sendPort The localhost UDP port to send responding OSC messages back to SuperCollider on.
     */
    OscHandler(int listenPort, int sendPort);

    /*! Destructs an OSCHandler.
     */
    ~OscHandler();

    /*! Process incoming OSC messages until terminated with a SIGINT.
     *
     * This function won't return until the program receives a SIGINT.
     */
    void listenUntilSigInt();

private:
    class OscListener;

    /*! Processes an asset addition request. Should run as a task.
     */
    void assetAddFile(std::string type, int serialNumber, std::string filePath);

    int m_listenPort;
    int m_sendPort;

    std::thread::id m_mainThreadID;
    std::unique_ptr<OscListener> m_listener;
    std::unique_ptr<UdpListeningReceiveSocket> m_socket;
};

}  // namespace Confab

#endif  // SRC_CONFAB_OSC_HANDLER_HPP_

