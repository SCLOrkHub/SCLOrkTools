#ifndef SRC_CONFAB_CHAT_SERVER_HPP_
#define SRC_CONFAB_CHAT_SERVER_HPP_

#include "lo/lo.h"

#include <array>
#include <string>
#include <unordered_map>

namespace Confab {

/*! Implementation of the sclang-based SCLOrkServer using TCP and liblo instead.
 */
class ChatServer {
public:
    ChatServer();
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

    // Packs an ipv4 tuple + port from the lo_address into a 48-bit unsigned integer. Probably some endian assumptions
    // in here, but as long as this value doesn't leave this computer it should be fine. Returns 0 on error.
    uint64_t makeToken(lo_address address);
    // Need to free the returned address with lo_address_free() once done.
    lo_address makeAddress(uint64_t token);

    // Adds a message to m_messages queue, freeing any existing old message, and increments serial number.
    void queueMessage(const char* path, lo_message message);

    lo_server_thread m_tcpThread;
    lo_server m_tcpServer;

    int m_userSerial;

    // Map of userID to nickname strings.
    std::unordered_map<int, std::string> m_nameMap;

    // Map of address/port tokens to userID.
    std::unordered_map<uint64_t, int> m_addressMap;

    static const size_t kMessageArraySize = 1024;
    int m_messageSerial;
    std::array<const char*, kMessageArraySize> m_paths;
    std::array<lo_message, kMessageArraySize> m_messages;
};

} // namespace Confab

#endif // SRC_CONFAB_CHAT_SERVER_HPP_
