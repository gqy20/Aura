#include <jni.h>

#include <algorithm>
#include <android/log.h>
#include <cstdint>
#include <functional>
#include <fstream>
#include <memory>
#include <regex>
#include <sstream>
#include <string>
#include <streambuf>
#include <sys/stat.h>

#ifdef AURA_MNN_LINKED
#include "llm/llm.hpp"
#endif

namespace {

constexpr const char* LOG_TAG = "Companion.LocalModel.Native";
constexpr int DEFAULT_MAX_NEW_TOKENS = 512;
constexpr int MOBILE_MAX_NEW_TOKENS_CAP = 512;

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

std::string baseName(const std::string& path) {
    const auto pos = path.find_last_of('/');
    if (pos == std::string::npos) {
        return path;
    }
    return path.substr(pos + 1);
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

    int overflow(int ch) override {
        if (ch == traits_type::eof()) {
            return traits_type::not_eof(ch);
        }
        const char c = static_cast<char>(ch);
        if (callback_ != nullptr) {
            callback_(&c, 1);
        }
        return traits_type::not_eof(ch);
    }

    int sync() override {
        return 0;
    }

private:
    std::function<bool(const char*, size_t)> callback_;
};

struct AuraMnnSession {
    explicit AuraMnnSession(std::unique_ptr<MNN::Transformer::Llm, void (*)(MNN::Transformer::Llm*)> llm)
            : llm(std::move(llm)) {}

    std::unique_ptr<MNN::Transformer::Llm, void (*)(MNN::Transformer::Llm*)> llm;
    int maxNewTokens = DEFAULT_MAX_NEW_TOKENS;
};

void destroyLlm(MNN::Transformer::Llm* llm) {
    MNN::Transformer::Llm::destroy(llm);
}

int readMaxNewTokens(const std::string& configPath) {
    std::ifstream input(configPath);
    if (!input) {
        return DEFAULT_MAX_NEW_TOKENS;
    }
    const std::string content((std::istreambuf_iterator<char>(input)), std::istreambuf_iterator<char>());
    std::smatch match;
    static const std::regex pattern(R"("max_new_tokens"\s*:\s*([0-9]+))");
    if (std::regex_search(content, match, pattern) && match.size() > 1) {
        return std::min(MOBILE_MAX_NEW_TOKENS_CAP, std::max(1, std::stoi(match[1].str())));
    }
    return DEFAULT_MAX_NEW_TOKENS;
}

std::string statusName(MNN::Transformer::LlmStatus status) {
    switch (status) {
        case MNN::Transformer::LlmStatus::NOT_LOADED:
            return "NOT_LOADED";
        case MNN::Transformer::LlmStatus::RUNNING:
            return "RUNNING";
        case MNN::Transformer::LlmStatus::NORMAL_FINISHED:
            return "NORMAL_FINISHED";
        case MNN::Transformer::LlmStatus::MAX_TOKENS_FINISHED:
            return "MAX_TOKENS_FINISHED";
        case MNN::Transformer::LlmStatus::USER_CANCEL:
            return "USER_CANCEL";
        case MNN::Transformer::LlmStatus::INTERNAL_ERROR:
            return "INTERNAL_ERROR";
        case MNN::Transformer::LlmStatus::TIMEOUT:
            return "TIMEOUT";
    }
    return "UNKNOWN";
}

void restoreAndroidSteppingStatusIfNeeded(MNN::Transformer::Llm* llm) {
    if (llm == nullptr) {
        return;
    }
    auto* context = llm->getContext();
    if (context == nullptr) {
        return;
    }
    if (context->status == MNN::Transformer::LlmStatus::MAX_TOKENS_FINISHED ||
        context->status == MNN::Transformer::LlmStatus::NORMAL_FINISHED) {
        auto* mutableContext = const_cast<MNN::Transformer::LlmContext*>(context);
        mutableContext->status = MNN::Transformer::LlmStatus::RUNNING;
    }
}

struct AndroidSteppingState {
    bool pendingEop = false;
    bool generateTextEnd = false;
    bool stopRequested = false;

    bool processToken(const std::string& token, const std::function<bool(const std::string&)>& emit) {
        if (token.find("<eop>") != std::string::npos) {
            pendingEop = true;
            return false;
        }
        stopRequested = stopRequested || emit(token);
        return stopRequested;
    }

