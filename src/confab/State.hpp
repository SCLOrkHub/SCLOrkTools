#ifndef SRC_CONFAB_STATE_HPP_
#define SRC_CONFAB_STATE_HPP_

#include "Record.hpp"

#include <experimental/filesystem>
#include <string>
#include <unordered_set>

namespace fs = std::experimental::filesystem;

namespace Confab {

/*! Represents the realtime state of a machine and user on the SCLOrk local-area network. Can produce FlatState record
 * on request, and caches the current values to make updating the FlatState record cheaper.
 */
class State {
public:
    /*! Constructs an empty State object.
     */
    State();

    /*! Destructs a State object.
     */
    ~State();

    /*! Initializes the immutable members (those that don't change from boot to boot).
     */
    void init();

    /*! Updates all tracking and returns latest serialized state.
     */
    void update();

    /*! Release any system resources.
     */
    void shutdown();



private:
    /*! Linux-only, parses the /proc/stat file to get CPU usage info.
     */
    void updateCPUStats();

    /*! Linux-only, parses the /proc/meminfo file to get memory usage info.
     */
    void updateMemStats();

    /*! Adds any new running commands, and removes any that are no longer running.
     */
    void updatePids();

    /*! Returns true if the provided pid directory still exists, meaning process is still running.
     */
    bool stillRunning(int pid);

    std::string m_hostname;

    uint64_t m_cpuTicksBusy;
    uint64_t m_cpuTicksIdle;
    float m_cpuPercentBusy;

    uint64_t m_memoryTotal;
    uint64_t m_memoryFree;

    std::unordered_set<int> m_usedPids;
    int m_jackdPid;
    int m_sclangPid;
    int m_scidePid;
    int m_scsynthPid;
};

}

#endif  // SRC_CONFAB_STATE_HPP_

