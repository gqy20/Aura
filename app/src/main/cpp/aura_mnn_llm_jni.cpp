#include <jni.h>

extern "C" JNIEXPORT jlong JNICALL
Java_com_xiaoqi_companion_core_local_JniNativeMnnLlmApi_initNative(
        JNIEnv* env,
        jobject /* thiz */,
        jstring /* configPath */) {
    jclass exClass = env->FindClass("java/lang/IllegalStateException");
    if (exClass != nullptr) {
        env->ThrowNew(exClass, "Aura MNN native stub is built, but MNN runtime is not linked yet.");
    }
    return 0;
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_xiaoqi_companion_core_local_JniNativeMnnLlmApi_submitNative(
        JNIEnv* env,
        jobject /* thiz */,
        jlong /* instanceId */,
        jstring /* prompt */,
        jobject /* listener */) {
    jclass hashMapClass = env->FindClass("java/util/HashMap");
    jmethodID hashMapInit = env->GetMethodID(hashMapClass, "<init>", "()V");
    jobject hashMap = env->NewObject(hashMapClass, hashMapInit);

    jclass exClass = env->FindClass("java/lang/IllegalStateException");
    if (exClass != nullptr) {
        env->ThrowNew(exClass, "Aura MNN native stub is built, but MNN runtime is not linked yet.");
    }
    return hashMap;
}

extern "C" JNIEXPORT void JNICALL
Java_com_xiaoqi_companion_core_local_JniNativeMnnLlmApi_releaseNative(
        JNIEnv* /* env */,
        jobject /* thiz */,
        jlong /* instanceId */) {
}
