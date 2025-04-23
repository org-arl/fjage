cmake_minimum_required(VERSION 3.10)

add_executable(test_fjage test_fjage.c)
target_link_libraries(test_fjage LINK_PUBLIC fjage pthread)
target_include_directories(test_fjage PRIVATE ${CMAKE_SOURCE_DIR}/src)
