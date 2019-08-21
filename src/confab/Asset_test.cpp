#include "Asset.hpp"

#include <cstring>
#include <gtest/gtest.h>

TEST(AssetTest, MinimalSerialization) {
    Confab::Asset asset(Confab::Asset::kSnippet);

    flatbuffers::FlatBufferBuilder builder;
    asset.flatten(builder);

    auto verifier = flatbuffers::Verifier(builder.GetBufferPointer(), builder.GetSize());
    ASSERT_TRUE(Confab::Data::VerifyFlatAssetBuffer(verifier));

    auto flatAsset = Confab::Data::GetFlatAsset(builder.GetBufferPointer());

    Confab::Asset testAsset(flatAsset);

    EXPECT_EQ(asset.type(), testAsset.type());
    EXPECT_EQ(0, testAsset.key());
    EXPECT_EQ("", testAsset.name());
    EXPECT_EQ("", testAsset.fileExtension());
    EXPECT_EQ(0, testAsset.size());
    EXPECT_EQ(0, testAsset.chunks());
    EXPECT_EQ(0, testAsset.salt());
    EXPECT_EQ(nullptr, testAsset.inlineData());
}

TEST(AssetTest, AllFieldsSet) {
    Confab::Asset asset(Confab::Asset::kSnippet);
    asset.setKey(0xdeadbeef1bad1dea);
    asset.setName("test asset not for serialization");
    asset.setFileExtension("scd");
    asset.setSalt(12345678);
    uint8_t* inlineData = asset.setInlineData(100);
    for (auto i = 0; i < 100; ++i) {
        inlineData[i] = 99 - i;
    }
    asset.setChunks(25);

    flatbuffers::FlatBufferBuilder builder;
    asset.flatten(builder);

    auto verifier = flatbuffers::Verifier(builder.GetBufferPointer(), builder.GetSize());
    ASSERT_TRUE(Confab::Data::VerifyFlatAssetBuffer(verifier));

    auto flatAsset = Confab::Data::GetFlatAsset(builder.GetBufferPointer());

    Confab::Asset testAsset(flatAsset);

    EXPECT_EQ(asset.type(), testAsset.type());
    EXPECT_EQ(asset.key(), testAsset.key());
    EXPECT_EQ(asset.name(), testAsset.name());
    EXPECT_EQ(asset.fileExtension(), testAsset.fileExtension());
    EXPECT_EQ(asset.salt(), testAsset.salt());
    EXPECT_EQ(asset.chunks(), testAsset.chunks());
    ASSERT_EQ(asset.size(), testAsset.size());
    EXPECT_EQ(std::memcmp(asset.inlineData(), testAsset.inlineData(), asset.size()), 0);
}

TEST(AssetTest, InlineDataOverride) {
    std::string testInline = "blah blah blah";
    Confab::Asset asset(Confab::Asset::kSnippet);
    asset.setKey(1);
    asset.setSize(testInline.size());

    flatbuffers::FlatBufferBuilder builder;
    asset.flatten(builder, reinterpret_cast<const uint8_t*>(testInline.c_str()));

    auto verifier = flatbuffers::Verifier(builder.GetBufferPointer(), builder.GetSize());
    ASSERT_TRUE(Confab::Data::VerifyFlatAssetBuffer(verifier));

    auto flatAsset = Confab::Data::GetFlatAsset(builder.GetBufferPointer());

    Confab::Asset testAsset(flatAsset);

    EXPECT_EQ(std::memcmp(testInline.c_str(), testAsset.inlineData(), asset.size()), 0);
}
