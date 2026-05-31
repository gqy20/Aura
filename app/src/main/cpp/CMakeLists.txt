cmake_minimum_required(VERSION 3.22.1)

project(aura_mnn_llm)

add_library(aura_mnn_llm SHARED
        aura_mnn_llm_jni.cpp
)

target_compile_features(aura_mnn_llm PRIVATE cxx_std_17)

target_link_libraries(aura_mnn_llm
        android
        log
)
