#ifndef SRC_CONFAB_CHAT_SERVER_HPP_
#define SRC_CONFAB_CHAT_SERVER_HPP_

#include "lo/lo.h"

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

    lo_server_thread m_tcpThread;
    lo_server m_tcpServer;
};

} // namespace Confab

#endif // SRC_CONFAB_CHAT_SERVER_HPP_
