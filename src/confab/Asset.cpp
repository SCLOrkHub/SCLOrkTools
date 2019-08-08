#include "Asset.hpp"

#include "Constants.hpp"

#include "glog/logging.h"

#include <array>
#include <cstring>
#include <inttypes.h>

namespace Confab {

// static
Asset::Type Asset::typeStringToEnum(const std::string& assetType) {
    if (assetType == "snippet") {
        return kSnippet;
    } else if (assetType == "image") {
        return kImage;
    } else if (assetType == "yaml") {
        return kYAML;
    } else if (assetType == "sample") {
        return kSample;
    }
    return kInvalid;
}

// static
std::string Asset::enumToTypeString(Asset::Type type) {
    switch (type) {
        case kSnippet:
            return std::string("snippet");
        case kImage:
            return std::string("image");
        case kYAML:
            return std::string("yaml");
        case kSample:
            return std::string("sample");
    }

    return "invalid";
}

// static
std::string Asset::keyToString(uint64_t key) {
    std::array<char, 17> buf;
    snprintf(buf.data(), 17, "%016" PRIx64, key);
    return std::string(buf.data());
}

// static
uint64_t Asset::stringToKey(const std::string& keyString) {
    return strtoull(keyString.c_str(), nullptr, 16);
}

Asset::Asset(Asset::Type type) :
    m_type(type),
    m_key(0),
    m_author(0),
    m_deprecatedBy(0),
    m_deprecates(0),
    m_size(0),
    m_chunks(0),
    m_salt(0),
    m_inlineData(nullptr) {
}

Asset::Asset(const Data::FlatAsset* flatAsset)  :
    m_type(static_cast<Asset::Type>(flatAsset->type())),
    m_key(flatAsset->key()),
    m_author(flatAsset->author()),
    m_deprecatedBy(flatAsset->deprecatedBy()),
    m_deprecates(flatAsset->deprecates()),
    m_size(flatAsset->size()),
    m_chunks(flatAsset->chunks()),
    m_salt(flatAsset->salt()) {

    if (flatAsset->name()) {
        m_name = std::string(flatAsset->name()->c_str());
    }

    if (flatAsset->fileExtension()) {
        m_fileExtension = std::string(flatAsset->fileExtension()->c_str());
    }

    if (flatAsset->lists()) {
        std::copy(flatAsset->lists()->begin(), flatAsset->lists()->end(), m_lists.begin());
    }

    if (flatAsset->inlineData()) {
        m_inlineData.reset(new uint8_t[m_size]);
        std::memcpy(m_inlineData.get(), flatAsset->inlineData()->data(), m_size);
    }
}

void Asset::flatten(flatbuffers::FlatBufferBuilder& builder, const uint8_t* inlineData) {
    // Create leaf objects first.
    auto name = m_name != "" ? builder.CreateString(m_name) : 0;
    auto fileExtension = m_fileExtension != "" ? builder.CreateString(m_fileExtension) : 0;
    auto lists = m_lists.size() > 0 ? builder.CreateVector(m_lists) : 0;
    const uint8_t* serialInline = inlineData ? inlineData : m_inlineData.get();
    // These builder Create* calls do a byte-by-byte copy in a for loop of the source data into the builder.
    auto builderInline = serialInline != nullptr ? builder.CreateVector(serialInline, m_size) : 0;

    // Can now create FlatAsset, because all leaf objects are built.
    Data::FlatAssetBuilder assetBuilder(builder);
    assetBuilder.add_type(static_cast<Data::Type>(m_type));
    assetBuilder.add_key(m_key);
    assetBuilder.add_size(m_size);

    // AFAICT the flatbuffers library does not verify these optional fields against the default values, so to avoid
    // bloating the buffer we only set these fields when they differ from their default value of zero.
    if (!name.IsNull()) {
        assetBuilder.add_name(name);
    }
    if (!fileExtension.IsNull()) {
        assetBuilder.add_fileExtension(fileExtension);
    }
    if (!lists.IsNull()) {
        assetBuilder.add_lists(lists);
    }
    if (m_author) {
        assetBuilder.add_author(m_author);
    }
    if (m_deprecatedBy) {
        assetBuilder.add_deprecatedBy(m_deprecatedBy);
    }
    if (m_deprecates) {
        assetBuilder.add_deprecates(m_deprecates);
    }
    if (m_chunks) {
        assetBuilder.add_chunks(m_chunks);
    }
    if (m_salt) {
        assetBuilder.add_salt(m_salt);
    }
    if (!builderInline.IsNull()) {
        assetBuilder.add_inlineData(builderInline);
    }

    auto asset = assetBuilder.Finish();
    builder.Finish(asset);
}

size_t Asset::parseListIds(const std::string& listIds) {
    size_t idsParsed = 0;
    size_t offset = 0;
    while (offset < listIds.size()) {
        char* endPtr;
        uint64_t key = strtoull(listIds.c_str() + offset, &endPtr, 16);
        if (key == 0) {
            LOG(ERROR) << "error parsing listIds string around '" << listIds.c_str() + offset << "'";
            break;
        }
        m_lists.push_back(key);
        ++idsParsed;
        offset = (endPtr - listIds.c_str()) + 1;
    }
    return idsParsed;
}

}  // namespace Confab

