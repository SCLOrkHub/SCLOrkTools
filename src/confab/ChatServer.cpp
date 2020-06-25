#include "ChatServer.hpp"

#include "spdlog/spdlog.h"

#include <cstring>

namespace Confab {

ChatServer::ChatServer():
    m_tcpThread(nullptr),
    m_tcpServer(nullptr),
    m_userSerial(0) {
}

ChatServer::~ChatServer() {
}

bool ChatServer::create(const std::string& bindPort) {
    m_tcpThread = lo_server_thread_new_with_proto(bindPort.data(), LO_TCP, loError);
    if (!m_tcpThread) {
        spdlog::error("Unable to create OSC listener on TCP port {}", bindPort);
        return false;
    }
    m_tcpServer = lo_server_thread_get_server(m_tcpThread);

    lo_server_thread_add_method(m_tcpThread, nullptr, nullptr, loHandle, this);
    return true;
}

bool ChatServer::run() {
    if (lo_server_thread_start(m_tcpThread) < 0) {
        spdlog::error("Failed to start OSC dispatcher thread.");
        return false;
    }
    return true;
}

void ChatServer::stop() {
    if (lo_server_thread_stop(m_tcpThread) < 0) {
        spdlog::error("Failed to stop OSC dispatcher TCP thread.");
    }
}

void ChatServer::destroy() {
    if (m_tcpThread) {
        lo_server_thread_free(m_tcpThread);
        m_tcpThread = nullptr;
    }
}

// static
void ChatServer::loError(int number, const char* message, const char* path) {
    spdlog::error("lo error number: {}, message: {}, path: {}", number, message, path);
}

// static
int ChatServer::loHandle(const char* path, const char* types, lo_arg** argv, int argc, lo_message message,
        void* userData) {
    ChatServer* server = static_cast<ChatServer*>(userData);
    lo_address address = lo_message_get_source(message);
    server->handleMessage(path, argc, argv, types, address);
    return 0;
}

void ChatServer::handleMessage(const char* path, int argc, lo_arg** argv, const char* types, lo_address address) {
    std::string osc = fmt::format("{}:{} sent OSC: [ {}", lo_address_get_hostname(address), lo_address_get_port(address),
            path);
    for (int i = 0; i < argc; ++i) {
        switch (types[i]) {
        case LO_INT32:
            osc += fmt::format(", {}", *reinterpret_cast<int32_t*>(argv[i]));
            break;

        case LO_FLOAT:
            osc += fmt::format(", {}", *reinterpret_cast<float*>(argv[i]));
            break;

        case LO_STRING:
            osc += fmt::format(", {}", reinterpret_cast<const char*>(argv[i]));
            break;

        case LO_BLOB:
            osc += std::string(", <binary blob>");
            break;

        default:
            osc += fmt::format(", <unrecognized type {}>", types[i]);
            break;
        }
    }
    osc += " ]";
    spdlog::info(osc);

    // TODO: consider perfect hashing with gperf, ala Scintillator.

    if (std::strcmp(path, "/chatConnect") == 0) {
        // This is a request formerly done by SCLOrkWire, which is a request for a unique client ID.
        std::string host = fmt::format("{}", lo_address_get_hostname(address));
        std::string port = fmt::format("{}", lo_address_get_port(address));

        // First blast through existing connections map to determine if we have a duplicate connection from existing
        // client.
        int serial = -1;
        std::pair addressPair = std::make_pair( host, port);
        for (auto item : m_addressMap) {
            if (item.second == addressPair) {
                serial = item.first;
                spdlog::info("logged duplicate connection from userID {} at {}:{}", serial, host, port);
                break;
            }
        }

        if (serial < 0) {
            serial = ++m_userSerial;
            m_addressMap[serial] = addressPair;
            spdlog::info("added new connection userID {} at {}:{}", serial, host, port);
        }

        // want to send back /chatConnected with userID
    } else if (std::strcmp(path, "/chatSignIn") == 0) {
    } else if (std::strcmp(path, "/chatGetAllClients") == 0) {
    } else if (std::strcmp(path, "/chatSendMessage") == 0) {
    } else if (std::strcmp(path, "/chatChangeName") == 0) {
    } else if (std::strcmp(path, "/chatSignOut") == 0) {
    } else {
    }
}

} // namespace Confab

