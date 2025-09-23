cmake_minimum_required(VERSION 3.22)
project(whisper_jni)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include(FetchContent)

# Fetch whisper.cpp
FetchContent_Declare(
  whispercpp
  GIT_REPOSITORY https://github.com/ggerganov/whisper.cpp.git
  GIT_TAG master
)
FetchContent_MakeAvailable(whispercpp)

add_library(whisper-jni SHARED
    src/whisper_jni.cpp
)

target_include_directories(whisper-jni PRIVATE
    ${whispercpp_SOURCE_DIR}
)

target_link_libraries(whisper-jni
    android
    log
    whisper
)


