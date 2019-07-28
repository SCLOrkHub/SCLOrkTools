#include "OscHandler.hpp"

#include "Asset.hpp"
#include "Constants.hpp"
#include "HttpClient.hpp"

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
                osc::ReceivedMessage::const_iterator arguments = message.ArgumentsBegin();
                std::string assetIdString((arguments++)->AsString());
                if (arguments != message.ArgumentsEnd()) {
                    throw osc::ExcessArgumentException();
                }

                LOG(INFO) << "processing [/assetFind " << assetIdString << "]";

                uint64_t assetKey = Asset::stringToKey(assetIdString);
                if (assetKey == 0) {
                    LOG(ERROR) << "/assetFind got invalid key value: " << assetIdString;
                } else {
                    std::async(std::launch::async, [this, assetKey] {
                        m_handler->findAsset(assetKey);
                    });
                }
            } else if (std::strcmp("/assetAddFile", message.AddressPattern()) == 0) {
                osc::ReceivedMessage::const_iterator arguments = message.ArgumentsBegin();
                std::string typeString((arguments++)->AsString());
                int serialNumber = (arguments++)->AsInt32();
                std::string name((arguments++)->AsString());
                std::string authorString((arguments++)->AsString());
                uint64_t author = 0;
                if (authorString.size() > 0) {
                    author = Asset::stringToKey(authorString);
                }
                std::string deprecatesString((arguments++)->AsString());
                uint64_t deprecates = 0;
                if (deprecatesString.size() > 0) {
                    uint64_t deprecats = Asset::stringToKey(deprecatesString);
                }
                std::string filePath((arguments++)->AsString());
                if (arguments != message.ArgumentsEnd()) {
                    throw osc::ExcessArgumentException();
                }

                LOG(INFO) << "processing [/assetAddFile " << typeString << ", " << serialNumber << ", " << name << ", "
                    << authorString << ", " << deprecatesString << ", " << filePath << "]";

                Asset::Type type = Asset::typeStringToEnum(typeString);
                if (type == Asset::kInvalid) {
                    LOG(ERROR) << "/assetAddFile got bad type string: " << typeString;
                } else {
                    std::async(std::launch::async, [this, type, serialNumber, name, author, deprecates, filePath] {
                        m_handler->addAssetFile(type, serialNumber, name, author, deprecates, filePath);
                    });
                }
            } else if (std::strcmp("/assetAddString", message.AddressPattern()) == 0) {
                osc::ReceivedMessage::const_iterator arguments = message.ArgumentsBegin();
                std::string typeString((arguments++)->AsString());
                int serialNumber = (arguments++)->AsInt32();
                std::string name((arguments++)->AsString());
                std::string authorString((arguments++)->AsString());
                uint64_t author = 0;
                if (authorString.size() > 0) {
                    author = Asset::stringToKey(authorString);
                }
                std::string deprecatesString((arguments++)->AsString());
                uint64_t deprecates = 0;
                if (deprecatesString.size() > 0) {
                    deprecates = Asset::stringToKey(deprecatesString);
                }
                std::string assetString((arguments++)->AsString());
                if (arguments != message.ArgumentsEnd()) {
                    throw osc::ExcessArgumentException();
                }

                LOG(INFO) << "processing [/assetAddString " << typeString << ", " << serialNumber << ", " << name
                    << ", " << authorString << ", " << deprecatesString << ", " << assetString << "]";

                Asset::Type type = Asset::typeStringToEnum(typeString);
                if (type == Asset::kInvalid) {
                    LOG(ERROR) << "/assetAddString got bad type string: " << typeString;
                } else {
                    std::async(std::launch::async, [this, type, serialNumber, name, author, deprecates, assetString] {
                        m_handler->addAssetString(type, serialNumber, name, author, deprecates, assetString);
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

OscHandler::OscHandler(int listenPort, int sendPort, std::shared_ptr<HttpClient> httpClient,
    std::shared_ptr<CacheManager> cacheManager) :
    m_listenPort(listenPort),
    m_sendPort(sendPort),
    m_httpClient(httpClient),
    m_cache(cacheManager) {
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

void OscHandler::findAsset(uint64_t assetId) {
    m_httpClient->getAsset(assetId, [this, assetId](uint64_t loadedKey, RecordPtr record) {
        char buffer[kDataChunkSize];
        osc::OutboundPacketStream p(buffer, kDataChunkSize);
        if (record->empty()) {
            LOG(ERROR) << "failed to retrieve Asset " << Asset::keyToString(assetId) << ".";
            p << osc::BeginMessage("/assetError") << Asset::keyToString(assetId).c_str()
                << "Failed to find asset associated with key." << osc::EndMessage;
            m_transmitSocket->Send(p.Data(), p.Size());
        } else {
            const Data::FlatAsset* asset = Data::GetFlatAsset(record->data().data());
            p << osc::BeginMessage("/assetFound");
            p << Asset::keyToString(assetId).c_str();
            p << Asset::keyToString(loadedKey).c_str();
            p << "snippet";  // TODO: asset type to string.
            p << asset->name();
            p << asset->fileExtension();
            p << Asset::keyToString(asset->author()).c_str();
            p << Asset::keyToString(asset->deprecatedBy()).c_str();
            p << Asset::keyToString(asset->deprecates()).c_str();
            if (asset->inlineData()) {
                osc::Blob blob(asset->inlineData()->data(), asset->inlineData()->size());
                p << blob;
            } else {
                osc::Blob blob(nullptr, 0);
                p << blob;
            }
            p << Asset::keyToString(asset->expiresOn()).c_str();
            p << Asset::keyToString(asset->salt()).c_str();
            p << osc::EndMessage;
            m_transmitSocket->Send(p.Data(), p.Size());
        }
    });
}

void OscHandler::addAssetFile(Asset::Type type, int serialNumber, std::string name, uint64_t author,
    uint64_t deprecates, std::string filePath) {
    uint64_t key = m_httpClient->postFileAsset(type, name, author, deprecates, filePath);
    char buffer[1024];
    osc::OutboundPacketStream p(buffer, 1024);
    p << osc::BeginMessage("/assetAdded") << serialNumber << Asset:keyToString(key).c_str()
        << osc::EndMessage;
    m_transmitSocket->Send(p.Data(), p.Size());
}

void OscHandler::addAssetString(Asset::Type type, int serialNumber, std::string name, uint64_t author,
    uint64_t deprecates, std::string assetString) {
    uint64_t key = m_httpClient->postInlineAsset(type, name, author, deprecates, assetString.size(),
        assetString.c_str());

    // Regardless of success or failure of Asset add we return the key and serial number.
    char buffer[1024];
    osc::OutboundPacketStream p(buffer, 1024);
    p << osc::BeginMessage("/assetAdded") << serialNumber << Asset:keyToString(assetId).c_str()
        << osc::EndMessage;
    m_transmitSocket->Send(p.Data(), p.Size());
}



}  // namespace Confab

