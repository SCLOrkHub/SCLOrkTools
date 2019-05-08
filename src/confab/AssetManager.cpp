#include "AssetManager.hpp"

#include "Constants.hpp"
#include "Database.hpp"

#include <glog/logging.h>
#include <xxhash.h>

#include <array>
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

    // For small files that fit within a single chunk data size limit, load the file into memory now and process
    // as an inline asset.
    if (fileSize < kSingleChunkDataSize) {
        uint8_t* inlineData = asset.setInlineData(fileSize);
        CHECK(inlineData);

        std::ifstream inFile;
        inFile.open(filePath, std::ios::in | std::ios::binary);
        if (!inFile) {
            LOG(ERROR) << "addAssetFile encountered error opening file: " << filePath;
            return;
        }

        inFile.read(reinterpret_cast<char*>(inlineData), kSingleChunkDataSize);
        size_t bytesRead = inFile.gcount();
        if (fileSize != bytesRead) {
            LOG(ERROR) << "addAssetFile got small file size of " << fileSize << " but only read " << bytesRead;
            return;
        }

        std::random_device randomDevice;
        std::uniform_int_distribution<uint64_t> distribution(0, std::numeric_limits<uint64_t>::max());

        uint64_t salt = distribution(randomDevice);
        asset.setSalt(salt);

        key = computeHashMemory(reinterpret_cast<uint8_t*>(inlineData), fileSize, salt);
    } else {
        key = computeHashFile(filePath, fileSize, 0);
    }
}

uint64_t AssetManager::computeHashFile(const std::string& filePath, size_t expectedSize, uint64_t salt) {
    DCHECK_LT(kSingleChunkDataSize, expectedSize) << "Single-chunk hashes should be computed with computeHashMemory()";
    std::array<char, kChunkSize> fileChunk;

    std::ifstream inFile;
    inFile.open(filePath, std::ios::in | std::ios::binary);
    if (!inFile) {
        LOG(ERROR) << "error opening file: " << filePath << " for hash computation.";
        return 0;
    }

    XXH64_state_t* hashState = XXH64_createState();
    XXH64_reset(hashState, salt);

    inFile.read(fileChunk.data(), kChunkSize);
    size_t bytesRead = inFile.gcount();
    size_t totalBytesRead = bytesRead;

    while (inFile && bytesRead > 0) {
        XXH64_update(hashState, fileChunk.data(), bytesRead);
        inFile.read(fileChunk.data(), kChunkSize);
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
    DCHECK_GE(kChunkSize, size) << "Hashes larger than a single chunk should be computed with computeHashFile()";

    size_t hash = XXH64(data, size, salt);
    CHECK_NE(0, hash) << "hash can't be zero!";

    return hash;
}

}  // namespace Confab

