cmake_minimum_required(VERSION 3.22.1)
project(dogmedia LANGUAGES CXX)

add_library(
    dogmedia
    SHARED
    media_probe.cpp
)

target_compile_features(dogmedia PRIVATE cxx_std_17)
target_compile_options(dogmedia PRIVATE -Wall -Wextra -Werror)
