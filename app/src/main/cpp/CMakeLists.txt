cmake_minimum_required(VERSION 3.22.1)
project(videofetchmedia LANGUAGES C CXX)

add_library(
    videofetchmedia
    SHARED
    media_probe.cpp
    media_crc32.c
)

target_compile_features(videofetchmedia PRIVATE c_std_11 cxx_std_17)
target_compile_options(videofetchmedia PRIVATE -Wall -Wextra -Werror)
