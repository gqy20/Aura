cmake_minimum_required(VERSION 3.22.1)

project(aura_mnn_llm)

add_library(aura_mnn_llm SHARED
        aura_mnn_llm_jni.cpp
)

target_compile_features(aura_mnn_llm PRIVATE cxx_std_17)

# stb_image 用于 submitWithImageNative 内 JPEG/PNG 解码。
# 优先走 AURA_MNN_HOME（绝对路径，与 libMNN.so 来源一致），
# 否则回退到仓库相对路径 ../mnn/3rd_party/imageHelper（约定 mnn 仓库在项目外层）。
if (DEFINED AURA_MNN_HOME AND NOT "${AURA_MNN_HOME}" STREQUAL "")
    set(AURA_STB_DIR "${AURA_MNN_HOME}/3rd_party/imageHelper")
else ()
    set(AURA_STB_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../../mnn/3rd_party/imageHelper")
endif ()
if (EXISTS "${AURA_STB_DIR}/stb_image.h")
    target_include_directories(aura_mnn_llm PRIVATE "${AURA_STB_DIR}")
    message(STATUS "Aura MNN bridge using stb_image from ${AURA_STB_DIR}")
else ()
    message(WARNING
        "stb_image.h not found at ${AURA_STB_DIR}; submitWithImageNative will fail to link. "
        "Set AURA_MNN_HOME or place mnn repo at ../mnn relative to project root.")
endif ()

if (DEFINED AURA_MNN_HOME AND NOT "${AURA_MNN_HOME}" STREQUAL "")
    set(AURA_MNN_BUILD_DIR "${AURA_MNN_HOME}/project/android/build_64")
    set(AURA_MNN_LIB_DIR "${AURA_MNN_BUILD_DIR}/lib")
    if (EXISTS "${AURA_MNN_BUILD_DIR}/libMNN.so")
        set(AURA_MNN_LIB_DIR "${AURA_MNN_BUILD_DIR}")
    endif ()
    set(AURA_MNN_INCLUDE_DIR "${AURA_MNN_HOME}/include")
    set(AURA_MNN_LLM_INCLUDE_DIR "${AURA_MNN_HOME}/transformers/llm/engine/include")

    if (EXISTS "${AURA_MNN_LIB_DIR}/libMNN.so"
            AND EXISTS "${AURA_MNN_INCLUDE_DIR}"
            AND EXISTS "${AURA_MNN_LLM_INCLUDE_DIR}/llm/llm.hpp")
        message(STATUS "Building Aura MNN bridge with runtime from ${AURA_MNN_LIB_DIR}")
        target_compile_definitions(aura_mnn_llm PRIVATE AURA_MNN_LINKED=1)
        target_include_directories(aura_mnn_llm PRIVATE
                "${AURA_MNN_INCLUDE_DIR}"
                "${AURA_MNN_LLM_INCLUDE_DIR}"
        )
        add_library(MNN SHARED IMPORTED)
        set_target_properties(MNN PROPERTIES IMPORTED_LOCATION "${AURA_MNN_LIB_DIR}/libMNN.so")
        target_link_libraries(aura_mnn_llm MNN)
    else ()
        message(WARNING
                "AURA_MNN_HOME is set to ${AURA_MNN_HOME}, but required MNN Android runtime files were not found. "
                "Expected libMNN.so under project/android/build_64[/lib], include/, and "
                "transformers/llm/engine/include/llm/llm.hpp. Building Aura MNN stub.")
    endif ()
else ()
    message(STATUS "AURA_MNN_HOME is not set. Building Aura MNN stub.")
endif ()

target_link_libraries(aura_mnn_llm
        android
        log
)
