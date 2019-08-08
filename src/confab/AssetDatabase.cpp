#include "AssetDatabase.hpp"

#include "Asset.hpp"
#include "Constants.hpp"
#include "schemas/FlatAsset_generated.h"
#include "schemas/FlatAssetData_generated.h"
#include "schemas/FlatList_generated.h"

#include "glog/logging.h"
#include "leveldb/cache.h"
#include "leveldb/db.h"
#include "leveldb/write_batch.h"

#include <array>
#include <chrono>
#include <cstring>

namespace {

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

/*! Character prefixes to prepend to Asset or AssetData keys for database.
 */
enum KeyPrefix : char {
    /*! Prefix for Asset metadata entries. Key is the kAsset prefix, followed by 8 bytes of the Asset key.
     */
    kAsset = 'a',

    /*! Prefix for AssetData entries. Key is the kAssetData prefix, followed by 8 bytes of Asset key, followed by 8
     * bytes of the chunk number.
     */
    kAssetData = 'd',

    /*! Prefix for List metadata entries. Key is the kList prefix, followed by 8 bytes of the List key.
     */
    kList = 'l',

    /*! Prefix for List name entries. Key is the kListEntry prefix, followed by 8 bytes of the List key, followed by
     * an 8-byte timestamp, then the final 8 bytes of Asset key. There are no data associated with these keys.
     */
    kListEntry = 'e'
};

static const char* kAssetNamePrefix = "na";
static const char* kListNamePrefix = "nl";

/*! Maximum number of list entries the database will add an asset to.
 */
static const size_t kAssetMaxListEntries = 8;

/*! Writes a byte sequence in keyOut suitable for storing or retrieving an Asset record from the database.
 *
 * \param key The key to format.
 * \param keyOut A pointer to where to store the key sequence, must be at least kAssetKeySize in size.
 */
inline void makeAssetKey(uint64_t key, char* keyOut) noexcept {
    keyOut[0] = kAsset;
    std::memcpy(keyOut + 1, reinterpret_cast<const char*>(&key), sizeof(uint64_t));
}

/*! Writes a byte sequence in keyOut suitable for storing or retrieving an AssetData record from the database.
 *
 * \param key The key to format.
 * \param chunkNumber The number in the sequence of chunks to include in the key.
 * \param keyOut A pointer to where to store the key sequence, must be at least kAssetDataKeySize in size.
 */
inline void makeAssetDataKey(uint64_t key, uint64_t chunkNumber, char* keyOut) noexcept {
    keyOut[0] = kAssetData;
    std::memcpy(keyOut + 1, reinterpret_cast<const char*>(&key), sizeof(uint64_t));
    std::memcpy(keyOut + 9, reinterpret_cast<const char*>(&chunkNumber), sizeof(uint64_t));
}

inline void makeListKey(uint64_t key, char* keyOut) noexcept {
    keyOut[0] = kList;
    std::memcpy(keyOut + 1, reinterpret_cast<const char*>(&key), sizeof(uint64_t));
}

inline bool iteratorMatch(std::shared_ptr<leveldb::Iterator> iterator, char* key, size_t keySize) noexcept {
    return iterator->Valid() &&
           iterator->key().size() == keySize &&
           std::memcmp(key, iterator->key().data(), keySize) == 0;
}

}  // namespace

namespace Confab {

/*! The DatabaseRecord is a Database-specific implementation of the non-copying backing store.
 *
 * It maintains a leveldb::Iterator pointer, which is where the Database data is sourced from. It deletes this pointer
 * in its own destructor.
 */
class DatabaseRecord : public Record {
public:

    /*! Default constructor not supported, use makeEmptyRecord().
     */
    DatabaseRecord() = delete;

    /*! Construct a record pointing at a Database load result.
     *
     * \param iterator The LevelDB data access iterator pointing at the desired results.
     */
    DatabaseRecord(std::shared_ptr<leveldb::Iterator> iterator) : m_iterator(iterator) {
    }

    /*! Deletes a DatabaseRecord.
     */
    ~DatabaseRecord() override {
    }

    /*! True if this Record has no results.
     *
     * \return A boolean which is true if this Record is pointing at nothing.
     */
    bool empty() const override { return m_iterator == nullptr; }

    /*! A pointer to the data associated with the key in the Database.
     *
     * \return A non-owning pointer to the data. Record will take care of the deletion of this pointer.
     */
    const SizedPointer data() const override {
        return SizedPointer(m_iterator->value().data(), m_iterator->value().size());
    }

