// بصيرة - محرك النموذج المحلي (llama.cpp عبر JNI)
#include <jni.h>
#include <algorithm>
#include <string>
#include <vector>
#include <unistd.h>

#include "llama.h"
#include "common.h"
#include "chat.h"
#include "sampling.h"

#ifdef __ANDROID__
#include <android/log.h>
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "basira", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "basira", __VA_ARGS__)
#else
#include <cstdio>
#define LOGI(...) fprintf(stderr, __VA_ARGS__)
#define LOGE(...) fprintf(stderr, __VA_ARGS__)
#endif

static llama_model *              g_model   = nullptr;
static llama_context *            g_ctx     = nullptr;
static common_sampler *           g_smpl    = nullptr;
static common_chat_templates_ptr  g_tmpl;
static std::vector<llama_token>   g_cache;      // tokens currently in the KV cache
static std::string                g_pending;    // bytes of an incomplete UTF-8 char
static int                        g_remaining = 0;
static int                        g_n_ctx     = 4096;
static constexpr int              BATCH       = 512;

static void log_cb(ggml_log_level level, const char * text, void *) {
    if (level >= GGML_LOG_LEVEL_WARN) LOGE("%s", text);
}

static bool utf8_complete(const std::string & s) {
    const auto * b = (const unsigned char *) s.data();
    size_t i = 0, n = s.size();
    while (i < n) {
        int len;
        if      ((b[i] & 0x80) == 0x00) len = 1;
        else if ((b[i] & 0xE0) == 0xC0) len = 2;
        else if ((b[i] & 0xF0) == 0xE0) len = 3;
        else if ((b[i] & 0xF8) == 0xF0) len = 4;
        else return true; // invalid byte: give up waiting, flush as is
        if (i + len > n) return false;
        i += len;
    }
    return true;
}

static std::string bytes_to_string(JNIEnv * env, jbyteArray arr) {
    jsize len = env->GetArrayLength(arr);
    std::string s(len, '\0');
    env->GetByteArrayRegion(arr, 0, len, reinterpret_cast<jbyte *>(&s[0]));
    return s;
}

static jbyteArray string_to_bytes(JNIEnv * env, const std::string & s) {
    jbyteArray arr = env->NewByteArray((jsize) s.size());
    env->SetByteArrayRegion(arr, 0, (jsize) s.size(), reinterpret_cast<const jbyte *>(s.data()));
    return arr;
}

static void free_all() {
    if (g_smpl)  { common_sampler_free(g_smpl); g_smpl = nullptr; }
    g_tmpl.reset();
    if (g_ctx)   { llama_free(g_ctx); g_ctx = nullptr; }
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
    g_cache.clear();
    g_pending.clear();
}

extern "C" JNIEXPORT void JNICALL
Java_com_basira_ai_llm_LlamaNative_init(JNIEnv *, jobject) {
    llama_log_set(log_cb, nullptr);
    llama_backend_init();
}

// 0 = ok, 1 = model failed, 2 = context failed
extern "C" JNIEXPORT jint JNICALL
Java_com_basira_ai_llm_LlamaNative_load(JNIEnv * env, jobject, jstring jpath, jint n_ctx, jint n_threads) {
    free_all();
    const char * path = env->GetStringUTFChars(jpath, nullptr);
    llama_model_params mp = llama_model_default_params();
    g_model = llama_model_load_from_file(path, mp);
    env->ReleaseStringUTFChars(jpath, path);
    if (!g_model) return 1;

    int threads = n_threads > 0 ? n_threads
                                : std::max(2, std::min(6, (int) sysconf(_SC_NPROCESSORS_ONLN) - 2));
    llama_context_params cp = llama_context_default_params();
    cp.n_ctx           = n_ctx;
    cp.n_batch         = BATCH;
    cp.n_ubatch        = BATCH;
    cp.n_threads       = threads;
    cp.n_threads_batch = threads;
    g_ctx = llama_init_from_model(g_model, cp);
    if (!g_ctx) { free_all(); return 2; }
    g_n_ctx = (int) llama_n_ctx(g_ctx);
    g_tmpl  = common_chat_templates_init(g_model, "");
    LOGI("model loaded, ctx=%d threads=%d", g_n_ctx, threads);
    return 0;
}

