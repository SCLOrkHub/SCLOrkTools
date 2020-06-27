#ifndef SRC_CONFAB_CHAT_COMMANDS_HPP_
#define SRC_CONFAB_CHAT_COMMANDS_HPP_

#include <string>

namespace Confab {

enum ChatCommands : int {
    kConnect,
    kSignIn,
    kGetAllClients,
    kSendMessage,
    kChangeName,
    kSignOut,
    kDisconnect,
    kNotFound
};

ChatCommands getCommandNamed(const std::string& name);

} // namespace Confab

#endif // SRC_CONFAB_CHAT_COMMANDS_HPP_
