#include "OscHandler.hpp"

#include <glog/logging.h>
#include <ip/UdpSocket.h>
#include <osc/OscPacketListener.h>
#include <osc/OscReceivedElements.h>

#include <cstring>

namespace Confab {

/*! Handler class for processing incoming OSC messages.
 */
class OscHandler::OscListener : public osc::OscPacketListener {
public:
    /*! Constructor, needs a reference back to the containing OscHandler object.
     *
     * \param handler A non-owning pointer to the containing OscHandler.
     */
    OscListener(OscHandler* handler) :
        osc::OscPacketListener(),
        m_handler(handler) {
    }

    /*! Message handling function, called on each incoming OSC message.
     *
     * \param message Contains the message data.
     * \param endpoint Describes the sender.
     */
    void ProcessMessage(const osc::ReceivedMessage& message, const IpEndpointName& endpoint) override {
        try {
            if (std::strcmp("assetFind", message.AddressPattern()) == 0) {

            } else if (std::strcmp("assetAdd", message.AddressPattern()) == 0) {
                osc::ReceivedMessage::const_iterator arguments = message.ArgumentsBegin();
                std::string type((arguments++)->AsString());
                std::string filePath((arguments++)->AsString());
                if (arguments != message.ArgumentsEnd()) {
                    throw osc::ExcessArgumentException();
                }

                std::thread([=] { m_handler->assetAdd(type, filePath); });
            } else {
                LOG(ERROR) << "OSC unknown message: " << message.AddressPattern();
            }
        } catch (osc::Exception& exception) {
            LOG(ERROR) << "OSC error parsing message: " << message.AddressPattern() << ": " << exception.what();
        }
    }

private:
    OscHandler* m_handler;
};

OscHandler::OscHandler(int listenPort, int sendPort) :
    m_listenPort(listenPort),
    m_sendPort(sendPort),
    m_mainThreadID(std::this_thread::get_id()) {
}

OscHandler::~OscHandler() {
}

void OscHandler::listen() {
    m_listener.reset(new OscListener(this));
    m_socket.reset(new UdpListeningReceiveSocket(IpEndpointName(IpEndpointName::ANY_ADDRESS, m_listenPort),
        m_listener.get()));
    std::thread([=] { runListener(); });
}

void OscHandler::stop() {
    m_socket->Break();
}

void OscHandler::runListener() {
    CHECK(m_mainThreadID != std::this_thread::get_id()) << "Should run on a dedicated thread.";
    m_socket->Run();
}

void OscHandler::assetAdd(std::string type, std::string filePath) {
    CHECK(m_mainThreadID != std::this_thread::get_id()) << "Should run on a dedicated thread.";
}

}  // namespace Confab