    void resolve(MNN::Transformer::Llm* llm, int currentSize, int maxNewTokens) {
        auto* context = llm != nullptr ? llm->getContext() : nullptr;
        if (context != nullptr &&
            context->status == MNN::Transformer::LlmStatus::MAX_TOKENS_FINISHED &&
            !stopRequested &&
            currentSize < maxNewTokens) {
            restoreAndroidSteppingStatusIfNeeded(llm);
            if (pendingEop) {
                generateTextEnd = false;
                pendingEop = false;
            }
            return;
        }
        if (context != nullptr &&
            context->status == MNN::Transformer::LlmStatus::NORMAL_FINISHED &&
            !pendingEop &&
            !stopRequested &&
            currentSize < maxNewTokens) {
            restoreAndroidSteppingStatusIfNeeded(llm);
            return;
        }
        if (pendingEop) {
            generateTextEnd = true;
            pendingEop = false;
        }
    }
};
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
        const std::string prefixCacheDir = tmpDir + "/prefixcache";
        mkdir(tmpDir.c_str(), 0700);
        mkdir(prefixCacheDir.c_str(), 0700);
        const int effectiveMaxNewTokens = readMaxNewTokens(path);
        llm->set_config(
                "{\"tmp_path\":\"" + tmpDir +
                "\",\"prefix_cache_path\":\"" + prefixCacheDir +
                "\",\"use_mmap\":true,\"kvcache_mmap\":true,"
                "\"reuse_kv\":true,"
                "\"prompt_cache\":true,"
                "\"max_new_tokens\":" + std::to_string(effectiveMaxNewTokens) + ","
                "\"jinja\":{\"context\":{\"enable_thinking\":false}}}");

        if (!llm->load()) {
            throwIllegalState(env, "MNN model load failed: " + path);
            return 0;
        }

        const std::string prefixCacheFile = "aura_" + baseName(modelDir);
        const bool prefixCacheReady = llm->setPrefixCacheFile(prefixCacheFile);
        auto* session = new AuraMnnSession(std::move(llm));
        session->maxNewTokens = effectiveMaxNewTokens;
        logInfo(
                "mnn_init_completed configPath=" + path +
                " maxNewTokens=" + std::to_string(session->maxNewTokens) +
                " prefixCacheReady=" + (prefixCacheReady ? "true" : "false") +
                " prefixCacheFile=" + prefixCacheFile);
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
        jstring systemPrompt,
        jstring userMessage,
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
        const std::string system = toString(env, systemPrompt);
        const std::string user = toString(env, userMessage);
        logInfo(
                "mnn_submit_started systemPromptLength=" + std::to_string(system.size()) +
                " userMessageLength=" + std::to_string(user.size()));
        JniTokenCallback tokenCallback(env, listener);
        AndroidSteppingState steppingState;
        std::string responseText;
        Utf8StreamProcessor utf8([&tokenCallback, &steppingState](const std::string& token) {
            return steppingState.processToken(token, [&tokenCallback](const std::string& value) {
                return tokenCallback.emit(value);
            });
        });
        CallbackStreamBuffer streamBuffer([&utf8, &responseText](const char* s, size_t n) {
            const std::string chunk(s, n);
            responseText += chunk;
            return utf8.process(s, n);
        });
        std::ostream outputStream(&streamBuffer);

        logInfo("mnn_prefill_started maxNewTokens=" + std::to_string(session->maxNewTokens));
        MNN::Transformer::ChatMessages messages;
        if (!system.empty()) {
            messages.emplace_back("system", system);
        }
        messages.emplace_back("user", user);
        session->llm->response(messages, &outputStream, "<eop>", 0);
        steppingState.resolve(session->llm.get(), 0, session->maxNewTokens);
        const auto* prefillContext = session->llm->getContext();
        if (prefillContext != nullptr) {
            logInfo(
                    "mnn_prefill_completed promptTokens=" + std::to_string(prefillContext->prompt_len) +
                    " prefillUs=" + std::to_string(prefillContext->prefill_us) +
                    " status=" + statusName(prefillContext->status));
        }

        int generated = 0;
        while (!steppingState.stopRequested &&
               !steppingState.generateTextEnd &&
               generated < session->maxNewTokens) {
            session->llm->generate(1);
            generated++;
            steppingState.resolve(session->llm.get(), generated, session->maxNewTokens);
            if (generated == 1 || generated % 16 == 0) {
                const auto* progressContext = session->llm->getContext();
                if (progressContext != nullptr) {
                    logInfo(
                            "mnn_decode_progress generated=" + std::to_string(generated) +
                            " contextGenSeqLen=" + std::to_string(progressContext->gen_seq_len) +
                            " decodeUs=" + std::to_string(progressContext->decode_us) +
                            " status=" + statusName(progressContext->status));
                }
            }
        }
        if (env->ExceptionCheck()) {
            return hashMap;
        }
        while (true) {
            const auto eopPos = responseText.find("<eop>");
            if (eopPos == std::string::npos) {
                break;
            }
            responseText.erase(eopPos, std::string("<eop>").size());
        }
        if (!steppingState.stopRequested && !responseText.empty()) {
            auto syncMessages = messages;
            syncMessages.emplace_back("assistant", responseText);
            session->llm->syncPromptCache(syncMessages);
            logInfo(
                    "mnn_prompt_cache_synced responseLength=" + std::to_string(responseText.size()) +
                    " messageCount=" + std::to_string(syncMessages.size()));
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
