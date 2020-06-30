# Specify project version.
cmake_policy(SET CMP0048 NEW)

project(SCLOrkTools
        VERSION 0.0.19)

cmake_minimum_required(VERSION 3.9)

add_subdirectory(third_party)
add_subdirectory(src)

