project(SCLOrkTools)

cmake_minimum_required(VERSION 2.6)

option(BUILD_DOC "Build C++ documentation with Doxygen" ON)

add_subdirectory(third_party)
add_subdirectory(src)

