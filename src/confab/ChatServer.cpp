#include "ChatServer.hpp"

#include "ChatCommands.hpp"

#include "spdlog/spdlog.h"

#include <cstring>

namespace Confab {

ChatServer::ChatServer():
    m_tcpThread(nullptr),
    m_tcpServer(nullptr),
    m_userSerial(0),
    m_messageSerial(0) {
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
    server->handleMessage(path, argc, argv, types, address, message);
    return 0;
}

void ChatServer::handleMessage(const char* path, int argc, lo_arg** argv, const char* types, lo_address address,
        lo_message message) {
    std::string osc = fmt::format("{}:{} - [ {}", lo_address_get_hostname(address), lo_address_get_port(address), path);
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

    ChatCommands command = getCommandNamed(std::string(path));
    switch (command) {
    // Input: [ /chatConnect ], server responds with [ /chatConnected userID ]
    case kConnect: {
        int serial = ++m_userSerial;
        spdlog::info("added new connection userID {} at {}:{}", serial, lo_address_get_hostname(address),
                lo_address_get_port(address));
        if (lo_send_from(address, m_tcpServer, LO_TT_IMMEDIATE, "/chatConnected", "i", serial) < 0) {
            spdlog::error("failed to send /chatConnected to {}:{}", lo_address_get_hostname(address),
                    lo_address_get_port(address));
        }
    } break;

    // Input: [ /chatSignIn userID name ], response [ /chatSignInComplete ],
    // queues [ /chatChangeClient add userID name ]
    case kSignIn: {
        if (argc != 2 || types[0] != LO_INT32 || types[1] != LO_STRING) {
            spdlog::error("/chatSignIn argument absent or wrong type.");
            return;
        }
        int userID = *reinterpret_cast<int32_t*>(argv[0]);
        std::string name(reinterpret_cast<const char*>(argv[1]));
        m_nameMap[mapEntry->second] = name;

        // Send back a /chatSignInComplete message to acknowledge receipt.
        if (lo_send_from(address, m_tcpServer, LO_TT_IMMEDIATE, "/chatSignInComplete", "") < 0) {
            spdlog::error("failed to send /chatSignInComplete to {}:{}", lo_address_get_hostname(address),
                    lo_address_get_port(address));
        }
        lo_message addClient = lo_message_new();
        lo_message_add_string(addClient, "add");
        lo_message_add_int32(addClient, userID);
        lo_message_add_string(addClient, name.data());
        queueMessage("/chatChangeClient", addClient);
    } break;

    // Input: [ /chatGetAllClients ], response [ /chatSetAllClients (pairs of userID, name) ]
    case kGetAllClients: {
        lo_message clientNames = lo_message_new();
        for (auto nameEntry : m_nameMap) {
            lo_message_add_int32(clientNames, nameEntry.first);
            lo_message_add_string(clientNames, nameEntry.second.data());
        }
        lo_send_message_from(address, m_tcpServer, "/chatSetAllClients", clientNames);
        lo_message_free(clientNames);
    } break;

    // Input: [ /chatSendMessage userID <message contents> ], queues [ /chatRecieve userID <message contents> ]
    case kSendMessage: {
        lo_message chatMessage = lo_message_clone(message);
        queueMessage("/chatReceive", chatMessage);
    } break;

    // Input: [ /chatChangeName userID newName ], queues [ /chatChangeClient rename userID newName ]
    case kChangeName: {
        if (argc != 2 || types[0] != LO_INT32 || types[1] != LO_STRING) {
            spdlog::error("/chatSignIn argument absent or wrong type.");
            return;
        }

        int userID = *reinterpret_cast<int32_t*>(argv[0]);
        std::string name(reinterpret_cast<const char*>(argv[1]));
        m_nameMap[mapEntry->second] = name;

        lo_message rename = lo_message_new();
        lo_message_add_string(rename, "rename");
        lo_message_add_int32(rename, userID);
        lo_message_add_string(rename, newName.data());
        queueMessage("/chatChangeClient", rename);
    } break;

    case kSignOut: {
    } break;
    case kDisconnect: {
    } break;
    case kNotFound: {
    } break;
    }

    } else if (std::strcmp(path, "/chatChangeName") == 0) {
        if (argc != 1 || types[0] != LO_STRING) {
            spdlog::error("/chatChangeName name argument absent or wrong type.");
            return;
        }
        std::string newName(reinterpret_cast<const char*>(argv[0]));
        auto mapEntry = m_addressMap.find(token);
        if (mapEntry != m_addressMap.end()) {
            m_nameMap[mapEntry->second] = newName;
        } else {
            spdlog::error("/chatChangeName called for unknown client at {}:{}", lo_address_get_hostname(address),
                    lo_address_get_port(address));
            return;
        }

        lo_message rename = lo_message_new();
        lo_message_add_string(rename, "rename");
        lo_message_add_int32(rename, mapEntry->second);
        lo_message_add_string(rename, newName.data());
        queueMessage("/chatChangeClient", rename);
    } else if (std::strcmp(path, "/chatSignOut") == 0) {
    } else {

    }
}

uint64_t ChatServer::makeToken(lo_address address) {
    const char* ipv4 = lo_address_get_hostname(address);
    uint64_t token = 0;
    // Convert IPv4 quad to binary first.
    for (auto i = 0; i < 4; ++i) {
        char* nextPart = nullptr;
        uint32_t part = std::strtoul(ipv4, &nextPart, 10);
        if (nextPart == ipv4) {
            spdlog::error("Failed to convert part {} of ipv4 quad {}", i, ipv4);
            return 0;
        }
        token = (token << 8) | static_cast<uint64_t>(part);
        // Skip over the dot.
        ipv4 = nextPart + 1;
    }

    const char* portString = lo_address_get_port(address);
    uint32_t port = std::strtoul(portString, nullptr, 10);
    if (port == 0) {
        spdlog::error("Failed to convert port {} to a number", portString);
        return 0;
    }
    token = (token << 16) | static_cast<uint64_t>(port);
    return token;
}

void ChatServer::queueMessage(const char* path, lo_message message) {
    int index = m_messageSerial % kMessageArraySize;
    if (m_messageSerial > kMessageArraySize) {
        lo_message_free(m_messages[index]);
    }
    m_paths[index] = path;
    m_messages[index] = message;
    ++m_messageSerial;
}

} // namespace Confab

