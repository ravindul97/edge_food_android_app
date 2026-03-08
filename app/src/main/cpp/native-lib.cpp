#include <jni.h>
#include <string>
#include <mutex>
#include <sstream>
#include <android/log.h>

#include "llama.h"

#define LOG_TAG "EdgeFoodNative"
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static std::mutex g_mutex;
static llama_model * g_model = nullptr;
static llama_context * g_ctx = nullptr;
static const llama_vocab * g_vocab = nullptr;

static std::string jstringToStd(JNIEnv *env, jstring s) {
    if (!s) return "";
    const char *chars = env->GetStringUTFChars(s, nullptr);
    std::string out(chars ? chars : "");
    if (chars) env->ReleaseStringUTFChars(s, chars);
    return out;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_example_edgefood_nativebridge_LlamaBridge_initModel(
        JNIEnv *env, jobject /*thiz*/, jstring modelPath, jint nThreads, jint contextSize) {
    std::lock_guard<std::mutex> lock(g_mutex);

    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }

    llama_backend_init();
    llama_model_params mparams = llama_model_default_params();
    std::string path = jstringToStd(env, modelPath);
    g_model = llama_model_load_from_file(path.c_str(), mparams);
    if (!g_model) {
        ALOGE("Failed to load model: %s", path.c_str());
        return JNI_FALSE;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = contextSize;
    cparams.n_batch = 512;
    cparams.n_threads = nThreads;
    cparams.n_threads_batch = nThreads;

    g_ctx = llama_init_from_model(g_model, cparams);
    if (!g_ctx) {
        ALOGE("Failed to init context");
        llama_model_free(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }

    g_vocab = llama_model_get_vocab(g_model);
    return JNI_TRUE;
}

static std::string sample_text(const std::string &prompt, int maxTokens) {
    if (!g_model || !g_ctx || !g_vocab) return "";

    std::vector<llama_token> tokens(prompt.size() + 32);
    int n_prompt = llama_tokenize(
            g_vocab,
            prompt.c_str(),
            (int32_t)prompt.size(),
            tokens.data(),
            (int32_t)tokens.size(),
            true,
            true
    );

    if (n_prompt < 0) {
        tokens.resize((size_t)(-n_prompt));
        n_prompt = llama_tokenize(
                g_vocab,
                prompt.c_str(),
                (int32_t)prompt.size(),
                tokens.data(),
                (int32_t)tokens.size(),
                true,
                true
        );
    }
    if (n_prompt <= 0) return "";

    tokens.resize((size_t)n_prompt);

    // llama_kv_cache_clear is deprecated/removed in some versions, but let's check what's available
    llama_kv_cache_clear(g_ctx);

    llama_batch batch = llama_batch_get_one(tokens.data(), n_prompt);
    if (llama_decode(g_ctx, batch) != 0) {
        return "";
    }

    std::string output;
    for (int i = 0; i < maxTokens; ++i) {
        const float * logits = llama_get_logits_ith(g_ctx, llama_n_tokens(g_ctx) - 1);
        if (!logits) break;

        llama_token_data_array candidates = { nullptr, 0, false };
        std::vector<llama_token_data> cur;
        cur.reserve((size_t)llama_vocab_n_tokens(g_vocab));
        for (llama_token tok = 0; tok < llama_vocab_n_tokens(g_vocab); ++tok) {
            cur.push_back({tok, logits[tok], 0.0f});
        }
        candidates.data = cur.data();
        candidates.size = cur.size();

        llama_sampler * sampler = llama_sampler_init_greedy();
        llama_token next = llama_sampler_sample(sampler, g_ctx, &candidates);
        llama_sampler_free(sampler);

        if (llama_vocab_is_eog(g_vocab, next)) break;

        char piece[256];
        int piece_len = llama_token_to_piece(g_vocab, next, piece, sizeof(piece), 0, true);
        if (piece_len > 0) output.append(piece, piece_len);

        llama_batch next_batch = llama_batch_get_one(&next, 1);
        if (llama_decode(g_ctx, next_batch) != 0) {
            break;
        }
    }
    return output;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_edgefood_nativebridge_LlamaBridge_analyze(
        JNIEnv *env, jobject /*thiz*/, jstring prompt, jint maxTokens) {
    std::lock_guard<std::mutex> lock(g_mutex);
    std::string out = sample_text(jstringToStd(env, prompt), (int)maxTokens);
    return env->NewStringUTF(out.c_str());
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_edgefood_nativebridge_LlamaBridge_releaseModel(
        JNIEnv * /*env*/, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    g_vocab = nullptr;
    llama_backend_free();
}
