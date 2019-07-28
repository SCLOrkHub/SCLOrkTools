#ifndef SRC_CONFAB_CONFAB_COMMON_HPP_
#define SRC_CONFAB_CONFAB_COMMON_HPP_

#include "gflags/gflags.h"

#include <experimental/filesystem>
#include <memory>

DECLARE_string(data_directory);

namespace Confab {

class AssetManager;
class Database;

/*! Utility class for initalization of subsystems common between confab and confab-server.
 */
class ConfabCommon {
public:
    /*! Initializes the common subsystems.
     *
     * \param argc The number of command-line arguments, from int main().
     * \param argv The arguments array, from int main().
     * \return True on success, false on error.
     */
    bool initialize(int argc, char* argv[]);

    /*! Shuts down the common subsystems.
     */
    void shutdown();

private:
    /*! Initialize the logging subsystem. Because all other systems depend on logging initialize this first.
     *
     * \param binaryName A pointer to the name of the invoked binary, typically extracted from argv[0].
     */
    void initializeLogging(char* binaryName);

    /*! Check for an already existing pid sentinel file, and create one if one does not yet exist.
     *
     * \return true if file created, false if file already exists.
     */
    bool checkSentinelFile();

    /*! Sets the interrupt mask on threads to ignore SIGINT, allowing it to be caught by a handler to shutdown.
     *
     * \return true on success, false on error.
     */
    bool setThreadMask();

    std::experimental::filesystem::path m_pidPath;
};


}  // namespace Confab

#endif  // SRC_CONFAB_CONFAB_COMMON_HPP_

