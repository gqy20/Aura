#include <jni.h>

#include <android/log.h>
#include <cstdint>
#include <functional>
#include <memory>
#include <sstream>
#include <string>
#include <streambuf>
#include <sys/stat.h>

#ifdef AURA_MNN_LINKED
#include "llm/llm.hpp"
#endif

namespace {

constexpr const char* LOG_TAG = "Companion.LocalModel.Native";

void logInfo(const std::string& message) {
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "%s", message.c_str());
}

void logError(const std::string& message) {
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "%s", message.c_str());
}

void throwIllegalState(JNIEnv* env, const std::string& message) {
    logError(message);
    jclass exClass = env->FindClass("java/lang/IllegalStateException");
    if (exClass != nullptr) {
        env->ThrowNew(exClass, message.c_str());
    }
}

jobject newHashMap(JNIEnv* env) {
    jclass hashMapClass = env->FindClass("java/util/HashMap");
    jmethodID hashMapInit = env->GetMethodID(hashMapClass, "<init>", "()V");
    return env->NewObject(hashMapClass, hashMapInit);
}

void putLong(JNIEnv* env, jobject map, const char* key, int64_t value) {
    jclass mapClass = env->GetObjectClass(map);
    jmethodID putMethod = env->GetMethodID(
            mapClass,
            "put",
            "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
    jclass longClass = env->FindClass("java/lang/Long");
    jmethodID longInit = env->GetMethodID(longClass, "<init>", "(J)V");
    jstring jKey = env->NewStringUTF(key);
    jobject jValue = env->NewObject(longClass, longInit, static_cast<jlong>(value));
    env->CallObjectMethod(map, putMethod, jKey, jValue);
    env->DeleteLocalRef(jKey);
    env->DeleteLocalRef(jValue);
    env->DeleteLocalRef(longClass);
    env->DeleteLocalRef(mapClass);
}

std::string toString(JNIEnv* env, jstring value) {
    if (value == nullptr) {
        return "";
    }
    const char* chars = env->GetStringUTFChars(value, nullptr);
    std::string result = chars == nullptr ? "" : chars;
    if (chars != nullptr) {
        env->ReleaseStringUTFChars(value, chars);
    }
    return result;
}

#ifdef AURA_MNN_LINKED
std::string parentDir(const std::string& path) {
    const auto pos = path.find_last_of('/');
    if (pos == std::string::npos) {
        return ".";
    }
    return path.substr(0, pos);
}

class JniTokenCallback {
public:
    JniTokenCallback(JNIEnv* env, jobject listener)
            : env_(env), listener_(listener) {
        jclass listenerClass = env_->GetObjectClass(listener_);
        onProgress_ = env_->GetMethodID(listenerClass, "onProgress", "(Ljava/lang/String;)Z");
        env_->DeleteLocalRef(listenerClass);
    }

    bool emit(const std::string& token) {
        if (listener_ == nullptr || onProgress_ == nullptr || token.empty()) {
            return false;
        }
        jstring value = env_->NewStringUTF(token.c_str());
        jboolean shouldContinue = env_->CallBooleanMethod(listener_, onProgress_, value);
        env_->DeleteLocalRef(value);
        return !env_->ExceptionCheck() && shouldContinue == JNI_TRUE;
    }

private:
    JNIEnv* env_;
    jobject listener_;
    jmethodID onProgress_ = nullptr;
};

class Utf8StreamProcessor {
public:
    explicit Utf8StreamProcessor(std::function<bool(const std::string&)> callback)
            : callback_(std::move(callback)) {}

    bool process(const char* str, size_t len) {
        buffer_.append(str, len);
        size_t index = 0;
        std::string completeChars;
        while (index < buffer_.size()) {
            int charLen = utf8CharLength(static_cast<unsigned char>(buffer_[index]));
            if (charLen == 0 || index + charLen > buffer_.size()) {
                break;
            }
            completeChars.append(buffer_, index, charLen);
            index += charLen;
        }
        buffer_ = buffer_.substr(index);
        if (!completeChars.empty()) {
            return callback_(completeChars);
        }
        return true;
    }

private:
    static int utf8CharLength(unsigned char byte) {
        if ((byte & 0x80) == 0) return 1;
        if ((byte & 0xE0) == 0xC0) return 2;
        if ((byte & 0xF0) == 0xE0) return 3;
        if ((byte & 0xF8) == 0xF0) return 4;
        return 0;
    }

    std::string buffer_;
    std::function<bool(const std::string&)> callback_;
};

class CallbackStreamBuffer : public std::streambuf {
public:
    explicit CallbackStreamBuffer(std::function<bool(const char*, size_t)> callback)
            : callback_(std::move(callback)) {}

protected:
    std::streamsize xsputn(const char* s, std::streamsize n) override {
        if (callback_ != nullptr) {
            callback_(s, static_cast<size_t>(n));
        }
        return n;
    }

private:
    std::function<bool(const char*, size_t)> callback_;
};

struct AuraMnnSession {
    explicit AuraMnnSession(std::unique_ptr<MNN::Transformer::Llm, void (*)(MNN::Transformer::Llm*)> llm)
            : llm(std::move(llm)) {}

    std::unique_ptr<MNN::Transformer::Llm, void (*)(MNN::Transformer::Llm*)> llm;
};

void destroyLlm(MNN::Transformer::Llm* llm) {
    MNN::Transformer::Llm::destroy(llm);
}
#endif

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_xiaoqi_companion_core_local_JniNativeMnnLlmApi_initNative(
        JNIEnv* env,
        jobject /* thiz */,
        jstring configPath) {
#ifndef AURA_MNN_LINKED
    throwIllegalState(env, "Aura MNN native stub is built, but MNN runtime is not linked yet.");
    return 0;
#else
    const std::string path = toString(env, configPath);
    if (path.empty()) {
        throwIllegalState(env, "MNN config path is empty.");
        return 0;
    }

    try {
        logInfo("mnn_init_started configPath=" + path);
        std::unique_ptr<MNN::Transformer::Llm, void (*)(MNN::Transformer::Llm*)> llm(
                MNN::Transformer::Llm::createLLM(path),
                destroyLlm);
        if (!llm) {
            throwIllegalState(env, "MNN createLLM failed: " + path);
            return 0;
        }

        const std::string modelDir = parentDir(path);
        const std::string tmpDir = modelDir + "/tmp";
        mkdir(tmpDir.c_str(), 0700);
        llm->set_config("{\"tmp_path\":\"" + tmpDir + "\",\"use_mmap\":true}");

        if (!llm->load()) {
            throwIllegalState(env, "MNN model load failed: " + path);
            return 0;
        }

        auto* session = new AuraMnnSession(std::move(llm));
        logInfo("mnn_init_completed configPath=" + path);
        return reinterpret_cast<jlong>(session);
    } catch (const std::exception& ex) {
        throwIllegalState(env, std::string("MNN native load failed: ") + ex.what());
        return 0;
    } catch (...) {
        throwIllegalState(env, "MNN native load failed with an unknown error.");
        return 0;
    }
#endif
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_xiaoqi_companion_core_local_JniNativeMnnLlmApi_submitNative(
        JNIEnv* env,
        jobject /* thiz */,
        jlong instanceId,
        jstring prompt,
        jobject listener) {
    jobject hashMap = newHashMap(env);
#ifndef AURA_MNN_LINKED
    throwIllegalState(env, "Aura MNN native stub is built, but MNN runtime is not linked yet.");
    return hashMap;
#else
    auto* session = reinterpret_cast<AuraMnnSession*>(instanceId);
    if (session == nullptr || !session->llm) {
        throwIllegalState(env, "MNN native session is not loaded.");
        return hashMap;
    }

    try {
        const std::string input = toString(env, prompt);
        logInfo("mnn_submit_started promptLength=" + std::to_string(input.size()));
        JniTokenCallback tokenCallback(env, listener);
        Utf8StreamProcessor utf8([&tokenCallback](const std::string& token) {
            return tokenCallback.emit(token);
        });
        CallbackStreamBuffer streamBuffer([&utf8](const char* s, size_t n) {
            return utf8.process(s, n);
        });
        std::ostream outputStream(&streamBuffer);

        session->llm->response(input, &outputStream, "<eop>");
        if (env->ExceptionCheck()) {
            return hashMap;
        }

        const auto* context = session->llm->getContext();
        if (context != nullptr) {
            putLong(env, hashMap, "prompt_tokens", context->prompt_len);
            putLong(env, hashMap, "completion_tokens", context->gen_seq_len);
            putLong(env, hashMap, "prefill_us", context->prefill_us);
            putLong(env, hashMap, "decode_us", context->decode_us);
            putLong(env, hashMap, "load_us", context->load_us);
        }
        logInfo("mnn_submit_completed");
        return hashMap;
    } catch (const std::exception& ex) {
        throwIllegalState(env, std::string("MNN native generation failed: ") + ex.what());
        return hashMap;
    } catch (...) {
        throwIllegalState(env, "MNN native generation failed with an unknown error.");
        return hashMap;
    }
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_com_xiaoqi_companion_core_local_JniNativeMnnLlmApi_releaseNative(
        JNIEnv* env,
        jobject /* thiz */,
        jlong instanceId) {
#ifdef AURA_MNN_LINKED
    try {
        logInfo("mnn_release_started");
        delete reinterpret_cast<AuraMnnSession*>(instanceId);
        logInfo("mnn_release_completed");
    } catch (...) {
        throwIllegalState(env, "MNN native release failed.");
    }
#else
    (void) env;
    (void) instanceId;
#endif
}
