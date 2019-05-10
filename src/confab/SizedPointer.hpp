#ifndef SRC_CONFAB_SIZED_POINTER_HPP_
#define SRC_CONFAB_SIZED_POINTER_HPP_

#include <cstdint>
#include <cstddef>

namespace Confab {

/*! Header-only convenience class for bundling a non-owning pointer and its size, along with some casting aid.
 */
class SizedPointer {
public:
    /*! Construct an empty SizedPointer.
     */
    constexpr SizedPointer() : m_data(nullptr), m_size(0) { }

    /*! Construct a constant sized pointer.
     *
     * \param data The data to point to.
     * \param size The size of data in bytes.
     */
    constexpr SizedPointer(const uint8_t* data, size_t size) : m_data(data), m_size(size) { }

    /*! Construct a constant sized pointer, convenience cast to char*
     *
     * \param data The data to point to, will be cast to uint8_t*.
     * \param size The size of data in bytes.
     */
    constexpr SizedPointer(const char* data, size_t size) :
        m_data(reinterpret_cast<const uint8_t*>(data)),
        m_size(size) { }

    /*! Construct a sized pointer.
     *
     * \param data The data to point to.
     * \param size The size of data in bytes.
     */
    SizedPointer(uint8_t* data, size_t size) :
        m_data(data),
        m_size(size) {
    }

    /// @cond UNDOCUMENTED
    SizedPointer(const SizedPointer&) = default;
    SizedPointer& operator=(const SizedPointer&) = default;
    ~SizedPointer() = default;
    /// @endcond UNDOCUMENTED

    /*! Exposes the contained data pointer.
     *
     * \return A const pointer to the contained data.
     */
    const uint8_t* data() const { return m_data; }

    /*! Exposes the contained data pointer, cast as a const char* for convenience.
     *
     * \return A const pointer to the contained data.
     */
    const char* dataChar() const { return reinterpret_cast<const char*>(m_data); }

    /*! Writable data pointer.
     *
     * \return A pointer to the contained data.
     */
    uint8_t* dataWritable() { return const_cast<uint8_t*>(m_data); }

    /*! The size of the target data.
     *
     * \return The size of what's pointed to by data().
     */
    size_t size() const { return m_size; }

private:
    const uint8_t* m_data;
    const size_t m_size;
};

}  // namespace Confab

#endif  // SRC_CONFAB_SIZED_POINTER_HPP_

