cmake_minimum_required(VERSION 3.22.1)
project(basira LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# llama.cpp pinned to a known-good commit
set(LLAMA_COMMIT 839a572a4cdecdb80d211297b8e7d7323a6e99d2)

include(FetchContent)
if(DEFINED LLAMA_LOCAL_DIR AND EXISTS ${LLAMA_LOCAL_DIR})
    FetchContent_Declare(llama SOURCE_DIR ${LLAMA_LOCAL_DIR})
else()
    FetchContent_Declare(llama URL https://github.com/ggml-org/llama.cpp/archive/${LLAMA_COMMIT}.tar.gz)
endif()

set(BUILD_SHARED_LIBS      OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON     ON  CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS      OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS      OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES   OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER     OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP        OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL          OFF CACHE BOOL "" FORCE)
set(LLAMA_SUBPROCESS       OFF CACHE BOOL "" FORCE)
set(LLAMA_USE_PREBUILT_UI  OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE            OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP            OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL        OFF CACHE BOOL "" FORCE)
if(ANDROID_ABI STREQUAL "arm64-v8a")
    # dotprod + fp16: supported by practically every phone since 2019
    set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod+fp16" CACHE STRING "" FORCE)
endif()

FetchContent_MakeAvailable(llama)

add_library(basira SHARED basira_jni.cpp)
target_link_libraries(basira PRIVATE llama llama-common)
if(ANDROID)
    target_link_libraries(basira PRIVATE android log)
endif()
