#ifndef SRC_CONFAB_ASSET_MANAGER_HPP_
#define SRC_CONFAB_ASSET_MANAGER_HPP_

#include <functional>
#include <memory>

namespace Confab {

class Database;

/*! Class responsible for the creation and retrieval of assets in filesystem and database.
 */
class AssetManager {
public:
    /*! Construct an AssetManager.
     *
     * \param database A pointer to the database object.
     */
    AssetManager(std::shared_ptr<Database> database);

    /*! Add a new asset in a file to the database.
     *
     * \param assetType A string describing one of the enumerated types in Asset::Type.
     * \param filePath A string with the path to the file to add.
     * \param callabck A function to callback with the Asset key, once computed, or 0 if error.
     */
    void addAssetFile(const std::string& assetType, const std::string& filePath,
        std::function<void(uint64_t)> callback);

    /*! {
     */
    AssetManager() = delete;
    AssetManager(const AssetManager&) = delete;
    AssetManager& operator=(const AssetManager&) = delete;
    ~AssetManager() = default;
    /*! }
     */

private:
    std::shared_ptr<Database> database;
};

}  // namespace Confab

#endif  // SRC_CONFAB_ASSET_MANAGER_HPP_

