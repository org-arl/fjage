cmake_minimum_required(VERSION 3.1)

project(fjage_c_gateway LANGUAGES C)

set(CMAKE_RUNTIME_OUTPUT_DIRECTORY ${CMAKE_SOURCE_DIR})
set(CMAKE_LIBRARY_OUTPUT_DIRECTORY ${CMAKE_SOURCE_DIR})
set(CMAKE_ARCHIVE_OUTPUT_DIRECTORY ${CMAKE_SOURCE_DIR})
add_subdirectory(src)
if(BUILD_TESTING)
    add_subdirectory(tests)
endif()
