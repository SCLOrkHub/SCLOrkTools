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

    /*! Locates an asset associated with the provided key and returns it.
     *
     * If the asset retrieved is marked as deprecated, this function will iteratively retrieve assets until it discovers
     * a non-deprecated Asset, and then will return that one. So it is possible that the returned Asset will have a
     * different key than the one requested.
     *
     * \param key The asset key associated with this asset.
     * \return A non-owning pointer to a FlatAsset record, or an empty Record on error.
     */
    RecordPtr findAsset(uint64_t key);

    /*! Locates an Asset associated with the provided name and returns it.
     *
     * Just like findAsset, will return the most recent version of the requested Asset, following deprecations.
     *
     * \param name The name of the asset to look up.
     * \return A non-owning pointer to the FlatAsset record, or an empty Record on error.
     */
    RecordPtr findNamedAsset(const std::string& name);

    /*! Stores a FlatAsset record with an already computed hash into the database.
     *
     * \param key The key to store the serialized asset under.
     * \param assetData The serialized asset data.
     * \return true on success, false on error.
     */
    bool storeAsset(uint64_t key, const SizedPointer& assetData);

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

    /*! Stores a new List entity into the database.
     *
     * \param key The list key to associate with this List.
     * \param listData A pointer to a FlatList record to store.
     * \return true on success, false on error.
     */
    bool storeList(uint64_t key, const SizedPointer& listData);

    /*! Loads a list with the provided key and returns it.
     *
     * \param key The list key to load.
     * \return A non-owning pointer to the FlatList record, or an empty record on error.
     */
    RecordPtr loadList(uint64_t key);

    /*! Loads a list by looking for the provided name.
     *
     * \param name The name of the list to look up.
     * \return A non-owning pointer to the FlatList record associated with name, or an empty record on error.
     */
    RecordPtr findNamedList(const std::string& name);

    /*! Populates the provided buffer with <token, key> pairs from a list. If it reaches the end of the list it will
     * put a <kEndList, kEndList> pair at the end.
     *
     * \param listKey The key of the list to draw from.
     * \param fromToken The token to start with, or 0 if starting from the beginning. Returned list will not include
     *                  this token.
     * \param maxPairs The maximum number of <token, key> pairs to put into listOut.
     * \param listOut A pointer to a buffer to hold the ordered list.
     * \return The number of elements written into listOut, or 0 on error (as at end of list will always return
     *          the end of list pair <kEndList, kEndList>.
     */
    size_t getListNext(uint64_t listKey, uint64_t fromToken, size_t maxPairs, uint64_t* listOut);

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

