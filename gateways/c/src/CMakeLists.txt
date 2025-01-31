cmake_minimum_required(VERSION 3.10)

set(SOURCE_FILES fjage.c jsmn.c encode.c decode.c fjage.h jsmn.h)

add_library(fjage STATIC ${SOURCE_FILES})
add_library(fjageShared SHARED ${SOURCE_FILES})

target_compile_features(fjage PUBLIC c_std_99)
add_compile_options(fjage -std=c99 -Wall -O2 -D_DEFAULT_SOURCE)

target_compile_features(fjageShared PUBLIC c_std_99)
add_compile_options(fjageShared -std=c99 -Wall -O2 -D_DEFAULT_SOURCE)
IF (WIN32)
    include(GenerateExportHeader)
    set_target_properties(fjageShared PROPERTIES OUTPUT_NAME "fjage" SUFFIX ".dll")
    GENERATE_EXPORT_HEADER(fjageShared
    BASE_NAME fjage
    EXPORT_MACRO_NAME fjage_EXPORT
    EXPORT_FILE_NAME fjage_Export.h
    STATIC_DEFINE fjage_BUILT_AS_STATIC
    )
ELSE()
    set_target_properties(fjageShared PROPERTIES OUTPUT_NAME "fjage" )
ENDIF()