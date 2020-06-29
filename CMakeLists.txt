# Specify project version.
cmake_policy(SET CMP0048 NEW)

project(SCLOrkTools
        VERSION 0.0.18)

cmake_minimum_required(VERSION 3.9)

option(BUILD_DOC "Build C++ documentation with Doxygen" ON)

add_subdirectory(third_party)
add_subdirectory(src)

