set(CMAKE_CXX_STANDARD 11)

# Allow inclusion of headers across project.
include_directories(${CMAKE_CURRENT_SOURCE_DIR})

set(confab_schema_files
    schemas/FlatAsset.fbs
    schemas/FlatAssetData.fbs
    schemas/FlatConfig.fbs
)

build_flatbuffers(
    "${confab_schema_files}"
    ""
    "confab_schemas"
    ""
    "${CMAKE_CURRENT_BINARY_DIR}/schemas"
    ""
    ""
)

set(CONFAB_VERSION_MAJOR 0)
set(CONFAB_VERSION_MINOR 0)
set(CONFAB_VERSION_PATCH 1)

set(CONFAB_DATABASE_VERSION 1)

configure_file(
    "${CMAKE_CURRENT_SOURCE_DIR}/ConfabVersion.hpp.in"
    "${CMAKE_CURRENT_BINARY_DIR}/ConfabVersion.hpp"
)

# To pick up the pre-processed ConfabVersion.hpp.
include_directories("${CMAKE_CURRENT_BINARY_DIR}")

set(confab_src_files
    Database.cpp
    Database.hpp
    OscHandler.cpp
    OscHandler.hpp
)

set(confab_test_files
    Database_test.cpp
)

add_library(confab_lib ${confab_src_files})

add_dependencies(confab_lib confab_schemas)

target_link_libraries(confab_lib PUBLIC
    flatbuffers
    gflags::gflags
    glog::glog
    leveldb
    oscpack
    pistache_static
    sclorktools_common
    yaml-cpp
)

add_executable(confab confab.cpp)

target_link_libraries(confab
    confab_lib
)

add_executable(test_confab test_confab.cpp ${confab_test_files})

target_link_libraries(test_confab
    confab_lib
    gtest
)

add_dependencies(test_confab confab_schemas)

