#include "common/Version.hpp"

#include <algorithm>
#include <stdio.h>

namespace Common {

Version::Version(int major, int minor, int sub) :
    m_major(major),
    m_minor(minor),
    m_sub(sub) {
    m_major = std::max(m_major, 0);
    m_minor = std::max(m_minor, 0);
    m_sub = std::max(m_sub, 0);
}

Version::Version(const Version& version) {
    *this = version;
}

Version& Version::operator=(const Version& version) {
    m_major = version.major();
    m_minor = version.minor();
    m_sub = version.sub();
    return *this;
}

std::string Version::toString() const {
    char buffer[128];
    snprintf(buffer, 128, "%d.%d.%d", m_major, m_minor, m_sub);
    return std::string(buffer);
}

bool Version::operator<(const Version& version) const {
    // Comparison of major values first.
    if (m_major > version.major()) {
        return false;
    }
    if (m_major < version.major()) {
        return true;
    }

    // Major values are equal, compare minor second.
    if (m_minor > version.minor()) {
        return false;
    }
    if (m_minor < version.minor()) {
        return true;
    }

    // Minor values are equal, compare sub lastly.
    if (m_sub > version.sub()) {
        return false;
    }
    if (m_sub < version.sub()) {
        return true;
    }

    // All three values are equal.
    return false;
}

bool Version::operator==(const Version& version) const {
    return (m_major == version.major() && m_minor == version.minor() && m_sub == version.sub());
}

bool Version::operator!=(const Version& version) const {
    return (m_major != version.major() || m_minor != version.minor() || m_sub != version.sub());
}

bool Version::operator>(const Version& version) const {
    if (*this < version) {
        return false;
    }
    return (*this != version);
}

bool Version::operator<=(const Version& version) const {
    return !(*this > version);
}

bool Version::operator>=(const Version& version) const {
    return !(*this < version);
}


}  // namespace Common

