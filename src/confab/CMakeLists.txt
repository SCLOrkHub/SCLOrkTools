set(CMAKE_CXX_STANDARD 11)

# Allow inclusion of headers across project.
include_directories(${CMAKE_CURRENT_SOURCE_DIR})

set(confab_schema_files
    schemas/Asset.fbs
    schemas/Config.fbs
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
)

set(confab_test_files
    Database_test.cpp

    test_confab.cpp
)

add_executable(confab confab.cpp ${confab_src_files})

target_link_libraries(confab PRIVATE
    flatbuffers
    gflags::gflags
    glog::glog
    leveldb
    oscpack
    pistache_static
    sclorktools_common
    yaml-cpp
)

add_dependencies(confab confab_schemas)

add_executable(test_confab ${confab_src_files} ${confab_test_files})

target_link_libraries(test_confab PRIVATE
    flatbuffers
    glog
    gmock
    leveldb
    sclorktools_common
)

add_dependencies(test_confab confab_schemas)
