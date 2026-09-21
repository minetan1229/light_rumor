cmake_minimum_required(VERSION 3.20)
project(light_rumor LANGUAGES CXX C)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Compiler warnings and optimizations
if(ANDROID)
    add_compile_options(-Wall -Wextra -O3 -fPIC)
elseif(MSVC)
    add_compile_options(/W4 /O2 /permissive- /Zc:preprocessor /utf-8 /openmp)
else()
    add_compile_options(-Wall -Wextra -O3 -fPIC -fopenmp)
endif()

# Include directories
include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}/include
    ${CMAKE_CURRENT_SOURCE_DIR}/include/light_rumor
    ${CMAKE_CURRENT_SOURCE_DIR}/include/apex
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

# Shared library for Android NDK / JNI
add_library(light_rumor_engine SHARED
    ${LIGHT_RUMOR_CORE_SOURCES}
    src/JniBridge.cpp
)

if(ANDROID)
    target_link_libraries(light_rumor_engine log ${CMAKE_DL_LIBS})
endif()

# Also build apex_engine shared library for binary compatibility
add_library(apex_engine SHARED
    ${LIGHT_RUMOR_CORE_SOURCES}
    src/JniBridge.cpp
)
if(ANDROID)
    target_link_libraries(apex_engine log ${CMAKE_DL_LIBS})
endif()

# Desktop test runners (only when not targeting Android)
if(NOT ANDROID)
    # Phase 1
    add_executable(light_rumor_phase1_test
        test/main_test.cpp
        ${LIGHT_RUMOR_CORE_SOURCES}
    )
    if(WIN32)
        target_link_libraries(light_rumor_phase1_test psapi)
    endif()

    # Phase 2
    add_executable(light_rumor_phase2_test
        test/culling_test.cpp
        ${LIGHT_RUMOR_CORE_SOURCES}
    )
    if(WIN32)
        target_link_libraries(light_rumor_phase2_test psapi)
    endif()

    # Phase 3
    add_executable(light_rumor_phase3_test
        test/phase3_test.cpp
        ${LIGHT_RUMOR_CORE_SOURCES}
    )
    if(WIN32)
        target_link_libraries(light_rumor_phase3_test psapi)
    endif()

    # Phase 4
    add_executable(light_rumor_phase4_test
        test/phase4_test.cpp
        ${LIGHT_RUMOR_CORE_SOURCES}
    )
    if(WIN32)
        target_link_libraries(light_rumor_phase4_test psapi)
    endif()

    # Phase 5
    add_executable(light_rumor_phase5_test
        test/phase5_test.cpp
        ${LIGHT_RUMOR_CORE_SOURCES}
    )
    if(WIN32)
        target_link_libraries(light_rumor_phase5_test psapi)
    endif()

    # Phase 6
    add_executable(light_rumor_phase6_test
        test/phase6_test.cpp
        ${LIGHT_RUMOR_CORE_SOURCES}
    )
    if(WIN32)
        target_link_libraries(light_rumor_phase6_test psapi)
    endif()

    # Compatibility aliases
    add_custom_target(apex_phase6_test DEPENDS light_rumor_phase6_test)
endif()