    /*! The key associated with this Record.
     *
     * \return A non-owning pointer to the key data.
     */
    const SizedPointer key() const override {
        return SizedPointer(m_iterator->key().data(), m_iterator->key().size());
    }

private:
    std::shared_ptr<leveldb::Iterator> m_iterator;
};


AssetDatabase::AssetDatabase() :
    m_database(nullptr) {
}

AssetDatabase::~AssetDatabase() {
}

bool AssetDatabase::open(const char* path, bool createNew, int cacheSize) {
    leveldb::Options options;
    options.create_if_missing = createNew;
    options.error_if_exists = createNew;
    if (cacheSize > 0) {
        options.block_cache = leveldb::NewLRUCache(cacheSize);
    }

    leveldb::DB* database = nullptr;

    leveldb::Status status = leveldb::DB::Open(options, path, &database);
    if (!status.ok()) {
        LOG(ERROR) << "Failure opening or creating database at '" << path << "'. LevelDB status: " << status.ToString();
        return false;
    } else {
        LOG(INFO) << "Opened database file at '" << path << "'.";
    }

    m_database.reset(database);

    return true;
}

void AssetDatabase::close() {
    m_database.reset();
}

RecordPtr AssetDatabase::findAsset(uint64_t key) {
    std::array<char, kAssetKeySize> assetKey;
    makeAssetKey(key, assetKey.data());

    std::shared_ptr<leveldb::Iterator> iterator(m_database->NewIterator(leveldb::ReadOptions()));
    iterator->Seek(leveldb::Slice(assetKey.data(), kAssetKeySize));
    if (!iteratorMatch(iterator, assetKey.data(), kAssetKeySize)) {
        LOG(ERROR) << "Asset " << Asset::keyToString(key) << " not found in database.";
        return makeEmptyRecord();
    }

    uint64_t loadedKey = key;
    auto flatAsset = Data::GetFlatAsset(iterator->value().data());
    while (flatAsset->deprecatedBy()) {
        uint64_t deprecatedBy = flatAsset->deprecatedBy();
        LOG(INFO) << "Asset " << Asset::keyToString(key) << " deprecated by " << Asset::keyToString(deprecatedBy)
            << ", loading.";
        makeAssetKey(deprecatedBy, assetKey.data());
        iterator->Seek(leveldb::Slice(assetKey.data(), kAssetKeySize));
        if (!iteratorMatch(iterator, assetKey.data(), kAssetKeySize)) {
            LOG(ERROR) << "error loaded deprecating asset " << Asset::keyToString(deprecatedBy) << ".";
            return makeEmptyRecord();
        }
        flatAsset = Data::GetFlatAsset(iterator->value().data());
        loadedKey = deprecatedBy;
    }
    LOG(INFO) << "Loaded Asset " << Asset::keyToString(loadedKey) << " upon request to load original asset "
        << Asset::keyToString(key);
    return RecordPtr(new DatabaseRecord(iterator));
}

RecordPtr AssetDatabase::findNamedAsset(const std::string& name) {
    // Look up name entry, if any.
    std::string nameKey = kAssetNamePrefix + name;
    std::shared_ptr<leveldb::Iterator> iterator(m_database->NewIterator(leveldb::ReadOptions()));
    iterator->Seek(nameKey);
    if (!iteratorMatch(iterator, nameKey.data(), nameKey.size())) {
        LOG(WARNING) << "no named asset found under name " << name;
        return makeEmptyRecord();
    }

    uint64_t assetKey = 0;
    std::memcpy(&assetKey, iterator->value().data(), sizeof(uint64_t));
    LOG(INFO) << "found key " << Asset::keyToString(assetKey) << " under name lookup " << name;
    return findAsset(assetKey);
}

bool AssetDatabase::storeAsset(uint64_t key, const SizedPointer& assetData) {
    leveldb::WriteBatch batch;

    // First we parse the Asset data to extract the name, if any.
    const Data::FlatAsset* flatAsset = Data::GetFlatAsset(assetData.data());
    // This string, name has to live as long as the call to the database write(), so lives out here.
    std::string name;
    if (flatAsset->name() && flatAsset->name()->size() > 0) {
        name = kAssetNamePrefix + flatAsset->name()->str();
        LOG(INFO) << "adding name '" << flatAsset->name()->data() << "' lookup to asset " << Asset::keyToString(key);
        batch.Put(name, leveldb::Slice(reinterpret_cast<const char*>(&key), sizeof(uint64_t)));
    }

    // Add any list entries to the batch.
    char listKeys[kListEntryKeySize * kAssetMaxListEntries];
    uint64_t timeStamp = flatAsset->lists()->size() ?
        std::chrono::duration_cast<std::chrono::microseconds>(
        std::chrono::high_resolution_clock::now().time_since_epoch()).count() : 0;
    for (auto i = 0; i < flatAsset->lists()->size(); ++i) {
        char* listKey = listKeys + (i * kListEntryKeySize);
        listKey[0]  = kListEntry;
        std::memcpy(listKey + 1, flatAsset->lists()->data() + i, sizeof(uint64_t));
        std::memcpy(listKey + 9, &timeStamp, sizeof(uint64_t));
        std::memcpy(listKey + 17, &key, sizeof(uint64_t));
        LOG(INFO) << "adding asset " << Asset::keyToString(key) << " to list " << Asset::keyToString(
            flatAsset->lists()->data()[i]);
        batch.Put(leveldb::Slice(listKey, kListEntryKeySize), leveldb::Slice());
    }

    // Store actual Asset key/value pair.
    std::array<char, kAssetKeySize> assetKey;
    makeAssetKey(key, assetKey.data());
    batch.Put(leveldb::Slice(assetKey.data(), kAssetKeySize), leveldb::Slice(assetData.dataChar(), assetData.size()));

    auto status = m_database->Write(leveldb::WriteOptions(), &batch);
    if (status.ok()) {
        LOG(INFO) << "Asset store " << Asset::keyToString(key) << " success.";
    } else {
        LOG(ERROR) << "Failed to store Asset " << Asset::keyToString(key) << " in database, status: "
            << status.ToString();
    }

    return status.ok();
}

RecordPtr AssetDatabase::loadAssetDataChunk(uint64_t key, uint64_t chunk) {
    std::array<char, kAssetDataKeySize> assetDataKey;
    makeAssetDataKey(key, chunk, assetDataKey.data());
    std::shared_ptr<leveldb::Iterator> iterator(m_database->NewIterator(leveldb::ReadOptions()));
    iterator->Seek(leveldb::Slice(assetDataKey.data(), kAssetDataKeySize));
    if (!iteratorMatch(iterator, assetDataKey.data(), kAssetDataKeySize)) {
        LOG(ERROR) << "asset Data " << Asset::keyToString(key) << " chunk: " << chunk << " not found.";
    } else {
        LOG(INFO) << "Loaded Asset " << Asset::keyToString(key) << " chunk: " << chunk << ".";
    }

    return RecordPtr(new DatabaseRecord(iterator));
}

bool AssetDatabase::storeAssetDataChunk(uint64_t key, uint64_t chunk, const SizedPointer& flatAssetData) {
    std::array<char, kAssetDataKeySize> assetDataKey;
    makeAssetDataKey(key, chunk, assetDataKey.data());
    auto status = m_database->Put(leveldb::WriteOptions(), leveldb::Slice(assetDataKey.data(), kAssetDataKeySize),
        leveldb::Slice(flatAssetData.dataChar(), flatAssetData.size()));

    if (status.ok()) {
        LOG(INFO) << "Asset Data store " << Asset::keyToString(key) << " chunk " << chunk << " success.";
    } else {
        LOG(ERROR) << "Failed to store Asset Data " << Asset::keyToString(key) << " chunk " << chunk << ", status: "
            << status.ToString();
    }

    return status.ok();
}

bool AssetDatabase::storeList(uint64_t key, const SizedPointer& listEntry) {
    leveldb::WriteBatch batch;

    // Extract the name, if any, for storage in a lookup table.
    const Data::FlatList* flatList = Data::GetFlatList(listEntry.data());
    std::string name;
    if (flatList->name() && flatList->name()->size() > 0) {
        name = kListNamePrefix + flatList->name()->str();
        LOG(INFO) << "adding name '" << flatList->name()->data() << "' lookup to list " << Asset::keyToString(key);
        batch.Put(name, leveldb::Slice(reinterpret_cast<const char*>(&key), sizeof(uint64_t)));
    }

    // Make sentinel keys at beginning and end of the list, to allow seeking using an iterator to always valid entries.
    std::array<char, kListEntryKeySize> listBeginKey;
    listBeginKey[0] = kListEntry;
    std::memcpy(listBeginKey.data() + 1, &key, sizeof(uint64_t));
    std::memcpy(listBeginKey.data() + 9, &kBeginList, sizeof(uint64_t));
    std::memcpy(listBeginKey.data() + 17, &kBeginList, sizeof(uint64_t));
    batch.Put(leveldb::Slice(listBeginKey.data(), kListEntryKeySize), leveldb::Slice());

    std::array<char, kListEntryKeySize> listEndKey;
    listEndKey[0] = kListEntry;
    std::memcpy(listEndKey.data() + 1, &key, sizeof(uint64_t));
    std::memcpy(listEndKey.data() + 9, &kEndList, sizeof(uint64_t));
    std::memcpy(listEndKey.data() + 17, &kEndList, sizeof(uint64_t));
    batch.Put(leveldb::Slice(listEndKey.data(), kListEntryKeySize), leveldb::Slice());

    std::array<char, kListKeySize> listKey;
    makeListKey(key, listKey.data());
    batch.Put(leveldb::Slice(listKey.data(), kListKeySize), leveldb::Slice(listEntry.dataChar(), listEntry.size()));

    auto status = m_database->Write(leveldb::WriteOptions(), &batch);
    if (status.ok()) {
        LOG(INFO) << "List store " << Asset::keyToString(key) << " success.";
    } else {
        LOG(ERROR) << "Failed to store KeyList Data " << Asset::keyToString(key) << ", status: " << status.ToString();
    }

    return status.ok();
}

RecordPtr AssetDatabase::loadList(uint64_t key) {
    std::array<char, kListKeySize> listKey;
    makeListKey(key, listKey.data());
    std::shared_ptr<leveldb::Iterator> iterator(m_database->NewIterator(leveldb::ReadOptions()));
    iterator->Seek(leveldb::Slice(listKey.data(), kListKeySize));
    if (!iteratorMatch(iterator, listKey.data(), kListKeySize)) {
        LOG(ERROR) << "error retrieving list " << Asset::keyToString(key) << ".";
    } else {
        LOG(INFO) << "loaded list " << Asset::keyToString(key) << ".";
    }

    return RecordPtr(new DatabaseRecord(iterator));
}

RecordPtr AssetDatabase::findNamedList(const std::string& name) {
    std::string nameKey = kListNamePrefix + name;
    std::shared_ptr<leveldb::Iterator> iterator(m_database->NewIterator(leveldb::ReadOptions()));
    iterator->Seek(nameKey);
    if (!iteratorMatch(iterator, nameKey.data(), nameKey.size())) {
        LOG(WARNING) << "no named list found under name " << name;
        return makeEmptyRecord();
    }

    uint64_t listKey = 0;
    std::memcpy(&listKey, iterator->value().data(), sizeof(uint64_t));
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

    // Point the iterator at the fromToken position in the list.
    std::array<char, kListEntryKeySize> listEntryKey;
    listEntryKey[0] = kListEntry;
    std::memcpy(listEntryKey.data() + 1, &listKey, sizeof(uint64_t));
    std::memcpy(listEntryKey.data() + 9, &fromToken, sizeof(uint64_t));
    std::memcpy(listEntryKey.data() + 17, &kBeginList, sizeof(uint64_t));

    std::shared_ptr<leveldb::Iterator> iterator(m_database->NewIterator(leveldb::ReadOptions()));
    iterator->Seek(leveldb::Slice(listEntryKey.data(), kListEntryKeySize));
    if (!iterator->Valid()) {
        LOG(ERROR) << "error finding first element token: " << Asset::keyToString(fromToken) << " in list: "
            << Asset::keyToString(listKey);
        return 0;
    }

    size_t pairs = 0;
    while (pairs < maxPairs) {
        iterator->Next();
        if (!iterator->Valid()) {
            LOG(ERROR) << "error finding list " << Asset::keyToString(listKey) << " for iteration.";
            return 0;
        }
        // We only compare the first 9 bytes of the list key, to make sure the prefix and key match.
        if (iterator->key().size() != kListEntryKeySize ||
            std::memcmp(iterator->key().data(), listEntryKey.data(), 9) != 0) {
            LOG(INFO) << "walked off end of list " << Asset::keyToString(listKey) << " after " << pairs << " pairs.";
            break;
        }

        std::memcpy(listOut + (pairs * 2), iterator->key().data() + 9, sizeof(uint64_t) * 2);
        ++pairs;
    }

    return pairs;
}

}  // namespace Confab

