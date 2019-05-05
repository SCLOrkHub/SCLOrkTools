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

    /*! Starts a new thread for processing OSC messages on the ports supplied in the ctor.
     */
    void listen();

    /*! Stops the listening thread and closes the open OSC port. Any pending processing threads will run to completion.
     */
    void stop();

private:
    class OscListener;

    /*! Run the socket's listener loop, for responding to incoming OSC messages. Function should run on its own thread.
     *
     * Function will not return until Stop() is called and the runloop is interrupted.
     */
    void runListener();

    /*! Processes an asset addition request. Function should run on its own thread.
     */
    void assetAdd(std::string type, std::string filePath);

    int m_listenPort;
    int m_sendPort;
    std::thread::id m_mainThreadID;
    std::unique_ptr<OscListener> m_listener;
    std::unique_ptr<UdpListeningReceiveSocket> m_socket;
};

}  // namespace Confab

#endif  // SRC_CONFAB_OSC_HANDLER_HPP_

