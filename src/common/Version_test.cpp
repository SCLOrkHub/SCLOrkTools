#include "common/Version.hpp"

#include <gtest/gtest.h>

#include <string>

TEST(VersionTest, Constructor) {
    Common::Version v1(1, 2, 3);
    EXPECT_EQ(1, v1.major());
    EXPECT_EQ(2, v1.minor());
    EXPECT_EQ(3, v1.sub());

    Common::Version v2(9, -3, 6);
    EXPECT_EQ(9, v2.major());
    EXPECT_EQ(0, v2.minor());
    EXPECT_EQ(6, v2.sub());
}

TEST(VersionTest, CopyConstructor) {
    Common::Version v1(7, 8, 9);
    Common::Version v2(v1);

    EXPECT_EQ(7, v1.major());
    EXPECT_EQ(8, v1.minor());
    EXPECT_EQ(9, v1.sub());

    EXPECT_EQ(7, v2.major());
    EXPECT_EQ(8, v2.minor());
    EXPECT_EQ(9, v2.sub());
}

TEST(VersionTest, Assignment) {
    Common::Version v1(-9, 0, -1);
    Common::Version v2(11, 12, 13);
    Common::Version v3(8, -7, 1);
    v1 = v2 = v3;

    EXPECT_EQ(8, v3.major());
    EXPECT_EQ(0, v3.minor());
    EXPECT_EQ(1, v3.sub());

    EXPECT_EQ(8, v2.major());
    EXPECT_EQ(0, v2.minor());
    EXPECT_EQ(1, v2.sub());

    EXPECT_EQ(8, v1.major());
    EXPECT_EQ(0, v1.minor());
    EXPECT_EQ(1, v1.sub());
}

TEST(VersionTest, ToString) {
    Common::Version v1(4, 10, 111);
    EXPECT_EQ(std::string("4.10.111"), v1.toString());

    Common::Version v2(8, 0, 0);
    EXPECT_EQ(std::string("8.0.0"), v2.toString());
}

TEST(VersionTest, ComparisonLessThan) {
    Common::Version v0(10, 10, 11);
    Common::Version v1(10, 10, 11);

    EXPECT_FALSE(v0 < v1);
    EXPECT_FALSE(v1 < v0);
    EXPECT_FALSE(v0 < v0);
    EXPECT_FALSE(v1 < v1);

    Common::Version v2(10, 10, 9);

    EXPECT_TRUE(v2 < v1);
    EXPECT_FALSE(v1 < v2);
    EXPECT_FALSE(v2 < v2);

    Common::Version v3(10, 1, 11);

    EXPECT_TRUE(v3 < v1);
    EXPECT_TRUE(v3 < v2);
    EXPECT_FALSE(v1 < v3);
    EXPECT_FALSE(v2 < v3);
    EXPECT_FALSE(v3 < v3);

    Common::Version v4(9, 20, 25);

    EXPECT_TRUE(v4 < v3);
    EXPECT_TRUE(v4 < v2);
    EXPECT_TRUE(v4 < v1);
    EXPECT_FALSE(v3 < v4);
    EXPECT_FALSE(v2 < v4);
    EXPECT_FALSE(v1 < v4);
}

TEST(VersionTest, ComparisonEquality) {
    Common::Version v1(1, 2, 3);
    Common::Version v2(1, 2, 3);

    EXPECT_TRUE(v1 == v2);
    EXPECT_TRUE(v2 == v1);
    EXPECT_TRUE(v1 == v1);
    EXPECT_TRUE(v2 == v2);

    Common::Version v3(1, 2, 4);

    EXPECT_FALSE(v3 == v1);
    EXPECT_FALSE(v3 == v2);
    EXPECT_TRUE(v3 == v3);

    Common::Version v4(1, 3, 3);

    EXPECT_FALSE(v4 == v1);
    EXPECT_FALSE(v4 == v2);
    EXPECT_FALSE(v4 == v3);
    EXPECT_TRUE(v4 == v4);

    Common::Version v5(8, 2, 3);

    EXPECT_FALSE(v5 == v1);
    EXPECT_FALSE(v5 == v2);
    EXPECT_FALSE(v5 == v3);
    EXPECT_FALSE(v5 == v4);
    EXPECT_TRUE(v5 == v5);

    Common::Version v6(127, 127, 127);

    EXPECT_FALSE(v6 == v1);
    EXPECT_FALSE(v6 == v2);
    EXPECT_FALSE(v6 == v3);
    EXPECT_FALSE(v6 == v4);
    EXPECT_FALSE(v6 == v5);
    EXPECT_TRUE(v6 == v6);
}

