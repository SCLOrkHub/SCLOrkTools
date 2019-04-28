#include "Version.hpp"

namespace Common {

Version::Version(int major, int minor, int sub) :
    m_major(major),
    m_minor(minor),
    m_sub(sub) {
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

