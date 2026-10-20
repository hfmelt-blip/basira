package com.basira.ai.data

import android.content.Context

class Prefs(context: Context) {
    private val p = context.getSharedPreferences("basira", Context.MODE_PRIVATE)

    var appName: String
        get() = p.getString("app_name", "بصيرة")!!
        set(v) = p.edit().putString("app_name", v).apply()

    var systemPrompt: String
        get() = p.getString("system_prompt", DEFAULT_PROMPT)!!
        set(v) = p.edit().putString("system_prompt", v).apply()

    var temperature: Float
        get() = p.getFloat("temperature", 0.7f)
        set(v) = p.edit().putFloat("temperature", v).apply()

    var contextSize: Int
        get() = p.getInt("ctx", 4096)
        set(v) = p.edit().putInt("ctx", v).apply()

    var threads: Int
        get() = p.getInt("threads", 0) // 0 = تلقائي
        set(v) = p.edit().putInt("threads", v).apply()

    var maxAnswer: Int
        get() = p.getInt("max_answer", 1024)
        set(v) = p.edit().putInt("max_answer", v).apply()

    var selectedModel: String?
        get() = p.getString("model", null)
        set(v) = p.edit().putString("model", v).apply()

    var webDefault: Boolean
        get() = p.getBoolean("web", false)
        set(v) = p.edit().putBoolean("web", v).apply()

    fun downloadId(file: String): Long = p.getLong("dl_$file", -1)
    fun setDownloadId(file: String, id: Long) = p.edit().putLong("dl_$file", id).apply()
    fun clearDownload(file: String) = p.edit().remove("dl_$file").apply()

    companion object {
        const val DEFAULT_PROMPT =
            "أنت {name}، مساعد ذكي يعمل محلياً على هاتف المستخدم بدون إنترنت. أجب بلغة المستخدم (العربية افتراضياً) " +
            "بوضوح ودقة وبشكل مباشر. إذا زُوّدت بمعلومات من قاعدة المعرفة أو الإنترنت فاعتمد عليها واذكر رقم المصدر مثل [1]. " +
            "إذا لم تكن متأكداً من معلومة فقل ذلك صراحة ولا تخترع."
    }
}
