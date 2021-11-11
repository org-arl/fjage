cmake_minimum_required(VERSION 3.1)

add_executable(test_fjage test_fjage.c)
target_link_libraries(test_fjage LINK_PUBLIC fjage pthread)
