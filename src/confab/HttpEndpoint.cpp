#include "HttpEndpoint.hpp"

#include "Asset.hpp"
#include "AssetDatabase.hpp"
#include "schemas/FlatAsset_generated.h"
#include "schemas/FlatAssetData_generated.h"

#include "glog/logging.h"
#include "pistache/endpoint.h"
#include "pistache/router.h"

namespace Confab {

/*! Handler class for processing incoming HTTP requests. Uses the Pistache Router to connect specific REST-style API
 * queries from clients to private method calls within this class.
 */
class HttpEndpoint::HttpHandler {
public:
    /*! Constructs an empty handler.
     *
     * \param listenPort The TCP port to listen on for HTTP requests.
     * \param numThreads The number of threads to use to listen on the port.
     * \param assetDatabase A pointer to the shared AssetDatabase instance.
     */
    HttpHandler(int listenPort, int numThreads, std::shared_ptr<AssetDatabase> assetDatabase) :
        m_listenPort(listenPort),
        m_numThreads(numThreads),
        m_assetDatabase(assetDatabase) { }

    /*! Setup HTTP URL routes and initialize server.
     */
    void setupRoutes() {
        Pistache::Address address(Pistache::Ipv4::any(), Pistache::Port(m_listenPort));
        m_server.reset(new Pistache::Http::Endpoint(address));
        auto opts = Pistache::Http::Endpoint::options().threads(m_numThreads);
        m_server->init(opts);

        Pistache::Rest::Routes::Get(m_router, "/asset/:key", Pistache::Rest::Routes::bind(
            &HttpEndpoint::HttpHandler::getAsset, this));
        Pistache::Rest::Routes::Post(m_router, "/asset/:key", Pistache::Rest::Routes::bind(
            &HttpEndpoint::HttpHandler::postAsset, this));

        Pistache::Rest::Routes::Get(m_router, "/asset_data/:key/:chunk", Pistache::Rest::Routes::bind(
            &HttpEndpoint::HttpHandler::getAssetData, this));
        Pistache::Rest::Routes::Post(m_router, "/asset_data/:key/:chunk", Pistache::Rest::Routes::bind(
            &HttpEndpoint::HttpHandler::postAssetData, this));
    }

    /*! Starts a thread that will listen on the provided TCP port and process incoming requests for storage and
     * retrieval of assets.
     */
    void startServerThread() {
        m_server->setHandler(m_router.handler());
        m_server->serveThreaded();
    }

    /*! Starts the server on this thread, blocking the thread. Call one of this method or startServerThread().
     */
    void startServer() {
        m_server->setHandler(m_router.handler());
        m_server->serve();
    }

    /*! Stops serving threads, closes ports.
     */
    void shutdown() {
        m_server->shutdown();
    }

private:
    void getAsset(const Pistache::Rest::Request& request, Pistache::Http::ResponseWriter response) {
        auto keyString = request.param(":key").as<std::string>();
        LOG(INFO) << "processing HTTP GET request for /asset/" << keyString;
        uint64_t key = Asset::stringToKey(keyString);
        RecordPtr record = m_assetDatabase->findAsset(key);
        if (record->empty()) {
            LOG(ERROR) << "HTTP get request for Asset " << keyString << " not found, returning 404.";
            response.headers().add<Pistache::Http::Header::Server>("confab");
            response.send(Pistache::Http::Code::Not_Found);
        } else {
            LOG(INFO) << "HTTP get request returning Asset data for " << keyString;
            response.headers()
                .add<Pistache::Http::Header::Server>("confab")
                .add<Pistache::Http::Header::ContentType>(MIME(Application, OctetStream));
            auto stream = response.stream(Pistache::Http::Code::Ok);
            stream.write(reinterpret_cast<const char*>(record->data().data()), record->data().size());
            stream.ends();
        }
    }

