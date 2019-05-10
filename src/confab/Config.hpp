#ifndef SRC_CONFAB_CONFIG_HPP_
#define SRC_CONFAB_CONFIG_HPP_

#include "Database.hpp"
#include "SizedPointer.hpp"
#include "common/Version.hpp"

#include <cstring>
#include <memory>

namespace flatbuffers {
    class FlatBufferBuilder;
}

namespace Confab {

namespace Data {
    class FlatConfig;
    class FlatConfigBuilder;
}

/*! Represents a Config object, a single copy of which is stored in the Confab database.
 */
class Config {
public:
    /*! Supplies the unique identifier used to store the singleton config object in the database.
     *
     * \return The key associated with the Config object.
     */
    static constexpr SizedPointer getConfigKey() {
        const char* kConfigKey = "confab-db-config";
        return SizedPointer(kConfigKey, std::strlen(kConfigKey));
    }

    /*! Methods for writing a new Config to a backing Flatbuffer store.
     */
    ///@{
    /*! Construct a new Config object with supplied Confab version.
     *
     * \param version The Confab version to store in this Config object.
     */
    Config(const Common::Version& version);

    /*! Serialize the Config object to a non-owning buffer.
     *
     * \return A non-owning pointer to the flattened Config array, owned by this Config object.
     */
    const SizedPointer flatten();
    ///@}

    /*! Methods for read-only access to a backing flatbuffer store.
     */
    ///@{
    /*! Check an underlying flatbuffer record to see if it is a valid Config record.
     *
     * \param record The serialized Config record to verify.
     * \return True if verify succeeded, false otherwise.
     */
    static bool Verify(const Database::Record& record);

    /*! Construct a read-only Config wrapped around a Database Record.
     *
     * \param record The Database::Record containing a serialized Data::FlatConfig.
     * \return A const Config object deserialized from record without copy.
     */
    static const Config LoadConfig(const Database::Record& record);

    /*! Return the version stored in the Config database entry.
     *
     * \return Version object representing the stored version value.
     */
    Common::Version version() const { return m_version; }
    ///@}

private:
    Config(const Database::Record& record, const Data::FlatConfig* flatConfig);

    const Database::Record m_record;
    const Data::FlatConfig* m_flatConfig;

    const Common::Version m_version;

    std::shared_ptr<flatbuffers::FlatBufferBuilder> m_builder;
    std::shared_ptr<Data::FlatConfigBuilder> m_configBuilder;
};

}  // namespace Confab

#endif  // SRC_CONFAB_CONFIG_HPP_

