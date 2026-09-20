cmake_minimum_required(VERSION 3.22.1)
project("apexfield")

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}/../../../../core/include
)

add_library(
    apexfield
    SHARED
    ${CMAKE_CURRENT_SOURCE_DIR}/../../../../core/src/ColorPipeline.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/JniBridge.cpp
)

find_library(log-lib log)
find_library(jnigraphics-lib jnigraphics)

target_link_libraries(
    apexfield
    ${log-lib}
    ${jnigraphics-lib}
)
