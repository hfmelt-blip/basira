package com.basira.ai.llm

/** الجسر مع محرك llama.cpp المكتوب بـ C++ (basira_jni.cpp). */
object LlamaNative {
    init {
        System.loadLibrary("basira")
    }

    external fun init()

    /** 0 = تم، 1 = الملف ما انقرى، 2 = ما كفت الذاكرة */
    external fun load(path: String, nCtx: Int, nThreads: Int): Int

    /** 0 = تم، 1 = ماكو نموذج، 2 = طويل جداً، 3 = فشل */
    external fun start(roles: Array<String>, contents: Array<ByteArray>, temp: Float, nPredict: Int): Int

    /** يرجع القطعة الجاية من الجواب، أو null إذا خلص */
    external fun next(): ByteArray?

    external fun unload()

    external fun info(): String
}
