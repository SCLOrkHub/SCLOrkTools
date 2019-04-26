include_directories(${CMAKE_CURRENT_SOURCE_DIR})

set(confab_src_files
    confab.cpp
)

add_executable(confab ${confab_src_files})

target_link_libraries(confab
    gflags::gflags
    leveldb
    oscpack
    pistache_static
    yaml-cpp
)
