set(common_src_files
    Version.cpp

    include/common/Version.hpp
)

set(common_test_files
    Version_test.cpp

    test_common.cpp
)


include_directories("${CMAKE_CURRENT_SOURCE_DIR}/include")

add_library(sclorktools_common ${common_src_files})

add_executable(test_common ${common_src_files} ${common_test_files})

target_link_libraries(test_common
    gtest
)

