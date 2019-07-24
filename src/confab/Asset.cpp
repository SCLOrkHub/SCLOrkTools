#include "Asset.hpp"

#include "Constants.hpp"

#include <array>
#include <cstring>

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
std::string Asset::keyToString(uint64_t key) {
    std::array<char, 17> buf;
    snprintf(buf.data(), 17, "%" PRIx64, key);
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
    m_inlineData(nullptr),
    m_inlineDataSize(0),
    m_expiresOn(0),
    m_salt(0) {
}

Asset::Asset(const Data::FlatAsset* flatAsset, uint64_t key)  :
    m_type(static_cast<Asset::Type>(flatAsset->type())),
    m_author(flatAsset->author()),
    m_deprecatedBy(flatAsset->deprecatedBy()),
    m_deprecates(flatAsset->deprecates()),
    m_expiresOn(flatAsset->expiresOn()),
    m_salt(flatAsset->salt()) {
    if (flatAsset->key() != 0) {
        m_key = flatAsset->key();
    } else {
        m_key = key;
    }

    if (flatAsset->name()) {
        m_name = std::string(flatAsset->name()->c_str());
    }

    if (flatAsset->fileExtension()) {
        m_fileExtension = std::string(flatAsset->fileExtension()->c_str());
    }

    if (flatAsset->inlineData()) {
        m_inlineDataSize = flatAsset->inlineData()->size();
        m_inlineData.reset(new uint8_t[m_inlineDataSize]);
        std::memcpy(m_inlineData.get(), flatAsset->inlineData()->data(), m_inlineDataSize);
    } else {
        m_inlineData = nullptr;
        m_inlineDataSize = 0;
    }
}

void Asset::flatten(flatbuffers::FlatBufferBuilder& builder) {
    // Create leaf objects first.
    auto name = m_name != "" ? builder.CreateString(m_name.c_str()) : 0;
    auto fileExtension = m_fileExtension != "" ? builder.CreateString(m_fileExtension.c_str()) : 0;
    auto inlineData = m_inlineData != nullptr ? builder.CreateVector(m_inlineData.get(), m_inlineDataSize) : 0;

    // Can now create FlatAsset
    Data::FlatAssetBuilder assetBuilder(builder);
    assetBuilder.add_type(static_cast<Data::Type>(m_type));

    if (m_key) {
        assetBuilder.add_key(m_key);
    }
    if (!name.IsNull()) {
        assetBuilder.add_name(name);
    }
    if (!fileExtension.IsNull()) {
        assetBuilder.add_fileExtension(fileExtension);
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
    if (!inlineData.IsNull()) {
        assetBuilder.add_inlineData(inlineData);
    }
    if (m_expiresOn) {
        assetBuilder.add_expiresOn(m_expiresOn);
    }
    if (m_salt) {
        assetBuilder.add_salt(m_salt);
    }

    auto asset = assetBuilder.Finish();
    builder.Finish(asset);
}

}  // namespace Confab

