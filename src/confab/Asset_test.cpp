#include "Asset.hpp"

#include <cstring>
#include <gtest/gtest.h>

TEST(AssetTest, MinimalSerialization) {
    Confab::Asset asset(Confab::Asset::kSnippet);

    flatbuffers::FlatBufferBuilder builder;
    asset.flatten(builder);

    auto flatAsset = Confab::Data::GetFlatAsset(builder.GetBufferPointer());

    Confab::Asset testAsset(flatAsset);

    EXPECT_EQ(asset.type(), testAsset.type());
    EXPECT_EQ(0, testAsset.key());
    EXPECT_EQ("", testAsset.name());
    EXPECT_EQ("", testAsset.fileExtension());
    EXPECT_EQ(0, testAsset.author());
    EXPECT_EQ(0, testAsset.deprecatedBy());
    EXPECT_EQ(0, testAsset.deprecates());
    EXPECT_EQ(0, testAsset.inlineDataSize());
    EXPECT_EQ(nullptr, testAsset.inlineData());
    EXPECT_EQ(0, testAsset.expiresOn());
    EXPECT_EQ(0, testAsset.salt());
}

TEST(AssetTest, AllFieldsSet) {
    Confab::Asset asset(Confab::Asset::kSnippet);
    asset.setKey(0xdeadbeef1bad1dea);
    asset.setName("test asset not for serialization");
    asset.setFileExtension("scd");
    asset.setAuthor(1);
    asset.setDeprecatedBy(2);
    asset.setDeprecates(3);
    uint8_t* inlineData = asset.setInlineData(100);
    for (auto i = 0; i < 100; ++i) {
        inlineData[i] = 99 - i;
    }
    asset.setExpiresOn(25);
    asset.setSalt(12345678);

    flatbuffers::FlatBufferBuilder builder;
    asset.flatten(builder);

    auto flatAsset = Confab::Data::GetFlatAsset(builder.GetBufferPointer());

    Confab::Asset testAsset(flatAsset);

    EXPECT_EQ(asset.type(), testAsset.type());
    EXPECT_EQ(asset.key(), testAsset.key());
    EXPECT_EQ(asset.name(), testAsset.name());
    EXPECT_EQ(asset.fileExtension(), testAsset.fileExtension());
    EXPECT_EQ(asset.author(), testAsset.author());
    EXPECT_EQ(asset.deprecatedBy(), testAsset.deprecatedBy());
    EXPECT_EQ(asset.deprecates(), testAsset.deprecates());
    ASSERT_EQ(asset.inlineDataSize(), testAsset.inlineDataSize());
    EXPECT_EQ(std::memcmp(asset.inlineData(), testAsset.inlineData(), asset.inlineDataSize()), 0);
    EXPECT_EQ(asset.expiresOn(), testAsset.expiresOn());
    EXPECT_EQ(asset.salt(), testAsset.salt());
}
