#ifndef SRC_CONFAB_ASSET_DATA_HPP_
#define SRC_CONFAB_ASSET_DATA_HPP_

#include "Database.hpp"
#include "SizedPointer.hpp"

#include <memory>

namespace flatbuffers {
    class FlatBufferBuilder;
}

namespace Confab {

namespace Data {
    class FlatAssetData;
    class FlatAssetDataBuilder;
}

/*! Represents a chunk of Asset data, broken into pieces for efficient storage, transmission, and verification.
 */
class AssetData {
public:
    /*! Methods for writing a new AssetData to a backing Flatbuffer store.
     */
    ///@{
    /*! Construct a new, empty, writable AssetData object.
     */
    AssetData();

    /*! Reserves space in the backing store for the data, returning a pointer to that space for writing.
     *
     * \param size How many bytes to reserve. Should be less than or equal to kDataChunkSize.
     * \return A non-owning pointer to the area reserved.
     */
    uint8_t* setData(size_t size);

    /*! Add a hash value for the computed data.
     *
     * \param hash The hash value of the data.
     */
    void setHash(uint64_t hash);

    /*! Finalize serialization and return a pointer to the flattened AssetData object.
     *
     * To avoid having to recreate a new AssetData object for each chunk of a large Asset file, flatten() creates a
     * mutable version of FlatAssetData object, allowing for calls to changeHash() after flatten(), as well as changing
     * the data pointed to by the pointer returned by setData(), but not the size of the data.
     *
     * \return A pointer to the flattened asset data suitable for serialization.
     */
    const SizedPointer flatten();

    /*! Change the hash value stored in a previously flattened AssetData object.
     *
     * \param hash The new hash value to store.
     */
    void changeHash(uint64_t hash);
    ///@}

    /*! Methods for read-only access to a backing flatbuffer store.
     */
    ///@{
    /*! Construct a read-only FlatAsset wrapped around a Database Record.
     *
     * \param record The Database::Record containing a serialized Data::FlatAsset.
     * \return A const FlatAsset object deserialized from record without copy.
     */
    static const AssetData LoadAssetData(const Database::Record& record);
    ///@}

private:
    AssetData(const Database::Record& record, const Data::FlatAssetData* flatAssetData);

    const Database::Record m_record;
    const Data::FlatAssetData* m_flatAssetData;

    std::shared_ptr<flatbuffers::FlatBufferBuilder> m_builder;
    std::shared_ptr<Data::FlatAssetDataBuilder> m_assetDataBuilder;
    Data::FlatAssetData* m_mutableFlatAssetData;
};

};  // namespace Confab

#endif  // SRC_CONFAB_ASSET_DATA_HPP_

