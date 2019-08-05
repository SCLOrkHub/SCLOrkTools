#include "AssetDatabase.hpp"

#include "Asset.hpp"
#include "Constants.hpp"
#include "Database.hpp"
#include "schemas/FlatAsset_generated.h"
#include "schemas/FlatAssetData_generated.h"
#include "schemas/FlatList_generated.h"

#include "glog/logging.h"

#include <array>
#include <chrono>
#include <cstring>

namespace Confab {

/*! The size in bytes of the key associated with an Asset in the database.
 *
 * Currently 9 bytes, counting one byte for the kAsset prefix, followed by 8 bytes of the Asset key.
 */
static const size_t kAssetKeySize = 9;

/*! The size in bytes of the key associated with an AssetData entry in the database.
 *
 * Currently 17 bytes, counting one byte for the kData prefix, followed by 8 bytes of the Asset key, followed by
 * 8 bytes indicated the chunk number (starting from 1).
 */
static const size_t kAssetDataKeySize = 17;

/*! List key size, 9 bytes with one for the kList prefix, followed by 8 bytes of List key.
 */
static const size_t kListKeySize = 9;

/*! Addition to lists is done by creating a new key (with no associated value) constructed from the kListEntry prefix,
 * followed by the 64-bit List unique identifier, followed by a 64-bit nanosecond time stamp, which is intended to keep
 * a monotonically increasing part of the key for lexical ordering, followed at last by the key of the data being added,
 * to avoid the (small) possibility of key collision between two different threads adding a list element at the exact
 * same time. This makes for a total key size of (3 * 8) + 1 = 25 bytes.
 */
static const size_t kListEntryKeySize = 25;

/*! Byte prefixes to prepend to Asset or AssetData keys for database. Keep these above 0x7f, so outside of normal ASCII
 * namespace so we can use the lower namespace for string prefixes.
 */
enum KeyPrefix : uint8_t {
    /*! Prefix for Asset metadata entries. Key is the kAsset prefix, followed by 8 bytes of the Asset key.
     */
    kAsset = 0xaa,

    /*! Prefix for AssetData entries. Key is the kAssetData prefix, followed by 8 bytes of Asset key, followed by 8
     * bytes of the chunk number.
     */
    kAssetData = 0xdd,

    /*! Prefix for List metadata entries. Key is the kList prefix, followed by 8 bytes of the List key.
     */
    kList = 0xee,

