cmake_minimum_required(VERSION 3.22.1)

project(aura_mnn_llm)

add_library(aura_mnn_llm SHARED
        aura_mnn_llm_jni.cpp
)

target_compile_features(aura_mnn_llm PRIVATE cxx_std_17)

if (DEFINED AURA_MNN_HOME AND NOT "${AURA_MNN_HOME}" STREQUAL "")
    set(AURA_MNN_BUILD_DIR "${AURA_MNN_HOME}/project/android/build_64")
    set(AURA_MNN_LIB_DIR "${AURA_MNN_BUILD_DIR}/lib")
    if (EXISTS "${AURA_MNN_BUILD_DIR}/libMNN.so")
        set(AURA_MNN_LIB_DIR "${AURA_MNN_BUILD_DIR}")
    endif ()
    set(AURA_MNN_INCLUDE_DIR "${AURA_MNN_HOME}/include")
    set(AURA_MNN_LLM_INCLUDE_DIR "${AURA_MNN_HOME}/transformers/llm/engine/include")

    if (EXISTS "${AURA_MNN_LIB_DIR}/libMNN.so" AND EXISTS "${AURA_MNN_INCLUDE_DIR}")
        target_compile_definitions(aura_mnn_llm PRIVATE AURA_MNN_LINKED=1)
        target_include_directories(aura_mnn_llm PRIVATE
                "${AURA_MNN_INCLUDE_DIR}"
                "${AURA_MNN_LLM_INCLUDE_DIR}"
        )
        add_library(MNN SHARED IMPORTED)
        set_target_properties(MNN PROPERTIES IMPORTED_LOCATION "${AURA_MNN_LIB_DIR}/libMNN.so")
        target_link_libraries(aura_mnn_llm MNN)
    else ()
        message(WARNING "AURA_MNN_HOME is set, but libMNN.so/include were not found. Building Aura MNN stub.")
    endif ()
else ()
    message(STATUS "AURA_MNN_HOME is not set. Building Aura MNN stub.")
endif ()

target_link_libraries(aura_mnn_llm
        android
        log
)
