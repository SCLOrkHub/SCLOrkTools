# Specify project version.
cmake_policy(SET CMP0048 NEW)

project(SCLOrkTools
        VERSION 0.0.1)

cmake_minimum_required(VERSION 3.9)

option(BUILD_DOC "Build C++ documentation with Doxygen" ON)
option(BUILD_OFFLINE "Build offline, forces cmake to not check for updates to external projects" OFF)

if (${BUILD_OFFLINE})
    set_property(DIRECTORY .
                 PROPERTY EP_UPDATE_DISCONNECTED 1)
endif()

add_subdirectory(third_party)
add_subdirectory(src)


