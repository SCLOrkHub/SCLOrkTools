#include "State.hpp"

#include "schemas/FlatState_generated.h"

#include "glog/logging.h"

#include <array>
#include <chrono>
#include <fstream>
#include <unistd.h>

namespace Confab {

State::State() :
    m_user(0),
    m_cpuTicksBusy(0),
    m_cpuTicksIdle(0),
    m_cpuPercentBusy(0.0f) {
}

State::~State() {
}

void State::init() {
    // Get hostname of system.
    std::array<char, 32> hostname;
    gethostname(hostname.data(), sizeof(hostname));
    m_hostname = std::string(hostname.data());
    LOG(INFO) << "got machine hostname of: " << m_hostname;

    updateCPUStats();
}

RecordPtr State::update() {
    updateCPUStats();
    updateMemStats();
    return makeEmptyRecord();
}

void State::shutdown() {
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
    // We need at least one old data point to compute the delta.
    if (m_cpuTicksBusy > 0) {
        m_cpuPercentBusy = (static_cast<float>(busyTicks - m_cpuTicksBusy) * 100.0f) /
            static_cast<float>(idleTicks - m_cpuTicksIdle);
        if (m_cpuPercentBusy > 90.0f) {
            LOG(WARNING) << "cpu at " << m_cpuPercentBusy << "%% load.";
        }
    }

    m_cpuTicksBusy = busyTicks;
    m_cpuTicksIdle = idleTicks;
}

void State::updateMemStats() {
}

}  // namespace Confab

