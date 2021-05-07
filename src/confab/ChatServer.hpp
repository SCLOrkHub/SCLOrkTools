#ifndef SRC_CONFAB_CHAT_SERVER_HPP_
#define SRC_CONFAB_CHAT_SERVER_HPP_

#include "lo/lo.h"

#include <array>
#include <chrono>
#include <string>
#include <unordered_map>

namespace Confab {

/*! Implementation of the sclang-based SCLOrkServer using TCP and liblo instead.
 */
class ChatServer {
public:
    ChatServer(int32_t timeout, int32_t maxMessagesPerRequest);
    ~ChatServer();

    bool create(const std::string& bindPort);

    bool run();

    void stop();
    void destroy();

private:
    static void loError(int number, const char* message, const char* path);
    static int loHandle(const char* path, const char* types, lo_arg** argv, int argc, lo_message message,
                        void* userData);

    void handleMessage(const char* path, int argc, lo_arg** argv, const char* types, lo_address address,
            lo_message message);

    // Adds a message to m_messages queue, freeing any existing old message, and increments serial number.
    void queueMessage(const char* path, lo_message message);

    lo_server_thread m_tcpThread;
    lo_server m_tcpServer;

    std::chrono::steady_clock::time_point m_lastUpdateTime;

    int m_userSerial;

    // Map of userID to nickname strings.
    std::unordered_map<int, std::string> m_nameMap;

    // Map of userID to most recent ping time, used to check for timeouts.
    std::chrono::seconds m_timeout;
    std::unordered_map<int, std::chrono::steady_clock::time_point> m_clientPings;

    int m_maxMessagesPerRequest;

    static const int kMessageArraySize = 128;
    int m_messageSerial;
    std::array<const char*, kMessageArraySize> m_paths;
    std::array<lo_message, kMessageArraySize> m_messages;
};

} // namespace Confab

#endif // SRC_CONFAB_CHAT_SERVER_HPP_
