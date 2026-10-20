package com.basira.ai.llm

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

sealed interface EngineState {
    data object Empty : EngineState
    data class Loading(val name: String) : EngineState
    data class Ready(val name: String, val path: String) : EngineState
    data class Failed(val message: String) : EngineState
}

class LlmException(message: String) : Exception(message)

/** يدير النموذج المحمّل. كل العمليات على خيط واحد لأن المحرك مو آمن للتوازي. */
object LlmEngine {
    private val dispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(r, "llama").apply { priority = Thread.MAX_PRIORITY }
    }.asCoroutineDispatcher()

    private val _state = MutableStateFlow<EngineState>(EngineState.Empty)
    val state: StateFlow<EngineState> = _state

    @Volatile private var stopRequested = false
    private var nativeReady = false

    suspend fun load(path: String, name: String, nCtx: Int, threads: Int) = withContext(dispatcher) {
        _state.value = EngineState.Loading(name)
        try {
            if (!nativeReady) {
                LlamaNative.init()
                nativeReady = true
            }
            when (LlamaNative.load(path, nCtx, threads)) {
                0 -> _state.value = EngineState.Ready(name, path)
                1 -> _state.value = EngineState.Failed("ملف النموذج تالف أو مو مدعوم")
                else -> _state.value = EngineState.Failed("ما كفت الذاكرة، جرّب نموذج أصغر أو قلل حجم السياق")
            }
        } catch (t: Throwable) {
            _state.value = EngineState.Failed("خطأ بتحميل النموذج: ${t.message}")
        }
    }

    suspend fun unload() = withContext(dispatcher) {
        if (nativeReady) LlamaNative.unload()
        _state.value = EngineState.Empty
    }

    fun stop() {
        stopRequested = true
    }

    /**
     * messages: أزواج (الدور، النص). أول عنصر هو system.
     * إذا المحادثة أطول من الذاكرة نحذف أقدم الرسائل تلقائياً.
     */
    fun generate(messages: List<Pair<String, String>>, temp: Float, maxTokens: Int): Flow<String> = flow {
        stopRequested = false
        if (_state.value !is EngineState.Ready) throw LlmException("ماكو نموذج محمّل")

        var msgs = messages
        var rc: Int
        while (true) {
            rc = LlamaNative.start(
                msgs.map { it.first }.toTypedArray(),
                msgs.map { it.second.toByteArray(Charsets.UTF_8) }.toTypedArray(),
                temp, maxTokens
            )
            if (rc != 2) break
            if (msgs.size > 3) {
                msgs = listOf(msgs.first()) + msgs.drop(3) // نحذف أقدم سؤال وجواب
            } else {
                val last = msgs.last()
                if (last.second.length < 400) break
                msgs = msgs.dropLast(1) + (last.first to last.second.take(last.second.length / 2))
            }
        }
        when (rc) {
            0 -> Unit
            1 -> throw LlmException("ماكو نموذج محمّل")
            2 -> throw LlmException("الرسالة طويلة جداً على ذاكرة النموذج، زيد حجم السياق من الإعدادات")
            else -> throw LlmException("صار خطأ أثناء المعالجة، حاول مرة ثانية")
        }
        while (!stopRequested) {
            val bytes = LlamaNative.next() ?: break
            if (bytes.isNotEmpty()) emit(String(bytes, Charsets.UTF_8))
        }
    }.flowOn(dispatcher)
}
