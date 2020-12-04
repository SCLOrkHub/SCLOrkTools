#include "ChatServer.hpp"

#include "ChatCommands.hpp"

#include "spdlog/spdlog.h"

#include <cstring>

namespace Confab {

ChatServer::ChatServer(int32_t timeout):
    m_tcpThread(nullptr),
    m_tcpServer(nullptr),
    m_userSerial(0),
    m_messageSerial(0),
    m_lastUpdateTime(std::chrono::system_clock::now()),
    m_timeout(std::chrono::seconds(timeout)) {
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

    spdlog::info("ChatServer listening on TCP port {}", bindPort);
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
    auto now = std::chrono::system_clock::now();
    if (now - m_lastUpdateTime > std::chrono::seconds(60)) {
        spdlog::info("ssh keepalive, {} users currently online", m_nameMap.size());
        m_lastUpdateTime = now;
    }

    ChatCommands command = getCommandNamed(std::string(path));
    switch (command) {
    // Input: [ /chatSignIn name ], response [ /chatSignInComplete userID ],
    // queues [ /chatChangeClient serial add userID name ]
    case kSignIn: {
        if (argc != 1 || types[0] != LO_STRING) {
            spdlog::error("/chatSignIn argument absent or wrong type.");
            return;
        }
        std::string name(reinterpret_cast<const char*>(argv[0]));
        int userID = ++m_userSerial;
        spdlog::info("added new connection name {} userID {} from {}:{}", name, userID,
                lo_address_get_hostname(address), lo_address_get_port(address));

        m_nameMap[userID] = name;

        // Send back a /chatSignInComplete message to acknowledge receipt.
        if (lo_send_from(address, m_tcpServer, LO_TT_IMMEDIATE, "/chatSignInComplete", "i", userID) < 0) {
            spdlog::error("failed to send /chatSignInComplete to {}:{}", lo_address_get_hostname(address),
                    lo_address_get_port(address));
        }
        lo_message addClient = lo_message_new();
        lo_message_add_int32(addClient, m_messageSerial);
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

    // Input: [ /chatGetMessages userID messageID ], responds with all messages with id > messageID. Can also queue
    // timeout messages from stale clients.
    case kGetMessages: {
        if (argc != 2 || types[0] != LO_INT32 || types[1] != LO_INT32) {
            spdlog::error("/chatGetMessages arguments absent or wrong type.");
            return;
        }
        int userID = *reinterpret_cast<int32_t*>(argv[0]);
        int messageID = *reinterpret_cast<int32_t*>(argv[1]);
        // m_messageSerial points at the first unoccupied message number. We store the last kMessageArraySize elements,
        // so if this is a request for older messages they are lost.
        if (m_messageSerial - messageID > kMessageArraySize) {
            spdlog::info("userID {} requested older messages, truncating request");
            messageID = std::max(m_messageSerial - kMessageArraySize, 0);
        }

        // Start on first message after messageID.
        for (auto i = messageID + 1; i < m_messageSerial; ++i) {
            int index = i % kMessageArraySize;
            lo_send_message_from(address, m_tcpServer, m_paths[index], m_messages[index]);
        }

        // Update ping time from this client.
        auto now = std::chrono::system_clock::now();
        m_clientPings[userID] = now;

        // Now iterate through client list and timeout any stale clients.
        auto cutTime = now - m_timeout;
        for (auto i = m_clientPings.begin(); i != m_clientPings.end(); /* */) {
            if (i->second <= cutTime) {
                // Could be a stale client, so make sure that the client is still in the name map before timing them
                // out.
                auto name = m_nameMap.find(i->first);
                if (name != m_nameMap.end()) {
                    spdlog::warn("user {} timed out.", name->second);
                    lo_message timeout = lo_message_new();
                    lo_message_add_int32(timeout, m_messageSerial);
                    lo_message_add_string(timeout, "timeout");
                    lo_message_add_int32(timeout, i->first);
                    queueMessage("/chatChangeClient", timeout);
                    m_nameMap.erase(name);
                }

                i = m_clientPings.erase(i);
            } else {
                ++i;
            }
        }

    } break;

    // Input: [ /chatSendMessage userID <message contents> ], queues [ /chatRecieve serial userID <message contents> ]
    case kSendMessage: {
        // lo_message chatMessage = lo_message_clone(message);
        lo_message chatMessage = lo_message_new();
        lo_message_add_int32(chatMessage, m_messageSerial);
        for (auto i = 0; i < argc; ++i) {
            switch (types[i]) {
                case LO_INT32:
                    lo_message_add_int32(chatMessage, *reinterpret_cast<int32_t*>(argv[i]));
                    break;

                case LO_STRING:
                    lo_message_add_string(chatMessage, reinterpret_cast<const char*>(argv[i]));
                    break;

                default:
                    spdlog::error("need to add support for message type: {}", types[i]);
                    return;
            }
        }
        queueMessage("/chatReceive", chatMessage);
    } break;

    // Input: [ /chatChangeName userID newName ], queues [ /chatChangeClient serial rename userID newName ]
    case kChangeName: {
        if (argc != 2 || types[0] != LO_INT32 || types[1] != LO_STRING) {
            spdlog::error("/chatSignIn argument absent or wrong type.");
            return;
        }

        int userID = *reinterpret_cast<int32_t*>(argv[0]);
        std::string name(reinterpret_cast<const char*>(argv[1]));
        m_nameMap[userID] = name;

        lo_message rename = lo_message_new();
        lo_message_add_int32(rename, m_messageSerial);
        lo_message_add_string(rename, "rename");
        lo_message_add_int32(rename, userID);
        lo_message_add_string(rename, name.data());
        queueMessage("/chatChangeClient", rename);
    } break;

    // Input: [ /chatSignOut userID ] queues [ /chatChangeClient serial remove userID ]
    case kSignOut: {
        if (argc != 1 || types[0] != LO_INT32) {
            spdlog::error("/chatSignOut argument absent or wrong type.");
            return;
        }

        int userID = *reinterpret_cast<int32_t*>(argv[0]);
        m_nameMap.erase(userID);

        spdlog::info("received sign out command from userID {} at {}:{}", userID, lo_address_get_hostname(address),
                lo_address_get_port(address));

        lo_message remove = lo_message_new();
        lo_message_add_int32(remove, m_messageSerial);
        lo_message_add_string(remove, "remove");
        lo_message_add_int32(remove, userID);
        queueMessage("/chatChangeClient", remove);
    } break;

    case kNotFound: {
        spdlog::error("received unsupported OSC command {} from {}:{}", path, lo_address_get_hostname(address),
                lo_address_get_port(address));
    } break;
    }
}

void ChatServer::queueMessage(const char* path, lo_message message) {
    lo_message_add_int32(message, m_messageSerial);
    int index = m_messageSerial % kMessageArraySize;
    if (m_messageSerial > kMessageArraySize) {
        lo_message_free(m_messages[index]);
    }
    m_paths[index] = path;
    m_messages[index] = message;
    ++m_messageSerial;
}

} // namespace Confab

