#### gflags
set(GFLAGS_BUILD_STATIC_LIBS ON CACHE BOOL "build gflags as a static library")
set(GFLAGS_BUILD_gflags_LIB ON CACHE BOOL "build multithreaded gflags library")
set(GFLAGS_BUILD_gflags_nothreads_LIB ON CACHE BOOL "build single-threaded gflags library")

add_subdirectory(gflags)

#### glog
add_subdirectory(glog)

#### leveldb
set(LEVELDB_BUILD_TESTS OFF CACHE BOOL "build LevelDB's unit tests")
set(LEVELDB_BUILD_BENCHMARKS OFF CACHE BOOL "build LevelDB's benchmarks")
set(LEVELDB_INSTALL OFF CACHE BOOL "make LevelDB's install target")
add_subdirectory(leveldb)

#### oscpack
include_directories(oscpack_1_1_0)
add_library(oscpack STATIC "OSCPackBuild.cpp")
target_include_directories(oscpack INTERFACE oscpack_1_1_0)

#### pistache
set(PISTACHE_BUILD_EXAMPLES OFF CACHE BOOL "build Pistache examples")
set(PISTACHE_BUILD_TESTS OFF CACHE BOOL "build Pistache tests")
set(PISTACHE_ENABLE_NETWORK_TESTS OFF CACHE BOOL "enable Pistache network tests")
set(PISTACHE_USE_SSL OFF CACHE BOOL "enable ssl in Pistache")
add_subdirectory(pistache)

#### yaml-cpp
set(YAML_CPP_BUILD_TESTS OFF CACHE BOOL "build yaml-cpp tests")
set(YAML_CPP_BUILD_TOOLS OFF CACHE BOOL "build yaml-cpp parse tools")
set(YAML_CPP_BUILD_CONTRIB OFF CACHE BOOL "build yaml-cpp contrib")
set(YAML_CPP_INSTALL OFF CACHE BOOL "make yaml-cpp install target")
add_subdirectory(yaml-cpp)

