cmake_minimum_required(VERSION 3.22.1)

project("soundbox_audio" VERSION 1.0.0 LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Optimization flags for audio processing and ARM NEON SIMD
if(ANDROID_ABI MATCHES "^arm")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -ffast-math -flto")
    if(ANDROID_ABI STREQUAL "armeabi-v7a")
        set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -mfpu=neon")
    endif()
else()
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -ffast-math -flto")
endif()

include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}
)

add_library(
    soundbox_audio
    SHARED
    native-audio-jni.cpp
    AudioEngine.cpp
    DspProcessor.cpp
    Crossfader.cpp
)

find_library(
    log-lib
    log
)

find_library(
    aaudio-lib
    aaudio
)

find_library(
    opensles-lib
    OpenSLES
)

find_library(
    math-lib
    m
)

target_link_libraries(
    soundbox_audio
    ${log-lib}
    ${aaudio-lib}
    ${opensles-lib}
    ${math-lib}
)
