cmake_minimum_required(VERSION 3.22.1)
project(edgefood)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(LLAMA_CPP_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/llama.cpp")
if (NOT EXISTS "${LLAMA_CPP_DIR}/CMakeLists.txt")
    message(FATAL_ERROR "Copy llama.cpp source into third_party/llama.cpp before building.")
endif()

set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)

add_subdirectory(${LLAMA_CPP_DIR} ${CMAKE_BINARY_DIR}/llama)

add_library(edgefood SHARED native-lib.cpp)

find_library(log-lib log)

target_include_directories(edgefood PRIVATE ${LLAMA_CPP_DIR}/include)
target_link_libraries(edgefood PRIVATE llama ${log-lib})
