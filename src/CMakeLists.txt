set(CMAKE_CXX_STANDARD 17)

# Allow other projects to include the Common library headers.
include_directories(${CMAKE_CURRENT_LIST_DIR}/common/include)

set(CMAKE_CXX_FLAGS "-g")

add_subdirectory(common)
add_subdirectory(confab)

