cmake_minimum_required(VERSION 3.20)
project(light_rumor LANGUAGES CXX C)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Compiler warnings and optimizations
if(ANDROID)
    add_compile_options(-Wall -Wextra -O3 -fPIC -fopenmp)
elseif(MSVC)
    add_compile_options(/W4 /O2 /permissive- /Zc:preprocessor /utf-8 /openmp)
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

# Shared library for Android NDK / JNI
add_library(light_rumor_engine SHARED
    ${LIGHT_RUMOR_CORE_SOURCES}
    src/JniBridge.cpp
)

target_include_directories(light_rumor_engine PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}/include
    ${CMAKE_CURRENT_SOURCE_DIR}/include/light_rumor
)

if(ANDROID)
    target_link_libraries(light_rumor_engine android log ${CMAKE_DL_LIBS} -fopenmp -static-openmp)
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
