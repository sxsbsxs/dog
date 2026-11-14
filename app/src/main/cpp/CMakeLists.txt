cmake_minimum_required(VERSION 3.22.1)
project(dogmedia LANGUAGES C CXX)

add_library(
    dogmedia
    SHARED
    media_probe.cpp
    media_crc32.c
)

target_compile_features(dogmedia PRIVATE c_std_11 cxx_std_17)
target_compile_options(dogmedia PRIVATE -Wall -Wextra -Werror)
