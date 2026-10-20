package com.basira.ai.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.basira.ai.data.Conversation
import com.basira.ai.data.Db
import com.basira.ai.data.DlState
import com.basira.ai.data.Hit
import com.basira.ai.data.MODEL_CATALOG
import com.basira.ai.data.ModelInfo
import com.basira.ai.data.ModelStore
import com.basira.ai.data.Prefs
import com.basira.ai.data.Source
import com.basira.ai.data.TextTools
import com.basira.ai.llm.EngineState
import com.basira.ai.llm.LlmEngine
import com.basira.ai.web.Web
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class Screen { CHAT, MODELS, KNOWLEDGE, SETTINGS }

data class SourceRef(val title: String, val url: String, val kind: String)

data class UiMessage(
    val role: String,
    val text: String,
    val sources: List<SourceRef> = emptyList(),
    val error: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val prefs = Prefs(app)
    private val db = Db(app)
    val models = ModelStore(app, prefs)

    var screen by mutableStateOf(Screen.CHAT)
    var conversations by mutableStateOf<List<Conversation>>(emptyList())
    var currentConv by mutableStateOf<Long?>(null)
    val messages = mutableStateListOf<UiMessage>()
    var generating by mutableStateOf(false)
    var status by mutableStateOf<String?>(null)
    var webEnabled by mutableStateOf(prefs.webDefault)

    val engineState = LlmEngine.state

    var localModels by mutableStateOf<List<File>>(emptyList())
    val downloads = mutableStateMapOf<String, DlState>()
    var importStatus by mutableStateOf<String?>(null)

    var sources by mutableStateOf<List<Source>>(emptyList())
    var learnStatus by mutableStateOf<String?>(null)
    var learnBusy by mutableStateOf(false)

    private var genJob: Job? = null

    init {
        refreshConversations()
        refreshModels()
        val selected = prefs.selectedModel?.let { File(it) }
        if (selected != null && selected.exists()) {
            if (LlmEngine.state.value !is EngineState.Ready) loadModel(selected)
        } else if (localModels.isEmpty()) {
            screen = Screen.MODELS
        } else {
            loadModel(localModels.first())
        }
        viewModelScope.launch {
            while (true) {
                pollDownloads()
                delay(1000)
            }
        }
    }

    // ================= المحادثة =================
    fun refreshConversations() {
        conversations = db.conversations()
    }

    fun newChat() {
        if (generating) return
        currentConv = null
        messages.clear()
        screen = Screen.CHAT
    }

    fun openConversation(id: Long) {
        if (generating) return
        currentConv = id
        messages.clear()
        messages.addAll(db.messages(id).map { UiMessage(it.role, it.content) })
        screen = Screen.CHAT
    }

    fun deleteConversation(id: Long) {
        db.deleteConversation(id)
        if (currentConv == id) newChat()
        refreshConversations()
    }

    fun renameConversation(id: Long, title: String) {
        db.renameConversation(id, title.take(80))
        refreshConversations()
    }

    fun toggleWeb() {
        webEnabled = !webEnabled
        prefs.webDefault = webEnabled
    }

    fun stop() {
        LlmEngine.stop()
    }

    fun send(text: String) {
        val msg = text.trim()
        if (msg.isEmpty() || generating) return
        if (LlmEngine.state.value !is EngineState.Ready) {
            messages.add(UiMessage("assistant", "", error = "ماكو نموذج جاهز. روح لقسم «النماذج» ونزّل أو اختار نموذج."))
            return
        }
        val convId = currentConv ?: db.newConversation(msg.take(45)).also { currentConv = it }
        val history = db.messages(convId).takeLast(16)
        db.addMessage(convId, "user", msg)
        messages.add(UiMessage("user", msg))
        messages.add(UiMessage("assistant", ""))
        val idx = messages.lastIndex
        refreshConversations()

        genJob = viewModelScope.launch {
            generating = true
            val answer = StringBuilder()
            try {
                if (webEnabled) {
                    status = "أبحث بالنت..."
                    try {
                        learnFromWeb(msg, 3) { status = it }
                    } catch (e: Exception) {
                        status = "تعذر البحث: ${e.message}"
                        delay(800)
                    }
                }
                status = "أدوّر بذاكرتي..."
                val hits = withContext(Dispatchers.IO) { db.search(msg, 4) }
                val refs = hits.distinctBy { it.sourceId }
                val userContent = buildUserContent(msg, hits, refs)
                val system = prefs.systemPrompt.replace("{name}", prefs.appName)
                val chat = listOf("system" to system) + history.map { it.role to it.content } + ("user" to userContent)

                status = "أفكر..."
                LlmEngine.generate(chat, prefs.temperature, prefs.maxAnswer).collect { piece ->
                    answer.append(piece)
                    status = null
                    messages[idx] = messages[idx].copy(text = TextTools.cleanAnswer(answer.toString()))
                }
                if (refs.isNotEmpty() && answer.isNotBlank()) {
                    messages[idx] = messages[idx].copy(sources = refs.map { SourceRef(it.title, it.url, it.kind) })
                }
            } catch (e: Exception) {
                messages[idx] = messages[idx].copy(error = e.message ?: "صار خطأ")
            } finally {
                val final = TextTools.cleanAnswer(answer.toString()).trim()
                if (final.isNotEmpty()) db.addMessage(convId, "assistant", final)
                generating = false
                status = null
                refreshConversations()
            }
        }
    }

    private fun buildUserContent(msg: String, hits: List<Hit>, refs: List<Hit>): String {
        if (hits.isEmpty()) return msg
        val sb = StringBuilder("معلومات من ذاكرتي وقاعدة معرفتي قد تفيد بالجواب:\n")
        for (h in hits) {
            val n = refs.indexOfFirst { it.sourceId == h.sourceId } + 1
            val label = if (h.kind == "memory") "تجربة/ملاحظة من المستخدم" else h.title
            sb.append("\n[").append(n).append("] (").append(label).append(")\n").append(h.text).append('\n')
        }
        sb.append("\n---\nسؤالي: ").append(msg)
        return sb.toString()
    }

    // ================= التعلّم =================
    private suspend fun learn(url: String, title: String, text: String, kind: String, chunk: Boolean = true): Int {
        val chunks = if (chunk) TextTools.chunk(text) else listOf(text.trim()).filter { it.isNotEmpty() }
        if (chunks.isEmpty()) return 0
        withContext(Dispatchers.IO) { db.addSource(url, title, kind, chunks) }
        return chunks.size
    }

    private suspend fun learnFromWeb(query: String, n: Int, onStatus: (String) -> Unit): Int {
        val results = withContext(Dispatchers.IO) { Web.search(query, n) }
        var learned = 0
        for (r in results) {
            if (withContext(Dispatchers.IO) { db.sourceExists(r.url) }) continue
            onStatus("أقرأ: ${r.title.take(50)}")
            try {
                val (title, text) = withContext(Dispatchers.IO) { Web.fetch(r.url) }
                if (learn(r.url, title.ifBlank { r.title }, text, "web") == 0 && r.snippet.isNotBlank()) {
                    learn(r.url, r.title, r.snippet, "web", chunk = false)
                }
                learned++
            } catch (e: Exception) {
                if (r.snippet.isNotBlank()) { learn(r.url, r.title, r.snippet, "web", chunk = false); learned++ }
            }
        }
        return learned
    }

    fun refreshSources() {
        sources = db.sources()
    }

    private fun runLearn(waitText: String, block: suspend () -> String) {
        if (learnBusy) return
        viewModelScope.launch {
            learnBusy = true
            learnStatus = waitText
            learnStatus = try { "✓ " + block() } catch (e: Exception) { "✗ " + (e.message ?: "خطأ") }
            learnBusy = false
            refreshSources()
        }
    }

    fun learnUrl(url: String) = runLearn("أقرأ الصفحة...") {
        val u = url.trim().let { if (it.startsWith("http")) it else "https://$it" }
        if (withContext(Dispatchers.IO) { db.sourceExists(u) }) throw Exception("هذا الرابط متعلَّم سابقاً")
        val (title, text) = withContext(Dispatchers.IO) { Web.fetch(u) }
        val n = learn(u, title, text, "web")
        if (n == 0) throw Exception("الصفحة ما بيها نص كافي")
        "تعلمت: $title ($n جزء)"
    }

    fun learnSearch(query: String) = runLearn("أبحث وأقرأ الصفحات...") {
        if (query.isBlank()) throw Exception("اكتب موضوع")
        val n = learnFromWeb(query.trim(), 5) { learnStatus = it }
        "تعلمت من $n صفحة جديدة"
    }

    fun learnMemory(title: String, text: String) = runLearn("أحفظ...") {
        if (text.isBlank()) throw Exception("اكتب التجربة")
        val t = title.ifBlank { text.take(50) }.trim()
        learn("memory:$t:${System.currentTimeMillis()}", t, text, "memory", chunk = text.length > 900)
        "حفظت التجربة، راح أرجعلها بالأسئلة اللي تخصها"
    }

    fun deleteSource(id: Long) {
        db.deleteSource(id)
        refreshSources()
    }

    // ================= النماذج =================
    fun refreshModels() {
        localModels = models.localModels()
    }

    private fun pollDownloads() {
        var finished = false
        for (m in MODEL_CATALOG) {
            val before = downloads[m.file]
            val st = models.downloadState(m)
            downloads[m.file] = st
            if (before is DlState.Running && st is DlState.None) finished = true
        }
        if (finished) {
            refreshModels()
            if (LlmEngine.state.value !is EngineState.Ready) localModels.firstOrNull()?.let { loadModel(it) }
        }
    }

    fun download(m: ModelInfo) {
        if (models.freeSpaceMb() < m.sizeMb + 300) {
            downloads[m.file] = DlState.Failed("المساحة الفارغة ما تكفي، تحتاج تقريباً ${m.sizeMb + 300}MB")
            return
        }
        models.startDownload(m)
        downloads[m.file] = DlState.Running(0f, 0)
    }

    fun cancelDownload(m: ModelInfo) {
        models.cancelDownload(m)
        downloads[m.file] = DlState.None
    }

    fun loadModel(file: File) {
        prefs.selectedModel = file.absolutePath
        viewModelScope.launch {
            LlmEngine.load(file.absolutePath, file.nameWithoutExtension, prefs.contextSize, prefs.threads)
        }
    }

    fun deleteModel(file: File) {
        viewModelScope.launch {
            val st = LlmEngine.state.value
            if (st is EngineState.Ready && st.path == file.absolutePath) LlmEngine.unload()
            file.delete()
            if (prefs.selectedModel == file.absolutePath) prefs.selectedModel = null
            refreshModels()
        }
    }

    fun importModel(uri: Uri) {
        viewModelScope.launch {
            importStatus = "أنسخ الملف..."
            try {
                val f = withContext(Dispatchers.IO) {
                    models.import(uri) { mb -> importStatus = "أنسخ الملف... ${mb}MB" }
                }
                importStatus = "✓ تم استيراد ${f.name}"
                refreshModels()
                loadModel(f)
            } catch (e: Exception) {
                importStatus = "✗ ${e.message}"
            }
        }
    }

    // ================= الإعدادات =================
    fun saveSettings(name: String, prompt: String, temp: Float, ctx: Int, threads: Int, maxAnswer: Int) {
        val reload = ctx != prefs.contextSize || threads != prefs.threads
        prefs.appName = name.ifBlank { "بصيرة" }
        prefs.systemPrompt = prompt.ifBlank { Prefs.DEFAULT_PROMPT }
        prefs.temperature = temp
        prefs.contextSize = ctx
        prefs.threads = threads
        prefs.maxAnswer = maxAnswer
        if (reload) {
            val st = LlmEngine.state.value
            if (st is EngineState.Ready) loadModel(File(st.path))
        }
    }
}
