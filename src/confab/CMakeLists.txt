set(CMAKE_CXX_STANDARD 17)

# Allow inclusion of headers across project.
include_directories("${CMAKE_CURRENT_SOURCE_DIR}")

set(confab_schema_files
    schemas/FlatAsset.fbs
    schemas/FlatAssetData.fbs
    schemas/FlatConfig.fbs
    schemas/FlatList.fbs
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

configure_file(
    "${CMAKE_CURRENT_SOURCE_DIR}/Constants.hpp.in"
    "${CMAKE_CURRENT_BINARY_DIR}/Constants.hpp"
)

# To pick up the pre-processed ConfabVersion.hpp.
include_directories("${CMAKE_CURRENT_BINARY_DIR}")

###
# confab common files
set(confab_common_src_files
    Asset.cpp
    Asset.hpp
    AssetDatabase.cpp
    AssetDatabase.hpp
    ConfabCommon.cpp
    ConfabCommon.hpp
    Config.cpp
    Config.hpp
    Record.hpp
    State.cpp
    State.hpp
    SizedPointer.hpp
)

# Ugly hack to include the base64 object file but this seems to be the only
# way to get this thing to link.
add_library(confab_common ${confab_common_src_files} ${BASE64_OBJECT})

add_dependencies(confab_common confab_schemas)

target_link_libraries(confab_common PUBLIC
    base64_lib
    flatbuffers
    gflags::gflags
    glog::glog
    leveldb
    pistache
    pistache_static
    sclorktools_common
    stdc++fs
    xxhash
)

###
# confab client
add_executable(confab
    confab.cpp
    CacheManager.cpp
    CacheManager.hpp
    HttpClient.cpp
    HttpClient.hpp
    OscHandler.cpp
    OscHandler.hpp
)

target_link_libraries(confab
    confab_common
    oscpack
)

###
# confab server
add_executable(confab-server
    confab-server.cpp
    HttpEndpoint.cpp
    HttpEndpoint.hpp
)

target_link_libraries(confab-server
    confab_common
)

##
# confab test
set(confab_test_files
    Asset_test.cpp
)

add_executable(test_confab test_confab.cpp ${confab_test_files})

target_link_libraries(test_confab
    confab_common
    gtest
)

add_dependencies(test_confab confab_schemas)

