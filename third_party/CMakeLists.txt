set(BUILD_SHARED_LIBS OFF CACHE BOOL "build static libs instead" FORCE)

### base64
include(ExternalProject)
ExternalProject_Add(base64
    PREFIX "base64-git"
    GIT_REPOSITORY "https://github.com/aklomp/base64"
    GIT_TAG "master"
    CONFIGURE_COMMAND ""
    BUILD_IN_SOURCE 1
    BUILD_COMMAND AVX2_CFLAGS=-mavx2 SSSE3_CFLAGS=-mssse3 SSE41_CFLAGS=-msse4.1 SSE42_CFLAGS=-msse4.2 AVX_CFLAGS=-mavx make -j1 lib/libbase64.o
    INSTALL_COMMAND ""
)
set(BASE64_INCLUDE "${CMAKE_CURRENT_BINARY_DIR}/base64-git/src/base64/include" CACHE STRING "base64 include directory")
set(BASE64_OBJECT "${CMAKE_CURRENT_BINARY_DIR}/base64-git/src/base64/lib/libbase64.o")

### flatbuffers
add_subdirectory(flatbuffers)

#### gflags
set(GFLAGS_BUILD_STATIC_LIBS ON CACHE BOOL "build gflags as a static library")
set(GFLAGS_BUILD_gflags_LIB ON CACHE BOOL "build multithreaded gflags library")
add_subdirectory(gflags)

#### glog
# Can't seem to reliably connect glog to gflags so programs can make their own logging flags and/or configure logging
# manually.
set(WITH_GFLAGS OFF CACHE BOOL "use gflags in glog")
add_subdirectory(glog)

#### googletest
set(BUILD_GMOCK ON CACHE BOOL "include gmock in googletest build")
add_subdirectory(googletest)

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

#### xxhash
set(XXHASH_BUNDLED_MODE ON CACHE BOOL "build xxhash as part of another project")
add_subdirectory(xxHash/cmake_unofficial)
target_include_directories(xxhash PUBLIC xxHash)

#### yaml-cpp
set(YAML_CPP_BUILD_TESTS OFF CACHE BOOL "build yaml-cpp tests")
set(YAML_CPP_BUILD_TOOLS OFF CACHE BOOL "build yaml-cpp parse tools")
set(YAML_CPP_BUILD_CONTRIB OFF CACHE BOOL "build yaml-cpp contrib")
set(YAML_CPP_INSTALL OFF CACHE BOOL "make yaml-cpp install target")
add_subdirectory(yaml-cpp)

