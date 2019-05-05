#include "Database.hpp"

#include <gtest/gtest.h>
#include <leveldb/db.h>

TEST(DatabaseTest, makeAssetKey) {
    auto key1 = Confab::Database::makeAssetKey(0x0102030405060708);
    EXPECT_EQ(Confab::Database::KeyPrefix::kAsset, key1[0]);
    EXPECT_EQ(0x08, key1[1]);
    EXPECT_EQ(0x07, key1[2]);
    EXPECT_EQ(0x06, key1[3]);
    EXPECT_EQ(0x05, key1[4]);
    EXPECT_EQ(0x04, key1[5]);
    EXPECT_EQ(0x03, key1[6]);
    EXPECT_EQ(0x02, key1[7]);
    EXPECT_EQ(0x01, key1[8]);
}

TEST(DatabaseTest, makeDataKey) {
    auto key1 = Confab::Database::makeDataKey(0x7060504030201000);
    EXPECT_EQ(Confab::Database::KeyPrefix::kData, key1[0]);
    EXPECT_EQ(0x00, key1[1]);
    EXPECT_EQ(0x10, key1[2]);
    EXPECT_EQ(0x20, key1[3]);
    EXPECT_EQ(0x30, key1[4]);
    EXPECT_EQ(0x40, key1[5]);
    EXPECT_EQ(0x50, key1[6]);
    EXPECT_EQ(0x60, key1[7]);
    EXPECT_EQ(0x70, key1[8]);
}

