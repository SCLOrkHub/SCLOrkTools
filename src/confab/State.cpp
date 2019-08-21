#include "State.hpp"

#include "schemas/FlatState_generated.h"

#include "glog/logging.h"

#include <array>
#include <chrono>
#include <fstream>
#include <unistd.h>

namespace Confab {

State::State() :
    m_cpuTicksBusy(0),
    m_cpuTicksIdle(0),
    m_cpuPercentBusy(0.0f),
    m_memoryTotal(0),
    m_memoryFree(0),
    m_jackdPid(0),
    m_sclangPid(0),
    m_scidePid(0),
    m_scsynthPid(0) {
}

State::~State() {
}

void State::init() {
    // Get hostname of system.
    std::array<char, 32> hostname;
    gethostname(hostname.data(), sizeof(hostname));
    m_hostname = std::string(hostname.data());
    LOG(INFO) << "got machine hostname of: " << m_hostname;
}

const std::string& State::update() {
    updateCPUStats();
    updateMemStats();
    updatePids();
    return toString();
}

const std::string& State::toString() {
    std::array<char, 128> buffer;
    snprintf(buffer.data(), 128, "%s|%c%c%c%c|%d|%d", m_hostname.c_str(), m_scidePid > 0 ? 'I' : 'i',
        m_sclangPid > 0 ? 'L' : 'l', m_jackdPid > 0 ? 'J' : 'j', m_scsynthPid > 0 ? 'S' : 's',
        static_cast<int>(m_cpuPercentBusy), static_cast<int>((100 * m_memoryFree) / m_memoryTotal));
    m_state = std::string(buffer.data());
    return m_state;
}

void State::updateCPUStats() {
    std::ifstream procStat;
    procStat.open("/proc/stat");
    if (!procStat) {
        LOG(ERROR) << "error opening /proc/stat, can't update CPU stats.";
        return;
    }

    // Read first line, first column should be the string "cpu".
    std::string cpu;
    std::getline(procStat, cpu, ' ');
    if (!procStat || cpu != "cpu") {
        LOG(ERROR) << "error reading /proc/stat, expected 'cpu' but got '" << cpu << "'";
        return;
    }

    // Next four columns are the user, user nice, system, and idle tick counts.
    uint64_t userTicks = 0;
    uint64_t userNiceTicks = 0;
    uint64_t systemTicks = 0;
    uint64_t idleTicks = 0;

    procStat >> userTicks;
    procStat >> userNiceTicks;
    procStat >> systemTicks;
    procStat >> idleTicks;

    if (!procStat || userTicks == 0 || userNiceTicks == 0 || systemTicks == 0 || idleTicks == 0) {
        LOG(ERROR) << "error reading /proc/stat, can't update CPU stats.";
        return;
    }

    procStat.close();

    uint64_t busyTicks = userTicks + userNiceTicks + systemTicks;
    m_cpuPercentBusy = (static_cast<float>(busyTicks - m_cpuTicksBusy) * 100.0f) /
        static_cast<float>(idleTicks - m_cpuTicksIdle);
    if (m_cpuPercentBusy > 90.0f) {
        LOG(WARNING) << "cpu at " << m_cpuPercentBusy << "%% load.";
    }

    m_cpuTicksBusy = busyTicks;
    m_cpuTicksIdle = idleTicks;
}

void State::updateMemStats() {
    std::ifstream memInfo;
    memInfo.open("/proc/meminfo");
    if (!memInfo) {
        LOG(ERROR) << "error opening /proc/meminfo, can't update memory stats.";
        return;
    }

    // First line is MemTotal: <number> kB
    std::string key;
    std::getline(memInfo, key, ' ');
    if (!memInfo || key != "MemTotal:") {
        LOG(ERROR) << "error parsing /proc/meminfo, expected 'MemTotal:' but got '" << key << "'";
        return;
    }

    memInfo >> m_memoryTotal;

    // Get rest of line, then following MemFree key.
    std::getline(memInfo, key, '\n');
    std::getline(memInfo, key, ' ');
    if (!memInfo || key != "MemFree:") {
        LOG(ERROR) << "error parsing /proc/meminfo, expected 'MemFree:' but got '" << key << "'";
        return;
    }

    memInfo >> m_memoryFree;
}

void State::updatePids() {
    if (m_jackdPid > 0 && !stillRunning(m_jackdPid)) {
        LOG(INFO) << "jackd process " << m_jackdPid << " no longer running.";
        m_usedPids.erase(m_jackdPid);
        m_jackdPid = 0;
    }

    if (m_sclangPid > 0 && !stillRunning(m_sclangPid)) {
        LOG(INFO) << "sclang process " << m_sclangPid << " no longer running.";
        m_usedPids.erase(m_sclangPid);
        m_sclangPid = 0;
    }

    if (m_scidePid > 0 && !stillRunning(m_scidePid)) {
        LOG(INFO) << "scide process " << m_scidePid << " no longer running.";
        m_usedPids.erase(m_scidePid);
        m_scidePid = 0;
    }

    if (m_scsynthPid > 0 && !stillRunning(m_scsynthPid)) {
        LOG(INFO) << "scsynth process " << m_scsynthPid << " no longer running.";
        m_usedPids.erase(m_scsynthPid);
        m_scsynthPid = 0;
    }

    // If any pids are not found yet we enumerate all pids.
    if (m_jackdPid == 0 || m_sclangPid == 0 || m_scidePid == 0 || m_scsynthPid == 0) {
        for (auto& entry : fs::directory_iterator("/proc")) {
            fs::path path = entry.path();
            // The running processes are described in directories with numeric names with value of the pid.
            if (fs::is_directory(path)) {
                // The length of 6 is the length of "/proc/".
                std::string directoryName = path.string().substr(6);
                int pid = strtol(directoryName.data(), nullptr, 10);
                if (pid > 0 && m_usedPids.count(pid) == 0) {
                    m_usedPids.insert(pid);
                    // Parse the comm file, to get the name of the binary associated with this pid.
                    fs::path commPath = path / "comm";
                    std::ifstream commFile;
                    commFile.open(commPath);
                    std::string comm;
                    if (commFile) {
                        std::getline(commFile, comm);
                        if (m_jackdPid == 0 && comm == "jackd") {
                            LOG(INFO) << "jackd process detected at " << pid;
                            m_jackdPid = pid;
                        } else if (m_sclangPid == 0 && comm == "sclang") {
                            LOG(INFO) << "sclang process detected at " << pid;
                            m_sclangPid = pid;
                        } else if (m_scidePid == 0 && comm == "scide") {
                            LOG(INFO) << "scide process detected at " << pid;
                            m_scidePid = pid;
                        } else if (m_scsynthPid == 0 && comm == "scsynth") {
                            LOG(INFO) << "scsynth process detected at " << pid;
                            m_scsynthPid = pid;
                        }
                    } else {
                        LOG(ERROR) << "error opening pid comm file " << commPath;
                    }
                }
            }
        }
    }
}

bool State::stillRunning(int pid) {
    std::array<char, 32> pathBuffer;
    snprintf(pathBuffer.data(), 32, "/proc/%d", pid);
    return fs::exists(fs::path(pathBuffer.data()));
}

}  // namespace Confab

