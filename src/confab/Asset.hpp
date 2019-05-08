#ifndef SRC_CONFAB_ASSET_HPP_
#define SRC_CONFAB_ASSET_HPP_

#include <memory>
#include <optional>
#include <string>

namespace flatbuffers {
    class FlatBufferBuilder;
}

namespace Confab {

namespace Data {
    class FlatAsset;
    class FlatAssetBuilder;
}

/*! Represents the metadata associated with a Confab digital asset.
 *
 * Designed for read-only zero-copy access to the underlying serial FlatAsset buffer, or for construction of a new
 * FlatAsset buffer by adding data using the various set attribute methods.
 */
class Asset {
public:
    /*! Describes the type of the Asset.
     *
     * Keep these values the same as in the FlatAsset schema.
     */
    enum Type : uint32_t {
        kInvalid = 1,
        kSnippet = 2,
        kImage = 3,
        kYAML = 4,
        kSample = 5
    };

    /*! Methods for writing a new Asset to a backing Flatbuffer store.
     */
    ///@{
    /*! Utility to convert a string into the equivalent Asset::Type enumeration.
     *
     * \param assetType A string describing the asset type
     * \return The equivalent enumerated type, kInvalid on error.
     */
    static Type typeStringToEnum(const std::string& assetType);

    /*! Constructs a new Asset for writing.
     *
     * \param type The type of asset to make.
     */
    Asset(Type type);

    /*! Destruct an Asset.
     *
     */
    ~Asset();

    /*! Set a value for the key attribute, for writeable assets only.
     *
     * \param key The key value.
     */
    void setKey(uint64_t key);

    /*! Add the file extension string.
     *
     * \note The . is normally not stored.
     *
     * \param fileExtension The extension of the file, e.g. "yaml", "png", "wav".
     */
    void setFileExtension(const std::string& fileExtension);

    /*! Reserves space in the backing store for an inline buffer, returning the pointer to that space for writing.
     *
     * \param size How many bytes to reserve. Should be less than or equal to kSingleChunkDataSize.
     * \return A non-owning pointer to the area reserved.
     */
    uint8_t* setInlineData(size_t size);

    /*! Adds a salt value to the Asset.
     *
     * \param salt The salt value to add. It will be used as the starting state in hash computations.
     */
    void setSalt(uint64_t salt);
    ///@}

    /*! Methods for read-only access to a backing flatbuffer store.
     */
    ///@{
    /*! Constructs a new Asset for read-only access, based on a backing FlatAsset store.
     *
     * \param data The pointer to the FlatAsset. This Asset will not take ownership of the pointer, deletion is the
     *             pointer owner's responsibility.
     * \param key The key associated with this asset. If the serialized asset in data has a key the serialized key will
     *            override this value and it will be ignored. But if the key is absent from those data it can be
     *            provided here.
     */
    Asset(const uint8_t* data, uint64_t key = 0);

    /*! The type of this Asset.
     *
     * \return The type of this Asset.
     */
    Type type() const { return m_type; }

    /*! Asset unique identifier, must be non-zero.
     *
     * Note that some representations (namely the dictionary) don't serialize the key with the Asset so this field is
     * optional.
     *
     * \return The key of this Asset.
     */
    std::optional<uint64_t> key() const { return m_key; }

    /*! Human-readable name of the Asset, null-terminated string if present.
     *
     * \return A pointer to a human-readable name.
     */
    std::optional<const char*> name() const;

    /*! Extension of Asset file, e.g. ".yaml", ".scd", ".png". Null-terminated string if present.
     *
     * \return A string with a file extension, including the period.
     */
    std::optional<const char*> fileExtension() const;

    /*! Key of a YAML Asset describing a person who made this content, if present.
     *
     * \return Asset key for the Author entity.
     */
    std::optional<uint64_t> author() const;

    /*! Key of a newer Asset to use in place of this one, if present.
     *
     * \return The key for an Asset that deprecates this one.
     */
    std::optional<uint64_t> deprecatedBy() const;

    /*! Key of an older Asset this Asset replaces, if present.
     *
     * \return Asset key deprecated by this one.
     */
    std::optional<uint64_t> deprecates() const;

    /*! Attached Asset data, if present.
     *
     * Some Assets are small enough it makes sense to serialize the data directly with the Asset metadata. If so this
     * will point to a buffer of inlineDataSize() containing the Asset data.
     *
     * \return A pointer to the inline data.
     */
    std::optional<const uint8_t*> inlineData() const;

    /*! Size of Asset data, if present.
     *
     * \return The size of the inline data pointed to by inlineData().
     * \sa inlineData()
     */
    std::optional<size_t> inlineDataSize() const;

    /*! Specifies a UNIX epoch after which this Asset should be re-checked for validity or deleted.
     *
     * For cached Assets, or Assets likely to be deprecated, can store a timeout here.
     *
     * \return A UNIX epoch for when this Asset should be refreshed.
     */
    std::optional<uint64_t> expiresOn() const;

    /*! Provides an initial state for the hashing function, to reduce the chance of hash collision on small data sizes.
     *
     * \return A salt value.
     */
    std::optional<uint64_t> salt() const;
    ///@}

    /// @cond UNDOCUMENTED
    Asset() = delete;
    Asset(const Asset& asset) = delete;
    Asset& operator=(const Asset& asset) = delete;
    /// @endcond UNDOCUMENTED

private:
    const Data::FlatAsset* m_flatAsset;
    flatbuffers::FlatBufferBuilder* m_builder;
    Data::FlatAssetBuilder* m_assetBuilder;

    Type m_type;
    std::optional<uint64_t> m_key;
};

}  // namespace Confab

#endif  // SRC_CONFAB_ASSET_HPP_