// Prepare a full chat. Reuses the KV cache for the shared prefix with the previous call.
// 0 = ok, 1 = no model, 2 = prompt too long, 3 = decode failed
extern "C" JNIEXPORT jint JNICALL
Java_com_basira_ai_llm_LlamaNative_start(JNIEnv * env, jobject, jobjectArray jroles, jobjectArray jcontents,
                                         jfloat temp, jint n_predict) {
    if (!g_ctx) return 1;

    common_chat_templates_inputs inputs;
    inputs.use_jinja             = true;
    inputs.add_generation_prompt = true;
    inputs.enable_thinking       = false;
    const jsize n = env->GetArrayLength(jroles);
    for (jsize i = 0; i < n; i++) {
        auto jr = (jstring) env->GetObjectArrayElement(jroles, i);
        auto jc = (jbyteArray) env->GetObjectArrayElement(jcontents, i);
        const char * r = env->GetStringUTFChars(jr, nullptr);
        common_chat_msg m;
        m.role    = r;
        m.content = bytes_to_string(env, jc);
        env->ReleaseStringUTFChars(jr, r);
        env->DeleteLocalRef(jr);
        env->DeleteLocalRef(jc);
        inputs.messages.push_back(m);
    }

    std::string prompt;
    try {
        prompt = common_chat_templates_apply(g_tmpl.get(), inputs).prompt.str();
    } catch (const std::exception & e) {
        LOGE("template error: %s, falling back to non-jinja", e.what());
        inputs.use_jinja = false;
        prompt = common_chat_templates_apply(g_tmpl.get(), inputs).prompt.str();
    }

    std::vector<llama_token> tokens = common_tokenize(g_ctx, prompt, true, true);
    if ((int) tokens.size() + std::max(256, (int) n_predict / 2) >= g_n_ctx) return 2;

    // reuse common prefix
    size_t keep = 0;
    while (keep < g_cache.size() && keep < tokens.size() && g_cache[keep] == tokens[keep]) keep++;
    if (keep == tokens.size()) keep--; // must decode at least one token to get logits
    llama_memory_t mem = llama_get_memory(g_ctx);
    if (!llama_memory_seq_rm(mem, 0, (llama_pos) keep, -1)) {
        llama_memory_clear(mem, false);
        keep = 0;
    }
    g_cache.resize(keep);

    for (size_t i = keep; i < tokens.size(); i += BATCH) {
        int cnt = (int) std::min((size_t) BATCH, tokens.size() - i);
        if (llama_decode(g_ctx, llama_batch_get_one(tokens.data() + i, cnt)) != 0) {
            llama_memory_clear(mem, false);
            g_cache.clear();
            return 3;
        }
        g_cache.insert(g_cache.end(), tokens.begin() + i, tokens.begin() + i + cnt);
    }

    if (g_smpl) common_sampler_free(g_smpl);
    common_params_sampling sp;
    sp.temp           = temp;
    sp.top_k          = 40;
    sp.top_p          = 0.9f;
    sp.min_p          = 0.05f;
    sp.penalty_repeat = 1.1f;
    sp.penalty_last_n = 128;
    g_smpl = common_sampler_init(g_model, sp);

    g_remaining = n_predict;
    g_pending.clear();
    return 0;
}

// Returns UTF-8 bytes of the next piece (may be empty), or null when finished.
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_basira_ai_llm_LlamaNative_next(JNIEnv * env, jobject) {
    if (!g_ctx || !g_smpl) return nullptr;
    if (g_remaining <= 0 || (int) g_cache.size() >= g_n_ctx - 2) {
        if (!g_pending.empty()) { auto r = string_to_bytes(env, g_pending); g_pending.clear(); return r; }
        return nullptr;
    }
    llama_token tok = common_sampler_sample(g_smpl, g_ctx, -1);
    common_sampler_accept(g_smpl, tok, true);
    if (llama_vocab_is_eog(llama_model_get_vocab(g_model), tok)) {
        g_remaining = 0;
        if (!g_pending.empty()) { auto r = string_to_bytes(env, g_pending); g_pending.clear(); return r; }
        return nullptr;
    }
    if (llama_decode(g_ctx, llama_batch_get_one(&tok, 1)) != 0) return nullptr;
    g_cache.push_back(tok);
    g_remaining--;

    g_pending += common_token_to_piece(g_ctx, tok);
    if (!utf8_complete(g_pending)) return string_to_bytes(env, "");
    auto r = string_to_bytes(env, g_pending);
    g_pending.clear();
    return r;
}

extern "C" JNIEXPORT void JNICALL
Java_com_basira_ai_llm_LlamaNative_unload(JNIEnv *, jobject) {
    free_all();
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_basira_ai_llm_LlamaNative_info(JNIEnv * env, jobject) {
    std::string s = llama_print_system_info();
    if (g_model) {
        char desc[128];
        llama_model_desc(g_model, desc, sizeof(desc));
        s = std::string(desc) + " | " + s;
    }
    return env->NewStringUTF(s.c_str());
}