    void postAsset(const Pistache::Rest::Request& request, Pistache::Http::ResponseWriter response) {
        auto keyString = request.param(":key").as<std::string>();
        uint64_t key = Asset::stringToKey(keyString);
        SizedPointer postedData(reinterpret_cast<const uint8_t*>(request.body().c_str()), request.body().size());
        LOG(INFO) << "processing HTTP POST request for /asset/" << keyString << ", " << postedData.size() << " bytes.";

        // Sanity-check the provided serialized FlatAsset data.
        auto verifier = flatbuffers::Verifier(postedData.data(), postedData.size());
        bool status = Data::VerifyFlatAssetBuffer(verifier);
        if (status) {
            LOG(INFO) << "verified FlatAsset " << keyString;
            status = m_assetDatabase->storeAsset(key, postedData);
        } else {
            LOG(ERROR) << "posted data did not verify for asset " << keyString;
        }

        response.headers().add<Pistache::Http::Header::Server>("confab");
        if (status) {
            LOG(INFO) << "sending OK response after storing asset " << keyString;
            response.send(Pistache::Http::Code::Ok);
        } else {
            LOG(ERROR) << "sending error response after failure to store asset " << keyString;
            response.send(Pistache::Http::Code::Internal_Server_Error);
        }
    }

    void getAssetData(const Pistache::Rest::Request& request, Pistache::Http::ResponseWriter response) {
        auto keyString = request.param(":key").as<std::string>();
        auto chunk = request.param(":chunk").as<uint64_t>();
        LOG(INFO) << "processing HTTP GET request for /asset_data/" << keyString << "/" << chunk;
        uint64_t key = Asset::stringToKey(keyString);
        RecordPtr assetData = m_assetDatabase->loadAssetDataChunk(key, chunk);
        if (assetData->empty()) {
            LOG(ERROR) << "HTTP get request for Asset Data " << keyString << " chunk " << chunk
                << " not found, returning 404.";
            response.headers().add<Pistache::Http::Header::Server>("confab");
            response.send(Pistache::Http::Code::Not_Found);
        } else {
            LOG(INFO) << "HTTP get request for Asset Data " << keyString << " chunk " << chunk
                << " returning Asset Data.";
            response.headers()
                    .add<Pistache::Http::Header::Server>("confab")
                    .add<Pistache::Http::Header::ContentType>(MIME(Application, OctetStream));
            auto stream = response.stream(Pistache::Http::Code::Ok);
            stream.write(reinterpret_cast<const char*>(assetData->data().data()), assetData->data().size());
            stream.ends();
        };
    }

    void postAssetData(const Pistache::Rest::Request& request, Pistache::Http::ResponseWriter response) {
        auto keyString = request.param(":key").as<std::string>();
        auto chunk = request.param(":chunk").as<uint64_t>();
        LOG(INFO) << "processing HTTP POST request for /asset_data/" << keyString << "/" << chunk;
        uint64_t key = Asset::stringToKey(keyString);
        SizedPointer postedData(reinterpret_cast<const uint8_t*>(request.body().c_str()), request.body().size());
        bool status = m_assetDatabase->storeAssetDataChunk(key, chunk, postedData);
        response.headers().add<Pistache::Http::Header::Server>("confab");
        if (status) {
            LOG(INFO) << "sending OK response after storing asset " << keyString << " data chunk " << chunk;
            response.send(Pistache::Http::Code::Ok);
        } else {
            LOG(ERROR) << "sending error response after failure to store asset " << keyString << " data chunk "
                << chunk;
            response.send(Pistache::Http::Code::Internal_Server_Error);
        }
    }

    int m_listenPort;
    int m_numThreads;
    std::shared_ptr<AssetDatabase> m_assetDatabase;
    std::shared_ptr<Pistache::Http::Endpoint> m_server;
    Pistache::Rest::Router m_router;
};

HttpEndpoint::HttpEndpoint(int listenPort, int numThreads, std::shared_ptr<AssetDatabase> assetDatabase) :
    m_handler(new HttpHandler(listenPort, numThreads, assetDatabase)) {
}

HttpEndpoint::~HttpEndpoint() {
}

void HttpEndpoint::startServerThread() {
    m_handler->setupRoutes();
    m_handler->startServerThread();
}

void HttpEndpoint::startServer() {
    m_handler->setupRoutes();
    m_handler->startServer();
}

void HttpEndpoint::shutdown() {
    m_handler->shutdown();
}

}  // namespace Confab

