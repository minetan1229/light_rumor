cmake_minimum_required(VERSION 3.20)
project(ApexFieldCore LANGUAGES CXX C)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Compiler warnings and optimizations
if(MSVC)
    add_compile_options(/W4 /O2 /permissive- /Zc:preprocessor /utf-8)
else()
    add_compile_options(-Wall -Wextra -O3 -fPIC)
endif()

# Include directories
include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}/include
)

# Core sources
set(APEX_CORE_SOURCES
    src/RawDecoder.cpp
    src/ExportPipeline.cpp
    src/ImageWriter.cpp
    src/VulkanCompute.cpp
)

# Optional JNI Bridge source
if(ANDROID OR APEX_ENABLE_JNI)
    list(APPEND APEX_CORE_SOURCES src/JniBridge.cpp)
endif()

# Build shared library for Android NDK / JNI
add_library(apex_engine SHARED
    ${APEX_CORE_SOURCES}
)

if(ANDROID)
    target_link_libraries(apex_engine log)
endif()

# Build verification test runner executable for desktop
add_executable(apex_phase1_test
    test/main_test.cpp
    ${APEX_CORE_SOURCES}
)

if(WIN32)
    target_link_libraries(apex_phase1_test psapi)
endif()
