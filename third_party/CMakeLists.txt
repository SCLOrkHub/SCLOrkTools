set(BUILD_SHARED_LIBS OFF CACHE BOOL "build static libs instead" FORCE)
include(ExternalProject)

set(EXT_INSTALL_DIR "${PROJECT_BINARY_DIR}/install-ext" CACHE PATH "External binary dependencies install dir")

ExternalProject_Add(liblo
    PREFIX ext
    STEP_TARGETS install
    EXCLUDE_FROM_ALL ON
    GIT_REPOSITORY https://github.com/radarsat1/liblo.git
    GIT_TAG master
    GIT_PROGRESS ON
    UPDATE_COMMAND ""
    BUILD_BYPRODUCTS ${EXT_INSTALL_DIR}/lib/liblo.a
    CMAKE_ARGS -DWITH_TOOLS=OFF -DWITH_TESTS=OFF -DWITH_EXAMPLES=OFF -DWITH_CPP_TESTS=OFF -DWITH_STATIC=ON -DWITH_THREADING=ON -DCMAKE_INSTALL_PREFIX=${EXT_INSTALL_DIR}
    SOURCE_SUBDIR cmake
)

### base64
#ExternalProject_Add(base64
#    PREFIX "base64-git"
#    GIT_REPOSITORY "https://github.com/aklomp/base64"
#    GIT_TAG "master"
#    CONFIGURE_COMMAND ""
#    BUILD_IN_SOURCE 1
#    BUILD_COMMAND AVX2_CFLAGS=-mavx2 SSSE3_CFLAGS=-mssse3 SSE41_CFLAGS=-msse4.1 SSE42_CFLAGS=-msse4.2 AVX_CFLAGS=-mavx make lib/libbase64.o
#    INSTALL_COMMAND ""
#)
#set(BASE64_INCLUDE "${CMAKE_CURRENT_BINARY_DIR}/base64-git/src/base64/include" CACHE STRING "base64 include directory")
#set(BASE64_OBJECT "${CMAKE_CURRENT_BINARY_DIR}/base64-git/src/base64/lib/libbase64.o" CACHE STRING "base64 object file")
#add_library(base64_lib OBJECT IMPORTED GLOBAL)
#set_property(TARGET base64_lib PROPERTY IMPORTED_OBJECT ${BASE64_OBJECT})
#target_include_directories(base64_lib INTERFACE ${BASE64_INCLUDE})
#add_dependencies(base64_lib base64)

### flatbuffers
#add_subdirectory(flatbuffers)

#### gflags
set(GFLAGS_BUILD_STATIC_LIBS ON CACHE BOOL "build gflags as a static library")
set(GFLAGS_BUILD_gflags_LIB ON CACHE BOOL "build multithreaded gflags library")
add_subdirectory(gflags)

#### googletest
set(BUILD_GMOCK ON CACHE BOOL "include gmock in googletest build")
add_subdirectory(googletest)

#### leveldb
#set(LEVELDB_BUILD_TESTS OFF CACHE BOOL "build LevelDB's unit tests")
#set(LEVELDB_BUILD_BENCHMARKS OFF CACHE BOOL "build LevelDB's benchmarks")
#set(LEVELDB_INSTALL OFF CACHE BOOL "make LevelDB's install target")
#add_subdirectory(leveldb)

#### pistache
#set(PISTACHE_BUILD_EXAMPLES OFF CACHE BOOL "build Pistache examples")
#set(PISTACHE_BUILD_TESTS OFF CACHE BOOL "build Pistache tests")
#set(PISTACHE_ENABLE_NETWORK_TESTS OFF CACHE BOOL "enable Pistache network tests")
#set(PISTACHE_USE_SSL OFF CACHE BOOL "enable ssl in Pistache")
#add_subdirectory(pistache)

#### xxhash
#set(XXHASH_BUNDLED_MODE ON CACHE BOOL "build xxhash as part of another project")
#add_subdirectory(xxHash/cmake_unofficial)
#target_include_directories(xxhash PUBLIC xxHash)

#### yaml-cpp
#set(YAML_CPP_BUILD_TESTS OFF CACHE BOOL "build yaml-cpp tests")
#set(YAML_CPP_BUILD_TOOLS OFF CACHE BOOL "build yaml-cpp parse tools")
#set(YAML_CPP_BUILD_CONTRIB OFF CACHE BOOL "build yaml-cpp contrib")
#set(YAML_CPP_INSTALL OFF CACHE BOOL "make yaml-cpp install target")
#add_subdirectory(yaml-cpp)

#### fmt
add_subdirectory(fmt)

#### spdlog
set(SPDLOG_FMT_EXTERNAL ON CACHE BOOL "require spdlog to use our external fmt")
add_subdirectory(spdlog)