TEST(VersionTest, ComparisonInequality) {
    Common::Version v1(6, 2, 9);
    Common::Version v2(6, 2, 9);

    EXPECT_FALSE(v1 != v2);
    EXPECT_FALSE(v2 != v1);
    EXPECT_FALSE(v1 != v1);
    EXPECT_FALSE(v2 != v2);

    Common::Version v3(7, 2, 9);

    EXPECT_TRUE(v3 != v1);
    EXPECT_TRUE(v3 != v2);
    EXPECT_FALSE(v3 != v3);

    Common::Version v4(6, 3, 9);

    EXPECT_TRUE(v4 != v1);
    EXPECT_TRUE(v4 != v2);
    EXPECT_TRUE(v4 != v3);
    EXPECT_FALSE(v4 != v4);

    Common::Version v5(6, 2, 3);

    EXPECT_TRUE(v5 != v1);
    EXPECT_TRUE(v5 != v2);
    EXPECT_TRUE(v5 != v3);
    EXPECT_TRUE(v5 != v4);
    EXPECT_FALSE(v5 != v5);

    Common::Version v6(0, 0, 0);

    EXPECT_TRUE(v6 != v1);
    EXPECT_TRUE(v6 != v2);
    EXPECT_TRUE(v6 != v3);
    EXPECT_TRUE(v6 != v4);
    EXPECT_TRUE(v6 != v5);
    EXPECT_FALSE(v6 != v6);
}

TEST(VersionTest, ComparisonGreaterThan) {
    Common::Version v0(9, 7, 5);
    Common::Version v1(9, 7, 5);

    EXPECT_FALSE(v0 > v1);
    EXPECT_FALSE(v1 > v0);
    EXPECT_FALSE(v0 > v0);
    EXPECT_FALSE(v1 > v1);

    Common::Version v2(9, 7, 8);

    EXPECT_TRUE(v2 > v1);
    EXPECT_FALSE(v1 > v2);
    EXPECT_FALSE(v2 > v2);

    Common::Version v3(9, 9, 5);

    EXPECT_TRUE(v3 > v1);
    EXPECT_TRUE(v3 > v2);
    EXPECT_FALSE(v1 > v3);
    EXPECT_FALSE(v2 > v3);
    EXPECT_FALSE(v3 > v3);

    Common::Version v4(11, 7, 5);

    EXPECT_TRUE(v4 > v3);
    EXPECT_TRUE(v4 > v2);
    EXPECT_TRUE(v4 > v1);
    EXPECT_FALSE(v3 > v4);
    EXPECT_FALSE(v2 > v4);
    EXPECT_FALSE(v1 > v4);
    EXPECT_FALSE(v4 > v4);
}

TEST(VersionTest, ComparisonLessThanOrEqualTo) {
    Common::Version v0(1, 1, 7);
    Common::Version v1(1, 1, 7);

    EXPECT_TRUE(v0 <= v1);
    EXPECT_TRUE(v1 <= v0);
    EXPECT_TRUE(v0 <= v0);
    EXPECT_TRUE(v1 <= v1);

    Common::Version v2(1, 1, 6);

    EXPECT_TRUE(v2 <= v1);
    EXPECT_FALSE(v1 <= v2);
    EXPECT_TRUE(v2 <= v2);

    Common::Version v3(1, 0, 7);

    EXPECT_TRUE(v3 <= v1);
    EXPECT_TRUE(v3 <= v2);
    EXPECT_FALSE(v1 <= v3);
    EXPECT_FALSE(v2 <= v3);
    EXPECT_TRUE(v3 <= v3);

    Common::Version v4(0, 1, 7);

    EXPECT_TRUE(v4 <= v3);
    EXPECT_TRUE(v4 <= v2);
    EXPECT_TRUE(v4 <= v1);
    EXPECT_FALSE(v3 <= v4);
    EXPECT_FALSE(v2 <= v4);
    EXPECT_FALSE(v1 <= v4);
    EXPECT_TRUE(v4 <= v4);
}

TEST(VersionTest, ComparisonGreaterThanOrEqualTo) {
    Common::Version v0(100, 1000, 10000);
    Common::Version v1(100, 1000, 10000);

    EXPECT_TRUE(v0 >= v1);
    EXPECT_TRUE(v1 >= v0);
    EXPECT_TRUE(v0 >= v0);
    EXPECT_TRUE(v1 >= v1);

    Common::Version v2(100, 1000, 10001);

    EXPECT_TRUE(v2 >= v1);
    EXPECT_FALSE(v1 >= v2);
    EXPECT_TRUE(v2 >= v2);

    Common::Version v3(100, 1001, 10000);

    EXPECT_TRUE(v3 >= v1);
    EXPECT_TRUE(v3 >= v2);
    EXPECT_FALSE(v1 >= v3);
    EXPECT_FALSE(v2 >= v3);
    EXPECT_TRUE(v3 >= v3);

    Common::Version v4(101, 1000, 10000);

    EXPECT_TRUE(v4 >= v3);
    EXPECT_TRUE(v4 >= v2);
    EXPECT_TRUE(v4 >= v1);
    EXPECT_FALSE(v3 >= v4);
    EXPECT_FALSE(v2 >= v4);
    EXPECT_FALSE(v1 >= v4);
    EXPECT_TRUE(v4 >= v4);
}

