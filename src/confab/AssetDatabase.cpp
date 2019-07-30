#include "AssetDatabase.hpp"

#include "Asset.hpp"
#include "Database.hpp"
#include "schemas/FlatAsset_generated.h"
#include "schemas/FlatAssetData_generated.h"

#include "glog/logging.h"

#include <array>
#include <cstring>

namespace Confab {

/*! The size in bytes of the key associated with an Asset in the database.
 *
 * Currently 9 bytes, counting one byte for the kAsset prefix, followed by 8 bytes of the Asset key.
 */
static const size_t kAssetDatabaseKeySize = 9;

/*! The size in bytes of the key associated with an AssetData entry in the database.
 *
 * Currently 17 bytes, counting one byte for the kData prefix, followed by 8 bytes of the Asset key, followed by
 * 8 bytes indicated the chunk number (starting from 1).
 */
static const size_t kAssetDataDatabaseKeySize = 17;

/*! Byte prefixes to prepend to Asset or AssetData keys for database.
 */
enum KeyPrefix : uint8_t { kAsset = 0xaa, kData = 0xdd };

/*! Writes a byte sequence in keyOut suitable for storing or retrieving an Asset record from the database.
 *
 * \param key The key to format.
 * \param keyOut A pointer to where to store the key sequence, must be at least kAssetDatabaseKeySize in size.
 */
void makeAssetDatabaseKey(uint64_t key, SizedPointer keyOut) {
    CHECK_LE(kAssetDatabaseKeySize, keyOut.size()) << "not enough room for an Asset database key.";

    keyOut.dataWritable()[0] = kAsset;
    std::memcpy(keyOut.dataWritable() + 1, reinterpret_cast<const uint8_t*>(&key), sizeof(uint64_t));
}

/*! Writes a byte sequence in keyOut suitable for storing or retrieving an AssetData record from the database.
 *
 * \param key The key to format.
 * \param chunkNumber The number in the sequence of chunks to include in the key.
 * \param keyOut A pointer to where to store the key sequence, must be at least kAssetDataDatabaseKeySize in size.
 */
void makeAssetDataDatabaseKey(uint64_t key, uint64_t chunkNumber, SizedPointer keyOut) {
    CHECK_LE(kAssetDataDatabaseKeySize, keyOut.size()) << "not enough room for an AssetData database key.";

    keyOut.dataWritable()[0] = kData;
    std::memcpy(keyOut.dataWritable() + 1, reinterpret_cast<const uint8_t*>(&key), sizeof(uint64_t));
    std::memcpy(keyOut.dataWritable() + 9, reinterpret_cast<const uint8_t*>(&chunkNumber), sizeof(uint64_t));
}

AssetDatabase::AssetDatabase(std::shared_ptr<Database> database) :
    m_database(database) {
}

RecordPtr AssetDatabase::findAsset(uint64_t key) {
    std::array<char, kAssetDatabaseKeySize> assetDatabaseKey;
    SizedPointer flatKey(assetDatabaseKey.data(), kAssetDatabaseKeySize);
    makeAssetDatabaseKey(key, flatKey);

    RecordPtr assetRecord = m_database->load(flatKey);
    if (assetRecord->empty()) {
        LOG(ERROR) << "Asset " << Asset::keyToString(key) << " not found in database.";
        return makeEmptyRecord();
    }
    uint64_t loadedKey = key;
    auto flatAsset = Data::GetFlatAsset(assetRecord->data().data());
    while (flatAsset->deprecatedBy()) {
        uint64_t deprecatedBy = flatAsset->deprecatedBy();
        LOG(INFO) << "Asset " << Asset::keyToString(key) << " deprecated by " << Asset::keyToString(deprecatedBy)
            << ", loading.";
        makeAssetDatabaseKey(deprecatedBy, flatKey);
        assetRecord = m_database->load(flatKey);
        if (assetRecord->empty()) {
            LOG(ERROR) << "error loaded deprecating asset " << Asset::keyToString(deprecatedBy) << ".";
            return makeEmptyRecord();
        }
        flatAsset = Data::GetFlatAsset(assetRecord->data().data());
        loadedKey = deprecatedBy;
    }
    LOG(INFO) << "Loaded Asset " << Asset::keyToString(loadedKey) << " upon request to load original asset "
        << Asset::keyToString(key);
    return assetRecord;
}

bool AssetDatabase::storeAsset(uint64_t key, const SizedPointer& flatAsset) {
    std::array<char, kAssetDatabaseKeySize> assetDatabaseKey;
    SizedPointer flatKey(assetDatabaseKey.data(), kAssetDatabaseKeySize);
    makeAssetDatabaseKey(key, flatKey);

    bool result = m_database->store(flatKey, flatAsset);
    if (result) {
        LOG(INFO) << "Asset store " << Asset::keyToString(key) << " success.";
    } else {
        LOG(ERROR) << "Failed to store Asset " << Asset::keyToString(key) << " in database.";
    }
    return result;
}

RecordPtr AssetDatabase::loadAssetDataChunk(uint64_t key, uint64_t chunk) {
    std::array<uint8_t, kAssetDataDatabaseKeySize> assetDataKeyArray;
    SizedPointer assetDataKey(assetDataKeyArray.data(), kAssetDataDatabaseKeySize);
    makeAssetDataDatabaseKey(key, chunk, assetDataKey);
    RecordPtr assetDataRecord = m_database->load(assetDataKey);
    if (assetDataRecord->empty()) {
        LOG(ERROR) << "asset Data " << Asset::keyToString(key) << " chunk: " << chunk << " not found.";
    } else {
        LOG(INFO) << "Loaded Asset " << Asset::keyToString(key) << " chunk: " << chunk << ".";
    }

    return assetDataRecord;
}

bool AssetDatabase::storeAssetDataChunk(uint64_t key, uint64_t chunk, const SizedPointer& flatAssetData) {
    std::array<char, kAssetDataDatabaseKeySize> assetDataKeyArray;
    SizedPointer assetDataKey(assetDataKeyArray.data(), kAssetDataDatabaseKeySize);
    makeAssetDataDatabaseKey(key, chunk, assetDataKey);

    bool result = m_database->store(assetDataKey, flatAssetData);
    if (result) {
        LOG(INFO) << "Asset Data store " << Asset::keyToString(key) << " chunk " << chunk << " success.";
    } else {
        LOG(ERROR) << "Failed to store Asset Data " << Asset::keyToString(key) << " chunk " << chunk << ".";
    }

    return result;
}

}  // namespace Confab
