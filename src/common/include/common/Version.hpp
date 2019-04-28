#ifndef SRC_COMMON_INCLUDE_COMMON_VERSION_HPP_
#define SRC_COMMON_INCLUDE_COMMON_VERSION_HPP_

#include <string>

namespace Common {

/*! Allows comparison of x.y.z style version numbers.
 *
 * A simple utility class supporting manipulation of version numbers provided in a major.minor.sub version style.
 */
class Version {
public:
    /*! Construct a Version object with provided version numbers. Negative numbers are truncated to zero.
     *
     * \param major The major version number.
     * \param minor The minor version number.
     * \param sub The sub version number.
     */
    Version(int major, int minor, int sub);

    /*! Copy constructor.
     *
     * \param version The Version object to copy.
     */
    Version(const Version& version);
    Version() = delete;

    /*! Value assignment.
     *
     * \param version The Version object to copy.
     * \returns A reference to this Version object.
     */
    Version& operator=(const Version& version);

    /*! Constructs a string in x.y.z format.
     *
     * \returns A string with the version numbers in major.minor.sub format.
     */
    std::string toString() const;

    /*! Version comparison for less than.
     *
     * \param version The version to compare against.
     * \returns True if this Version is lower than the supplied version.
     */
    bool operator<(const Version& version) const;

    /*! Version comparison for equality.
     *
     * \param version The version to compare to.
     * \returns True if this Version is equal to the supplied version.
     */
    bool operator==(const Version& version) const;

    /*! Version comparison for inequality.
     *
     * \param version The version to compare to.
     * \returns True if this Version is not equal to the supplied version.
     */
    bool operator!=(const Version& version) const;

    /*! Version comparison for greater than.
     *
     * \param version The version to compare against.
     * \returns True if this Version is greater than the supplied version.
     */
    bool operator>(const Version& version) const;

    /*! Version comparison for less than or equality.
     *
     * \param version The version to compare against.
     * \returns True if this Version is equal to or less than the supplied version.
     */
    bool operator<=(const Version& version) const;

    /*! Version comparison for greater than or equality.
     *
     * \param version The version to compare against.
     * \returns True if this Version is greater than or equal to the supplied version.
     */
    bool operator>=(const Version& version) const;

    /*! Version major value.
     *
     * \returns The value of the Version major number, or the "x" value in version "x.y.z".
     */
    int major() const { return m_major; }

    /*! Version minor value.
     *
     * \returns The value of the Version minor number, or the "y" value in version "x.y.z".
     */
    int minor() const { return m_minor; }

    /*! Version sub value.
     *
     * \returns The value of the Version sub number, or the "z" value in version "x.y.z".
     */
    int sub() const { return m_sub; }

private:
    int m_major;
    int m_minor;
    int m_sub;
};

}  // namespace Common

#endif  // SRC_COMMON_INCLUDE_COMMON_VERSION_HPP_
