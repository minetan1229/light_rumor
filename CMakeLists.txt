cmake_minimum_required(VERSION 3.20)
project(light_rumor LANGUAGES CXX C)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Compiler warnings and optimizations
if(ANDROID)
    add_compile_options(-Wall -Wextra -O3 -fPIC -fopenmp)
elseif(MSVC)
    add_compile_options(/W4 /permissive- /Zc:preprocessor /utf-8 /openmp $<$<CONFIG:Release>:/O2>)
else()
    add_compile_options(-Wall -Wextra -O3 -fPIC -fopenmp)
endif()

# Include directories
include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}/include
    ${CMAKE_CURRENT_SOURCE_DIR}/include/light_rumor
)

# Core sources
set(LIGHT_RUMOR_CORE_SOURCES
    src/RawDecoder.cpp
    src/ExportPipeline.cpp
    src/ImageWriter.cpp
    src/VulkanCompute.cpp
    src/WaveformEngine.cpp
    src/DenoiseEngine.cpp
    src/LensfunIntegration.cpp
    src/MaskEngine.cpp
    src/TetherManager.cpp
    src/FieldScopesEngine.cpp
    src/FocusStacker.cpp
    src/AstroAligner.cpp
    src/ColorCheckerCalibration.cpp
    src/SoftProofingEngine.cpp
)

# LibRaw configuration
option(ENABLE_LIBRAW "Enable LibRaw support for camera RAW decoding" ON)
if(ENABLE_LIBRAW)
    find_package(LibRaw QUIET)
    if(NOT LibRaw_FOUND)
        find_path(LibRaw_INCLUDE_DIR NAMES libraw/libraw.h)
        find_library(LibRaw_LIBRARY NAMES raw raw_r libraw libraw_r)
        if(LibRaw_INCLUDE_DIR AND LibRaw_LIBRARY)
            set(LibRaw_FOUND TRUE)
            set(LibRaw_LIBRARIES ${LibRaw_LIBRARY})
            set(LibRaw_INCLUDE_DIRS ${LibRaw_INCLUDE_DIR})
        endif()
    endif()
    if(LibRaw_FOUND)
        add_compile_definitions(LIGHT_RUMOR_ENABLE_LIBRAW=1)
        include_directories(${LibRaw_INCLUDE_DIRS})
        message(STATUS "LibRaw enabled: ${LibRaw_LIBRARIES}")
    else()
        message(STATUS "LibRaw not found, using built-in DNG/TIFF linear decoder fallback.")
    endif()
endif()

# WebP configuration
option(ENABLE_WEBP "Enable WebP support for image export" ON)
if(ENABLE_WEBP)
    find_package(WebP QUIET)
    if(NOT WebP_FOUND)
        include(FetchContent)
        FetchContent_Declare(
            libwebp
            GIT_REPOSITORY https://github.com/webmproject/libwebp.git
            GIT_TAG v1.4.0
            GIT_SHALLOW TRUE
        )
        set(WEBP_BUILD_ANIM_UTILS OFF CACHE BOOL "" FORCE)
        set(WEBP_BUILD_CWEBP OFF CACHE BOOL "" FORCE)
        set(WEBP_BUILD_DWEBP OFF CACHE BOOL "" FORCE)
        set(WEBP_BUILD_GIF2WEBP OFF CACHE BOOL "" FORCE)
        set(WEBP_BUILD_IMG2WEBP OFF CACHE BOOL "" FORCE)
        set(WEBP_BUILD_VWEBP OFF CACHE BOOL "" FORCE)
        set(WEBP_BUILD_WEBPINFO OFF CACHE BOOL "" FORCE)
        set(WEBP_BUILD_LIBWEBPMUX OFF CACHE BOOL "" FORCE)
        set(WEBP_BUILD_WEBPMUX OFF CACHE BOOL "" FORCE)
        set(WEBP_BUILD_EXTRAS OFF CACHE BOOL "" FORCE)
        FetchContent_MakeAvailable(libwebp)
    endif()
    if(TARGET webp)
        add_compile_definitions(LIGHT_RUMOR_ENABLE_WEBP=1)
        message(STATUS "libwebp enabled.")
    endif()