    /*! Prefix for List name entries. Key is the kListEntry prefix, followed by 8 bytes of the List key, followed by
     * an 8-byte timestamp, then the final 8 bytes of Asset key. There are no data associated with these keys.
     */
    kListEntry = 0xff
};

static const char* kAssetNamePrefix = "an";
static const char* kListNamePrefix = "ln";

/*! Maximum number of list entries the database will add an asset to.
 */
static const size_t kAssetMaxListEntries = 8;

namespace {

/*! Writes a byte sequence in keyOut suitable for storing or retrieving an Asset record from the database.
 *
 * \param key The key to format.
 * \param keyOut A pointer to where to store the key sequence, must be at least kAssetKeySize in size.
 */
void makeAssetKey(uint64_t key, SizedPointer keyOut) {
    CHECK_LE(kAssetKeySize, keyOut.size()) << "not enough room for an Asset database key.";

    keyOut.dataWritable()[0] = kAsset;
    std::memcpy(keyOut.dataWritable() + 1, reinterpret_cast<const uint8_t*>(&key), sizeof(uint64_t));
}

/*! Writes a byte sequence in keyOut suitable for storing or retrieving an AssetData record from the database.
 *
 * \param key The key to format.
 * \param chunkNumber The number in the sequence of chunks to include in the key.
 * \param keyOut A pointer to where to store the key sequence, must be at least kAssetDataKeySize in size.
 */
void makeAssetDataKey(uint64_t key, uint64_t chunkNumber, SizedPointer keyOut) {
    CHECK_LE(kAssetDataKeySize, keyOut.size()) << "not enough room for an AssetData database key.";

    keyOut.dataWritable()[0] = kAssetData;
    std::memcpy(keyOut.dataWritable() + 1, reinterpret_cast<const uint8_t*>(&key), sizeof(uint64_t));
    std::memcpy(keyOut.dataWritable() + 9, reinterpret_cast<const uint8_t*>(&chunkNumber), sizeof(uint64_t));
}

void makeListKey(uint64_t key, SizedPointer keyOut) {
    CHECK_LE(kListKeySize, keyOut.size());

    keyOut.dataWritable()[0] = kList;
    std::memcpy(keyOut.dataWritable() + 1, reinterpret_cast<const uint8_t*>(&key), sizeof(uint64_t));
}

}  // namespace

AssetDatabase::AssetDatabase(std::shared_ptr<Database> database) :
    m_database(database) {
}

RecordPtr AssetDatabase::findAsset(uint64_t key) {
    std::array<char, kAssetKeySize> assetDatabaseKey;
    SizedPointer flatKey(assetDatabaseKey.data(), kAssetKeySize);
    makeAssetKey(key, flatKey);

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
        makeAssetKey(deprecatedBy, flatKey);
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

RecordPtr AssetDatabase::findNamedAsset(const std::string& name) {
    // Look up name entry, if any.
    std::string nameKey = kAssetNamePrefix + name;
    RecordPtr namedAssetId = m_database->load(SizedPointer(nameKey.c_str(), nameKey.size()));
    if (namedAssetId->empty()) {
        LOG(WARNING) << "no named asset found under name " << name;
        return makeEmptyRecord();
    }

    uint64_t assetKey = 0;
    std::memcpy(&assetKey, namedAssetId->data().data(), sizeof(uint64_t));
    LOG(INFO) << "found key " << Asset::keyToString(assetKey) << " under name lookup " << name;
    return findAsset(assetKey);
}

bool AssetDatabase::storeAsset(uint64_t key, const SizedPointer& assetData) {
    Database::Batch batch;

    // First we parse the Asset data to extract the name, if any.
    const Data::FlatAsset* flatAsset = Data::GetFlatAsset(assetData.data());
    // This string, name has to live as long as the call to the database write(), so lives out here.
    std::string name;
    if (flatAsset->name() && flatAsset->name()->size() > 0) {
        name = kAssetNamePrefix + flatAsset->name()->str();
        LOG(INFO) << "adding name '" << flatAsset->name()->c_str() << "' lookup to asset " << Asset::keyToString(key);
        SizedPointer nameKey(name.c_str(), name.size());
        SizedPointer keyKey(reinterpret_cast<const uint8_t*>(&key), sizeof(uint64_t));
        batch.store(nameKey, keyKey);
    }

    // Add any list entries to the batch.
    uint8_t listKeys[kListEntryKeySize * kAssetMaxListEntries];
    uint64_t timeStamp = flatAsset->lists()->size() ?
        std::chrono::duration_cast<std::chrono::microseconds>(
        std::chrono::high_resolution_clock::now().time_since_epoch()).count() : 0;
    for (auto i = 0; i < flatAsset->lists()->size(); ++i) {
        SizedPointer listKey(listKeys + (i * kListEntryKeySize), kListEntryKeySize);
        SizedPointer listEntry;
        listKey.dataWritable()[0]  = kListEntry;
        std::memcpy(listKey.dataWritable() + 1, reinterpret_cast<const uint8_t*>(flatAsset->lists()->data() + i),
            sizeof(uint64_t));
        std::memcpy(listKey.dataWritable() + 9, reinterpret_cast<const uint8_t*>(&timeStamp), sizeof(uint64_t));
        std::memcpy(listKey.dataWritable() + 17, reinterpret_cast<const uint8_t*>(&key), sizeof(uint64_t));
        batch.store(listKey, listEntry);
    }

    // Store actual Asset key/value pair.
    std::array<uint8_t, kAssetKeySize> assetDatabaseKey;
    SizedPointer flatKey(assetDatabaseKey.data(), kAssetKeySize);
    makeAssetKey(key, flatKey);
    batch.store(flatKey, assetData);

    bool result = m_database->write(batch);
    if (result) {
        LOG(INFO) << "Asset store " << Asset::keyToString(key) << " success.";
    } else {
        LOG(ERROR) << "Failed to store Asset " << Asset::keyToString(key) << " in database.";
    }

    return result;
}

RecordPtr AssetDatabase::loadAssetDataChunk(uint64_t key, uint64_t chunk) {
    std::array<uint8_t, kAssetDataKeySize> assetDataKeyArray;
    SizedPointer assetDataKey(assetDataKeyArray.data(), kAssetDataKeySize);
    makeAssetDataKey(key, chunk, assetDataKey);
    RecordPtr assetDataRecord = m_database->load(assetDataKey);
    if (assetDataRecord->empty()) {
        LOG(ERROR) << "asset Data " << Asset::keyToString(key) << " chunk: " << chunk << " not found.";
    } else {
        LOG(INFO) << "Loaded Asset " << Asset::keyToString(key) << " chunk: " << chunk << ".";
    }

    return assetDataRecord;
}

bool AssetDatabase::storeAssetDataChunk(uint64_t key, uint64_t chunk, const SizedPointer& flatAssetData) {
    std::array<uint8_t, kAssetDataKeySize> assetDataKeyArray;
    SizedPointer assetDataKey(assetDataKeyArray.data(), kAssetDataKeySize);
    makeAssetDataKey(key, chunk, assetDataKey);

    bool result = m_database->store(assetDataKey, flatAssetData);
    if (result) {
        LOG(INFO) << "Asset Data store " << Asset::keyToString(key) << " chunk " << chunk << " success.";
    } else {
        LOG(ERROR) << "Failed to store Asset Data " << Asset::keyToString(key) << " chunk " << chunk << ".";
    }

    return result;
}

bool AssetDatabase::storeList(uint64_t key, const SizedPointer& listEntry) {
    Database::Batch batch;

    // Extract the name, if any, for storage in a lookup table.
    const Data::FlatList* flatList = Data::GetFlatList(listEntry.data());
    std::string name;
    if (flatList->name() && flatList->name()->size() > 0) {
        name = kListNamePrefix + flatList->name()->str();
        LOG(INFO) << "adding name '" << flatList->name()->c_str() << "' lookup to list " << Asset::keyToString(key);
        SizedPointer nameKey(name.c_str(), name.size());
        SizedPointer keyKey(reinterpret_cast<const uint8_t*>(&key), sizeof(uint64_t));
        batch.store(nameKey, keyKey);
    }

    // Make a sentinel entry which will always be the last entry in the list assuming lexigraphical ordering. This
    // allows us to reverse iterate from this element to get the latest.
    std::array<uint8_t, kListEntryKeySize> listEntryKeyArray;
    SizedPointer listEntryKey(listEntryKeyArray.data(), kListEntryKeySize);
    listEntryKey.dataWritable()[0] = kListEntry;
    std::memcpy(listEntryKey.dataWritable() + 1, &key, sizeof(uint8_t));
    std::memcpy(listEntryKey.dataWritable() + 9, &kEndList, sizeof(uint8_t));
    std::memcpy(listEntryKey.dataWritable() + 17, &kEndList, sizeof(uint8_t));
    batch.store(listEntryKey, SizedPointer());

    std::array<uint8_t, kListKeySize> listKeyArray;
    SizedPointer listKey(listKeyArray.data(), kListKeySize);
    makeListKey(key, listKey);
    batch.store(listKey, listEntry);

    bool result = m_database->write(batch);
    if (result) {
        LOG(INFO) << "List store " << Asset::keyToString(key) << " success.";
    } else {
        LOG(ERROR) << "Failed to store KeyList Data " << Asset::keyToString(key) << ".";
    }

    return result;
}

RecordPtr AssetDatabase::loadList(uint64_t key) {
    std::array<uint8_t, kListKeySize> listKeyArray;
    SizedPointer listKey(listKeyArray.data(), kListKeySize);
    makeListKey(key, listKey);

    RecordPtr listRecord = m_database->load(listKey);
    if (listRecord->empty()) {
        LOG(ERROR) << "error retrieving list " << Asset::keyToString(key) << ".";
    } else {
        LOG(INFO) << "loaded list " << Asset::keyToString(key) << ".";
    }

    return listRecord;
}

RecordPtr AssetDatabase::findNamedList(const std::string& name) {
    std::string nameKey = kListNamePrefix + name;
    RecordPtr namedListId = m_database->load(SizedPointer(nameKey.c_str(), nameKey.size()));
    if (namedListId->empty()) {
        LOG(WARNING) << "no named list found under name " << name;
        return makeEmptyRecord();
    }

    uint64_t listKey = 0;
    std::memcpy(&listKey, namedListId->data().data(), sizeof(uint64_t));
    LOG(INFO) << "found key " << Asset::keyToString(listKey) << " under name lookup " << name;
    return loadList(listKey);
}

size_t AssetDatabase::getListNext(uint64_t listKey, uint64_t fromToken, size_t maxPairs, uint64_t* listOut) {
    // Early-out for asking for the end of the list.
    if (fromToken == kEndList) {
        if (maxPairs >= 1) {
            listOut[0] = kEndList;
            listOut[1] = kEndList;
        }
        return 1;
    }

    uint64_t currentToken = fromToken;
    size_t pairs = 0;
    std::array<uint8_t, kListEntryKeySize> listEntryKeyArray;
    SizedPointer listEntryKey(listEntryKeyArray.data(), kListEntryKeySize);
    listEntryKey.dataWritable()[0] = kListEntry;

    while (currentToken != kEndList && pairs < maxPairs) {
    }

    return pairs;
}

}  // namespace Confab

