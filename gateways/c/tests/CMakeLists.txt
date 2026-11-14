cmake_minimum_required(VERSION 3.10)

add_executable(test_fjage test_fjage.c)
target_link_libraries(test_fjage LINK_PUBLIC fjage pthread)
target_include_directories(test_fjage PRIVATE ${CMAKE_SOURCE_DIR}/src)

# Offline codec tests include fjage.c directly, so they build from source
# rather than linking the library.
add_executable(test_codec test_codec.c ../src/jsmn.c ../src/encode.c ../src/decode.c)
target_include_directories(test_codec PRIVATE ${CMAKE_SOURCE_DIR}/src)
target_compile_features(test_codec PRIVATE c_std_99)
target_compile_definitions(test_codec PRIVATE _DEFAULT_SOURCE)
if(NOT WIN32)
    target_compile_options(test_codec PRIVATE -g -fsanitize=address,undefined -fno-sanitize-recover=undefined)
    target_link_libraries(test_codec PRIVATE -fsanitize=address,undefined)
endif()
