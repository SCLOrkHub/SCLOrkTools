#ifndef SRC_CONFAB_ASSET_DATABASE_HPP_
#define SRC_CONFAB_ASSET_DATABASE_HPP_

#include "Record.hpp"
#include "SizedPointer.hpp"

#include <memory>

namespace Confab {

class Database;

/*! Class responsible for storage, retrieval, and verification of FlatAsset and FlatAssetData objects in the provided
 * file database.
 */
class AssetDatabase {
public:
    /*! Constructs an AssetDatabase.
     *
     * \param database A shared pointer to the database object.
     */
    AssetDatabase(std::shared_ptr<Database> database);

    /*! Locates an asset associated with the provided Id and returns it by calling the provided callback function.
     *
     * If the asset retrieved is marked as deprecated, this function will iteratively retrieve assets until it discovers
     * a non-deprecated Asset, and then will return that one. So it is possible that the returned Asset will have a
     * different key than the one requested.
     *
     * \param key The asset key associated with this asset.
     * \return A non-owning pointer to a FlatAsset record, or an empty Record on error.
     */
    RecordPtr findAsset(uint64_t key);

    /*! Stores a FlatAsset record with an already computed hash into the database.
     *
     * \param key The key to store the serialized asset under.
     * \param flatAsset The serialized asset data.
     * \return true on success, false on error.
     */
    bool storeAsset(uint64_t key, const SizedPointer& flatAsset);

    /*! Loads a chunk FlatAssetData record from the database.
     *
     * \param key The key associated with this asset.
     * \param chunk Which chunk number to load.
     * \return A non-owning pointer to the FlatAssetData record, or an empty Record on error.
     */
    RecordPtr loadAssetDataChunk(uint64_t key, uint64_t chunk);

    /*! Stores a FlatAssetData record for an Asset into the database.
     *
     * \param key The key to associate with this Asset data chunk.
     * \param chunk The chunk number to store this under.
     * \param flatAssetData The FlatAssetData record to save.
     * \return true on success, false on error.
     */
    bool storeAssetDataChunk(uint64_t key, uint64_t chunk, const SizedPointer& flatAssetData);

    /// @cond UNDOCUMENTED
    AssetDatabase() = delete;
    AssetDatabase(const AssetDatabase&) = delete;
    AssetDatabase& operator=(const AssetDatabase&) = delete;
    ~AssetDatabase() = default;
    /// @endcond UNDOCUMENTED

private:
    std::shared_ptr<Database> m_database;
};

}  // namespace Confab

#endif  // SRC_CONFAB_ASSET_DATABASE_HPP_

