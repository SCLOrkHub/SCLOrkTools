#include "Asset.hpp"

#include <cstring>
#include <gtest/gtest.h>

TEST(AssetTest, MinimalSerialization) {
    Confab::Asset asset(Confab::Asset::kSnippet);

    Confab::SizedPointer assetPointer = asset.flatten();

    const Confab::Asset flatAsset = Confab::Asset::LoadAsset(assetPointer, 0);
    EXPECT_EQ(asset.type(), flatAsset.type());
}

TEST(AssetTest, InlineData) {
    Confab::Asset asset(Confab::Asset::kSnippet);

    const std::string testSnippet("Ndef(\\a, { SinOsc.ar(43 * 7) }).play;");
    uint8_t* inlineData = asset.setInlineData(testSnippet.size());
    std::memcpy(inlineData, testSnippet.c_str(), testSnippet.size());

    Confab::SizedPointer assetPointer = asset.flatten();

    const Confab::Asset flatAsset = Confab::Asset::LoadAsset(assetPointer, 0);
    EXPECT_EQ(asset.type(), flatAsset.type());
    ASSERT_EQ(testSnippet.size(), asset.inlineDataSize());
    EXPECT_EQ(std::memcmp(testSnippet.c_str(), *flatAsset.inlineData(), testSnippet.size()), 0);
}
