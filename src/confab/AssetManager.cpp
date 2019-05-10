#include "AssetManager.hpp"

#include "AssetData.hpp"
#include "Constants.hpp"
#include "Database.hpp"

#include <glog/logging.h>
#include <xxhash.h>

#include <array>
#include <cstring>
#include <experimental/filesystem>
#include <fstream>
#include <limits>
#include <random>

namespace fs = std::experimental::filesystem;

namespace Confab {

AssetManager::AssetManager(std::shared_ptr<Database> database) :
    m_database(database) {
}

void AssetManager::addAssetFile(Asset::Type type, const std::string& filePath, std::function<void(uint64_t)> callback) {
    // First check that file exists and has a nonzero size.
    if (!fs::exists(filePath)) {
        DLOG(ERROR) << "addAssetFile failure, file: " << filePath << " does not exist.";
        return;
    }

    size_t fileSize = fs::file_size(filePath);
    if (fileSize == 0) {
        DLOG(ERROR) << "addAssetFile failure, file: " << filePath << " has zero size.";
        return;
    }

    uint64_t key = 0;
    Asset asset(type);

    // Since this Asset is added by file the extension is always known.
    asset.setFileExtension(fs::path(filePath).extension());

    bool fitsInSingleChunk = fileSize < kSingleChunkDataSize;

    // For small files that fit within a single chunk data size limit, load the file into memory now and process
    // as an inline asset.
    if (fitsInSingleChunk) {
        uint8_t* inlineData = asset.setInlineData(fileSize);
        CHECK(inlineData);

        std::ifstream inFile;
        inFile.open(filePath, std::ios::in | std::ios::binary);
        if (!inFile) {
            LOG(ERROR) << "addAssetFile encountered error opening file: " << filePath;
            callback(0);
            return;
        }

        inFile.read(reinterpret_cast<char*>(inlineData), kSingleChunkDataSize);
        size_t bytesRead = inFile.gcount();
        if (fileSize != bytesRead) {
            LOG(ERROR) << "addAssetFile got small file size of " << fileSize << " but only read " << bytesRead;
            callback(0);
            return;
        }

        // Add a random initial state to the hashing function, to help avoid collision on smaller-size Assets.
        std::random_device randomDevice;
        std::uniform_int_distribution<uint64_t> distribution(0, std::numeric_limits<uint64_t>::max());

        uint64_t salt = distribution(randomDevice);
        asset.setSalt(salt);

        key = computeHashMemory(reinterpret_cast<uint8_t*>(inlineData), fileSize, salt);
    } else {
        // Double-check size before computing hash of very large file.
        if (fileSize > kMaxAssetSize) {
            LOG(ERROR) << "Asset file " << filePath << " too large to store in database!";
            callback(0);
            return;
        }

        key = computeHashFile(filePath, fileSize);
    }

    std::array<uint8_t, kAssetDatabaseKeySize> assetDatabaseKey;
    SizedPointer flatKey(assetDatabaseKey.data(), sizeof(assetDatabaseKey));
    makeAssetDatabaseKey(key, flatKey);

    SizedPointer flatAsset = asset.flatten();

    bool result = m_database->store(flatKey, flatAsset);

    if (!result) {
        LOG(ERROR) << "Store of new Asset " << filePath << " failed.";
        callback(0);
        return;
    }


    // If this was a larger Asset, now need to process individual AssetData chunks and save them to database.
    if (!fitsInSingleChunk) {
        std::ifstream inFile;
        inFile.open(filePath, std::ios::in | std::ios::binary);
        if (!inFile) {
            LOG(ERROR) << "error opening file: " << filePath << " for chunk ingestion.";
            callback(0);
            return;
        }

        // Make the AssetData object which we will re-use for all but the last (smaller-sized) chunk.
        AssetData assetData;
        size_t chunkSize = std::min(fileSize, kDataChunkSize);
        size_t chunkNumber = 0;
        char* data = reinterpret_cast<char*>(assetData.setData(chunkSize));
        inFile.read(data, chunkSize);
        size_t bytesRead = inFile.gcount();
        if (bytesRead != chunkSize) {
            LOG(ERROR) << "error reading first chunk of Asset " << keyToString(key) << " file at " << filePath << ".";
            callback(0);
            return;
        }
        assetData.setHash(computeHashMemory(reinterpret_cast<uint8_t*>(data), chunkSize));
        const SizedPointer assetDataValue = assetData.flatten();

        std::array<uint8_t, kAssetDataDatabaseKeySize> assetDataKeyArray;
        SizedPointer assetDataKey(assetDataKeyArray.data(), kAssetDataDatabaseKeySize);
        makeAssetDataDatabaseKey(key, chunkNumber, assetDataKey);

        if (!m_database->store(assetDataKey, assetDataValue)) {
            LOG(ERROR) << "error writing first chunk of Asset " << keyToString(key) << " to database.";
            callback(0);
            return;
        }

        ++chunkNumber;
        size_t bytesRemaining = fileSize - chunkSize;

        while (inFile && bytesRemaining >= kDataChunkSize) {
            inFile.read(data, kDataChunkSize);
            bytesRead = inFile.gcount();
            if (bytesRead != kDataChunkSize) {
                LOG(ERROR) << "error reading chunk of Asset " << keyToString(key) << " file at " << filePath << ".";
                callback(0);
                return;
            }

            assetData.changeHash(computeHashMemory(reinterpret_cast<uint8_t*>(data), kDataChunkSize));
            makeAssetDataDatabaseKey(key, chunkNumber, assetDataKey);

            if (!m_database->store(assetDataKey, assetDataValue)) {
                LOG(ERROR) << "error writing chunk of Asset " << keyToString(key) << " to database.";
                callback(0);
                return;
            }

            ++chunkNumber;
            bytesRemaining -= kDataChunkSize;
        }

        if (bytesRemaining > 0) {
            AssetData lastChunkData;
            data = reinterpret_cast<char*>(lastChunkData.setData(bytesRemaining));
            inFile.read(data, bytesRemaining);
            bytesRead = inFile.gcount();

            if (bytesRead != bytesRemaining) {
                LOG(ERROR) << "error reading final chunk of Asset " << keyToString(key) << " file at " << filePath
                    << ".";
                callback(0);
                return;
            }

            lastChunkData.setHash(computeHashMemory(reinterpret_cast<uint8_t*>(data), bytesRemaining));
            makeAssetDataDatabaseKey(key, chunkNumber, assetDataKey);

            const SizedPointer lastDataValue = lastChunkData.flatten();
            if (!m_database->store(assetDataKey, lastDataValue)) {
                LOG(ERROR) << "error writing final chunk of Asset " << keyToString(key) << " to database.";
                callback(0);
                return;
            }
        }
    }

    LOG(INFO) << "Asset " << keyToString(key) << " from file " << filePath << " successfully added to database.";
    callback(key);
}

uint64_t AssetManager::computeHashFile(const std::string& filePath, size_t expectedSize, uint64_t salt) {
    CHECK_LT(kSingleChunkDataSize, expectedSize) << "Single-chunk hashes should be computed with computeHashMemory()";
    std::array<char, kDataChunkSize> fileChunk;

    std::ifstream inFile;
    inFile.open(filePath, std::ios::in | std::ios::binary);
    if (!inFile) {
        LOG(ERROR) << "error opening file: " << filePath << " for hash computation.";
        return 0;
    }

    XXH64_state_t* hashState = XXH64_createState();
    XXH64_reset(hashState, salt);

    inFile.read(fileChunk.data(), kDataChunkSize);
    size_t bytesRead = inFile.gcount();
    size_t totalBytesRead = bytesRead;

    while (inFile && bytesRead > 0) {
        XXH64_update(hashState, fileChunk.data(), bytesRead);
        inFile.read(fileChunk.data(), kDataChunkSize);
        bytesRead = inFile.gcount();
        totalBytesRead += bytesRead;
    }

    // Compute any remaining hash bytes.
    if (bytesRead > 0) {
        XXH64_update(hashState, fileChunk.data(), bytesRead);
    }

    uint64_t hash = XXH64_digest(hashState);
    XXH64_freeState(hashState);

    CHECK_NE(0, hash) << "hash can't be zero!";

    if (totalBytesRead != expectedSize) {
        LOG(ERROR) << "hash computed size " << totalBytesRead << " differs from expected size " << expectedSize;
        return 0;
    }

    return hash;
}

uint64_t AssetManager::computeHashMemory(const uint8_t* data, size_t size, uint64_t salt) {
    CHECK_GE(kDataChunkSize, size) << "Hashes larger than a single chunk should be computed with computeHashFile()";

    size_t hash = XXH64(data, size, salt);
    CHECK_NE(0, hash) << "hash can't be zero!";

    return hash;
}

void AssetManager::makeAssetDatabaseKey(uint64_t key, SizedPointer keyOut) {
    CHECK_LE(kAssetDatabaseKeySize, keyOut.size()) << "not enough room for an Asset database key.";

    keyOut.dataWritable()[0] = kAsset;
    std::memcpy(keyOut.dataWritable() + 1, reinterpret_cast<const uint8_t*>(key), sizeof(uint64_t));
}

void AssetManager::makeAssetDataDatabaseKey(uint64_t key, uint64_t chunkNumber, SizedPointer keyOut) {
    CHECK_LE(kAssetDataDatabaseKeySize, keyOut.size()) << "not enough room for an AssetData database key.";

    keyOut.dataWritable()[0] = kData;
    std::memcpy(keyOut.dataWritable() + 1, reinterpret_cast<const uint8_t*>(key), sizeof(uint64_t));
    std::memcpy(keyOut.dataWritable() + 9, reinterpret_cast<const uint8_t*>(chunkNumber), sizeof(uint64_t));
}

// static
std::string AssetManager::keyToString(uint64_t key) {
    std::array<char, 17> buf;
    snprintf(buf.data(), 17, "%" PRIx64, key);
    return std::string(buf.data());
}

}  // namespace Confab

