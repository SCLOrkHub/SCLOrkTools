
#### leveldb
SET(LEVELDB_BUILD_TESTS OFF CACHE BOOL "build LevelDB's unit tests")
SET(LEVELDB_BUILD_BENCHMARKS OFF CACHE BOOL "build LevelDB's benchmarks")
SET(LEVELDB_INSTALL OFF CACHE BOOL "make LevelDB's install target")
add_subdirectory(leveldb)

#### oscpack
include_directories(oscpack_1_1_0)
add_library(oscpack STATIC "OSCPackBuild.cpp")
target_include_directories(oscpack INTERFACE oscpack_1_1_0)

#### pistache
SET(PISTACHE_BUILD_EXAMPLES OFF CACHE BOOL "build Pistache examples")
SET(PISTACHE_BUILD_TESTS OFF CACHE BOOL "build Pistache tests")
SET(PISTACHE_ENABLE_NETWORK_TESTS OFF CACHE BOOL "enable Pistache network tests")
SET(PISTACHE_USE_SSL OFF CACHE BOOL "enable ssl in Pistache")
add_subdirectory(pistache)

#### yaml-cpp
SET(YAML_CPP_BUILD_TESTS OFF CACHE BOOL "build yaml-cpp tests")
SET(YAML_CPP_BUILD_TOOLS OFF CACHE BOOL "build yaml-cpp parse tools")
SET(YAML_CPP_BUILD_CONTRIB OFF CACHE BOOL "build yaml-cpp contrib")
SET(YAML_CPP_INSTALL OFF CACHE BOOL "make yaml-cpp install target")
add_subdirectory(yaml-cpp)

