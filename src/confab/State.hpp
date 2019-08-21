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
     *
     * \return The stringified version of the current State.
     */
    const std::string& update();

    /*! Converts current state to a compact string representation suitable for sending over network.
     *
     * Current State string format:
     *
     * hostname|iljs|CPU|MEM
     *
     * Hostname is a string. The next field is the state of the different processes, in order scIde, scLang, Jackd, and
     * finally scSynth. If the character is lowercase the process is not detected, if upper case it is running. the CPU
     * and MEM fields are decimal percentage values from 0-100.
     *
     * \return The string format of State.
     */
    const std::string& toString();

    /*! Get the hostname.
     *
     * \return A string with this machine's hostname.
     */
    const std::string& hostname() const { return m_hostname; }

    /*! Get the percentage CPU load between this and last call to update().
     *
     * \return A number within [0.0, 100.0] reflecting current CPU load.
     */
    float cpuPercentBusy() const { return m_cpuPercentBusy; }

    /*! Get the total memory of the machine in KB.
     *
     * \return A number of KB the machine has in total.
     */
    uint64_t memoryTotal() const { return m_memoryTotal; }

    /*! Get the free memory of the machine in KB.
     *
     * \return A number of KB the machine has unused.
     */
    uint64_t memoryFree() const { return m_memoryFree; }

    /*! Returns the pid of the jackd process, or 0 if not detected.
     *
     * \return A pid.
     */
    int jackdPid() const { return m_jackdPid; }

    /*! Returns the pid of the sclang process, or 0 if not detected.
     *
     * \return A pid.
     */
    int sclangPid() const { return m_sclangPid; }

    /*! Returns the pid of the scide process, or 0 if not detected.
     *
     * \return A pid.
     */
    int scidePid() const { return m_scidePid; }

    /*! Returns the pid of the scsynth process, or 0 if not detected.
     *
     * \return A pid.
     */
    int scsynthPid() const { return m_scsynthPid; }

private:
    /*! Linux only, parses the /proc/stat file to get CPU usage info.
     */
    void updateCPUStats();

    /*! Linux only, parses the /proc/meminfo file to get memory usage info.
     */
    void updateMemStats();

    /*! Linux only. Adds any new running commands, and removes any that are no longer running.
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

    std::string m_state;
};

}

#endif  // SRC_CONFAB_STATE_HPP_

