cmake_minimum_required(VERSION 3.1)

add_library(fjage STATIC fjage.c jsmn.c encode.c decode.c fjage.h jsmn.h)
target_compile_features(fjage PUBLIC c_std_99)
add_compile_options(fjage -std=c99 -Wall -O2 -D_DEFAULT_SOURCE)
target_include_directories (fjage PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})