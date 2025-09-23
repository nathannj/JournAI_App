#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <mutex>
#include <memory>
#include <cmath>
#include <atomic>
#include <thread>
#include <chrono>

#include "whisper.h"

#define LOG_TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {
struct WhisperEngine {
    std::mutex mutex;
    whisper_context *ctx = nullptr;
    std::vector<float> pcmBuffer;
    std::vector<float> tailBuffer;
    std::atomic<bool> isProcessing{false};
};

static std::unique_ptr<WhisperEngine> g_engine;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_journai_journai_whisper_WhisperBridge_nativeInit(
        JNIEnv *env, jobject /*thiz*/, jstring modelPath) {
    const char *cpath = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("Initializing whisper with model: %s", cpath);
    auto engine = std::make_unique<WhisperEngine>();
    struct whisper_context_params cparams = whisper_context_default_params();
    engine->ctx = whisper_init_from_file_with_params(cpath, cparams);
    env->ReleaseStringUTFChars(modelPath, cpath);
    if (!engine->ctx) {
        LOGE("Failed to load whisper model");
        return JNI_FALSE;
    }
    g_engine = std::move(engine);
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_journai_journai_whisper_WhisperBridge_nativeRelease(
        JNIEnv *, jobject) {
    if (!g_engine) return;
    // Wait for any in-flight processing to complete
    while (g_engine->isProcessing.load(std::memory_order_acquire)) {
        std::this_thread::sleep_for(std::chrono::milliseconds(2));
    }
    std::lock_guard<std::mutex> lock(g_engine->mutex);
    if (g_engine->ctx) {
        whisper_free(g_engine->ctx);
        g_engine->ctx = nullptr;
    }
    g_engine.reset();
}

extern "C" JNIEXPORT void JNICALL
Java_com_journai_journai_whisper_WhisperBridge_nativeFeedPcm(
        JNIEnv *env, jobject, jshortArray pcmArray, jint length) {
    if (!g_engine || !g_engine->ctx) return;
    jboolean isCopy = JNI_FALSE;
    jshort *data = env->GetShortArrayElements(pcmArray, &isCopy);
    if (!data) return;
    std::lock_guard<std::mutex> lock(g_engine->mutex);
    g_engine->pcmBuffer.reserve(g_engine->pcmBuffer.size() + length);
    for (int i = 0; i < length; ++i) {
        g_engine->pcmBuffer.push_back(data[i] / 32768.0f);
    }
    env->ReleaseShortArrayElements(pcmArray, data, JNI_ABORT);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_journai_journai_whisper_WhisperBridge_nativeProcess(
        JNIEnv *env, jobject, jboolean translate, jint threads) {
    if (!g_engine) return env->NewStringUTF("");
    std::unique_lock<std::mutex> lock(g_engine->mutex);
    if (!g_engine->ctx) return env->NewStringUTF("");
    std::vector<float> chunk;
    if (g_engine->pcmBuffer.size() < 4800) { // ~0.3 sec at 16kHz for better stability
        return env->NewStringUTF("");
    }
    // Cheap VAD: skip very low energy chunks to avoid blank audio decodes
    {
        double sumsq = 0.0;
        for (float s : g_engine->pcmBuffer) { sumsq += (double)s * (double)s; }
        double rms = std::sqrt(sumsq / (double)g_engine->pcmBuffer.size());
        if (rms < 0.0015) { // be less aggressive; keep quiet speech
            g_engine->pcmBuffer.clear();
            return env->NewStringUTF("");
        }
    }
    // Prepend small tail for context across chunks (~100ms)
    const int tail = std::min<int>(2400, (int)g_engine->tailBuffer.size()); // ~150ms
    chunk.reserve(tail + g_engine->pcmBuffer.size());
    if (tail > 0) {
        chunk.insert(chunk.end(), g_engine->tailBuffer.end() - tail, g_engine->tailBuffer.end());
    }
    chunk.insert(chunk.end(), g_engine->pcmBuffer.begin(), g_engine->pcmBuffer.end());
    // Save new tail from end of current chunk
    const int newTail = std::min<int>(2400, (int)chunk.size());
    g_engine->tailBuffer.assign(chunk.end() - newTail, chunk.end());
    // Clear consumed buffer
    g_engine->pcmBuffer.clear();
    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.translate = translate;
    params.n_threads = threads > 0 ? threads : 2;
    params.no_context = false; // keep context across calls for better accuracy
    params.single_segment = false;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.temperature = 0.2f;
    params.detect_language = false;
    params.language = "en";

    // mark processing and capture ctx pointer
    g_engine->isProcessing.store(true, std::memory_order_release);
    whisper_context *ctx = g_engine->ctx;
    lock.unlock();
    if (whisper_full(ctx, params, chunk.data(), (int)chunk.size()) != 0) {
        LOGE("whisper_full failed");
        g_engine->isProcessing.store(false, std::memory_order_release);
        return env->NewStringUTF("");
    }
    std::string out;
    const int n_segments = whisper_full_n_segments(ctx);
    for (int i = 0; i < n_segments; ++i) {
        const char *text = whisper_full_get_segment_text(ctx, i);
        if (!text) continue;
        // Skip bracketed tokens like [BLANK_AUDIO]
        size_t len = strlen(text);
        if (len >= 2 && text[0] == '[' && text[len-1] == ']') continue;
        if (!out.empty()) out += " ";
        out += text;
    }
    g_engine->isProcessing.store(false, std::memory_order_release);
    return env->NewStringUTF(out.c_str());
}


