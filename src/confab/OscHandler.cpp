#include "OscHandler.hpp"

#include "AssetManager.hpp"

#include <glog/logging.h>
#include <ip/UdpSocket.h>
#include <osc/OscOutboundPacketStream.h>
#include <osc/OscPacketListener.h>
#include <osc/OscReceivedElements.h>

#include <cstring>
#include <future>

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
            if (std::strcmp("/assetFind", message.AddressPattern()) == 0) {

            } else if (std::strcmp("/assetAddFile", message.AddressPattern()) == 0) {
                osc::ReceivedMessage::const_iterator arguments = message.ArgumentsBegin();
                std::string typeString((arguments++)->AsString());
                int serialNumber = (arguments++)->AsInt32();
                std::string filePath((arguments++)->AsString());
                if (arguments != message.ArgumentsEnd()) {
                    throw osc::ExcessArgumentException();
                }

                LOG(INFO) << "processing [/assetAddFile " << typeString << ", " << serialNumber << ", " << filePath
                    << "]";

                Asset::Type type = Asset::typeStringToEnum(typeString);
                if (type == Asset::kInvalid) {
                    LOG(ERROR) << "/assetAddFile got bad type string: " << typeString;
                } else {
                    std::async(std::launch::async, [this, type, serialNumber, filePath] {
                        m_handler->addAssetFile(type, serialNumber, filePath);
                    });
                }
            } else if (std::strcmp("/assetAddString", message.AddressPattern()) == 0) {
                osc::ReceivedMessage::const_iterator arguments = message.ArgumentsBegin();
                std::string typeString((arguments++)->AsString());
                int serialNumber = (arguments++)->AsInt32();
                std::string assetString((arguments++)->AsString());
                if (arguments != message.ArgumentsEnd()) {
                    throw osc::ExcessArgumentException();
                }

                LOG(INFO) << "processing [/assetAddString " << typeString << ", " << serialNumber << ", " << assetString
                    << "]";

                Asset::Type type = Asset::typeStringToEnum(typeString);
                if (type == Asset::kInvalid) {
                    LOG(ERROR) << "/assetAddString got bad type string: " << typeString;
                } else {
                    std::async(std::launch::async, [this, type, serialNumber, assetString] {
                        m_handler->addAssetString(type, serialNumber, assetString);
                    });
                }
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

OscHandler::OscHandler(int listenPort, int sendPort, std::shared_ptr<AssetManager> assetManager) :
    m_listenPort(listenPort),
    m_sendPort(sendPort),
    m_assetManager(assetManager),
    m_mainThreadID(std::this_thread::get_id()) {
}

OscHandler::~OscHandler() {
}

void OscHandler::listenUntilSigInt() {
    m_transmitSocket.reset(new UdpTransmitSocket(IpEndpointName("127.0.0.1", m_sendPort)));

    m_listener.reset(new OscListener(this));
    m_listenSocket.reset(new UdpListeningReceiveSocket(IpEndpointName(IpEndpointName::ANY_ADDRESS, m_listenPort),
        m_listener.get()));
    m_listenSocket->RunUntilSigInt();
}

void OscHandler::addAssetFile(Asset::Type type, int serialNumber, std::string filePath) {
    CHECK(m_mainThreadID != std::this_thread::get_id()) << "Should run on a dedicated thread.";

    m_assetManager->addAssetFile(type, filePath, [this, serialNumber](uint64_t assetId) {
        char buffer[1024];
        osc::OutboundPacketStream p(buffer, 1024);
        p << osc::BeginMessage("/assetAdded") << serialNumber << AssetManager::keyToString(assetId).c_str()
            << osc::EndMessage;
        m_transmitSocket->Send(p.Data(), p.Size());
    });
}

void OscHandler::addAssetString(Asset::Type type, int serialNumber, std::string assetString) {
    CHECK(m_mainThreadID != std::this_thread::get_id()) << "Should run on a dedicated thread.";

    m_assetManager->addAssetString(type, assetString, [this, serialNumber](uint64_t assetId) {
        char buffer[1024];
        osc::OutboundPacketStream p(buffer, 1024);
        p << osc::BeginMessage("/assetAdded") << serialNumber << AssetManager::keyToString(assetId).c_str()
            << osc::EndMessage;
        m_transmitSocket->Send(p.Data(), p.Size());
    });
}

}  // namespace Confab

