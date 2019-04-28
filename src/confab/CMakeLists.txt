include_directories(${CMAKE_CURRENT_SOURCE_DIR})

set(CONFAB_VERSION_MAJOR 0)
set(CONFAB_VERSION_MINOR 0)
set(CONFAB_VERSION_SUB 1)

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
    confab.cpp
)

add_executable(confab ${confab_src_files})

target_link_libraries(confab
    gflags::gflags
    glog::glog
    leveldb
    oscpack
    pistache_static
    sclorktools_common
    yaml-cpp
)