endif()

# Shared library for Android NDK / JNI
add_library(light_rumor_engine SHARED
    ${LIGHT_RUMOR_CORE_SOURCES}
    src/JniBridge.cpp
)

target_include_directories(light_rumor_engine PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}/include
    ${CMAKE_CURRENT_SOURCE_DIR}/include/light_rumor
)

if(TARGET webp)
    target_link_libraries(light_rumor_engine webp)
endif()

if(ANDROID)
    target_link_libraries(light_rumor_engine android log ${CMAKE_DL_LIBS} -fopenmp -static-openmp)
    if(LibRaw_FOUND)
        target_link_libraries(light_rumor_engine ${LibRaw_LIBRARIES})
    endif()
endif()

# Desktop test runners (only when not targeting Android)
if(NOT ANDROID)
    find_package(Vulkan QUIET)
    find_package(OpenMP)
    set(DESKTOP_EXTRA_LIBS "")
    if(WIN32)
        list(APPEND DESKTOP_EXTRA_LIBS psapi)
    endif()
    if(Vulkan_FOUND)
        list(APPEND DESKTOP_EXTRA_LIBS Vulkan::Vulkan)
    endif()
    if(OpenMP_FOUND)
        list(APPEND DESKTOP_EXTRA_LIBS OpenMP::OpenMP_CXX)
    endif()
    if(LibRaw_FOUND)
        list(APPEND DESKTOP_EXTRA_LIBS ${LibRaw_LIBRARIES})
    endif()
    if(TARGET webp)
        list(APPEND DESKTOP_EXTRA_LIBS webp)
    endif()

    # Phase 1
    add_executable(light_rumor_phase1_test
        test/main_test.cpp
        ${LIGHT_RUMOR_CORE_SOURCES}
    )
    target_link_libraries(light_rumor_phase1_test ${DESKTOP_EXTRA_LIBS})

    # Phase 2
    add_executable(light_rumor_phase2_test
        test/culling_test.cpp
        ${LIGHT_RUMOR_CORE_SOURCES}
    )
    target_link_libraries(light_rumor_phase2_test ${DESKTOP_EXTRA_LIBS})

    # Phase 3
    add_executable(light_rumor_phase3_test
        test/phase3_test.cpp
        ${LIGHT_RUMOR_CORE_SOURCES}
    )
    target_link_libraries(light_rumor_phase3_test ${DESKTOP_EXTRA_LIBS})

    # Phase 4
    add_executable(light_rumor_phase4_test
        test/phase4_test.cpp
        ${LIGHT_RUMOR_CORE_SOURCES}
    )
    target_link_libraries(light_rumor_phase4_test ${DESKTOP_EXTRA_LIBS})

    # Phase 5
    add_executable(light_rumor_phase5_test
        test/phase5_test.cpp
        ${LIGHT_RUMOR_CORE_SOURCES}
    )
    target_link_libraries(light_rumor_phase5_test ${DESKTOP_EXTRA_LIBS})

    # Phase 6
    add_executable(light_rumor_phase6_test
        test/phase6_test.cpp
        ${LIGHT_RUMOR_CORE_SOURCES}
    )
    target_link_libraries(light_rumor_phase6_test ${DESKTOP_EXTRA_LIBS})

    # Compatibility aliases
    add_custom_target(apex_phase1_test DEPENDS light_rumor_phase1_test)
    add_custom_target(apex_phase2_test DEPENDS light_rumor_phase2_test)
    add_custom_target(apex_phase3_test DEPENDS light_rumor_phase3_test)
    add_custom_target(apex_phase4_test DEPENDS light_rumor_phase4_test)
    add_custom_target(apex_phase5_test DEPENDS light_rumor_phase5_test)
    add_custom_target(apex_phase6_test DEPENDS light_rumor_phase6_test)
endif()
