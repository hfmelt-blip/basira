package com.basira.ai.data

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import java.io.File

data class ModelInfo(
    val name: String,
    val description: String,
    val url: String,
    val file: String,
    val sizeMb: Int,
)

val MODEL_CATALOG = listOf(
    ModelInfo(
        "Qwen3 4B  (موصى به)",
        "الأذكى، عربيته جيدة. يحتاج تقريباً 3GB رام.",
        "https://huggingface.co/unsloth/Qwen3-4B-Instruct-2507-GGUF/resolve/main/Qwen3-4B-Instruct-2507-Q4_0.gguf",
        "Qwen3-4B-Instruct-2507-Q4_0.gguf", 2380
    ),
    ModelInfo(
        "Qwen3 1.7B  (سريع)",
        "أسرع بضعفين وأخف، بس أقل دقة.",
        "https://huggingface.co/unsloth/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q4_0.gguf",
        "Qwen3-1.7B-Q4_0.gguf", 1060
    ),
)

sealed interface DlState {
    data object None : DlState
    data class Running(val progress: Float, val doneMb: Int) : DlState
    data class Failed(val reason: String) : DlState
}

class ModelStore(private val context: Context, private val prefs: Prefs) {
    val dir: File = (context.getExternalFilesDir("models") ?: File(context.filesDir, "models")).apply { mkdirs() }
    private val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    fun localModels(): List<File> =
        dir.listFiles { f -> f.isFile && f.name.endsWith(".gguf") }?.sortedBy { it.name } ?: emptyList()

    fun freeSpaceMb(): Long = StatFs(dir.path).availableBytes / (1024 * 1024)

    fun startDownload(m: ModelInfo) {
        File(dir, m.file + ".download").delete()
        val req = DownloadManager.Request(Uri.parse(m.url))
            .setTitle("بصيرة: تنزيل ${m.name}")
            .setDescription("نموذج الذكاء الصناعي")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setDestinationInExternalFilesDir(context, "models", m.file + ".download")
        prefs.setDownloadId(m.file, dm.enqueue(req))
    }

    fun cancelDownload(m: ModelInfo) {
        val id = prefs.downloadId(m.file)
        if (id >= 0) dm.remove(id)
        prefs.clearDownload(m.file)
        File(dir, m.file + ".download").delete()
    }

    /** يحدّث حالة التنزيل، وإذا خلص يغيّر اسم الملف. */
    fun downloadState(m: ModelInfo): DlState {
        val id = prefs.downloadId(m.file)
        if (id < 0) return DlState.None
        dm.query(DownloadManager.Query().setFilterById(id)).use { c ->
            if (!c.moveToFirst()) {
                prefs.clearDownload(m.file); return DlState.None
            }
            val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            return when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    File(dir, m.file + ".download").renameTo(File(dir, m.file))
                    prefs.clearDownload(m.file)
                    DlState.None
                }
                DownloadManager.STATUS_FAILED -> {
                    prefs.clearDownload(m.file)
                    DlState.Failed("فشل التنزيل، تأكد من النت والمساحة وحاول مرة ثانية")
                }
                else -> {
                    val totalBytes = if (total > 0) total else m.sizeMb * 1024L * 1024L
                    DlState.Running((done.toFloat() / totalBytes).coerceIn(0f, 1f), (done / (1024 * 1024)).toInt())
                }
            }
        }
    }

    /** نسخ ملف GGUF من ذاكرة التلفون. */
    fun import(uri: Uri, onProgress: (Int) -> Unit): File {
        var name = "model.gguf"
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) name = c.getString(idx)
        }
        if (!name.endsWith(".gguf", true)) throw Exception("لازم يكون الملف بصيغة .gguf")
        val tmp = File(dir, "$name.download")
        context.contentResolver.openInputStream(uri)!!.use { input ->
            tmp.outputStream().use { out ->
                val buf = ByteArray(1 shl 20)
                var copied = 0L
                while (true) {
                    val r = input.read(buf)
                    if (r < 0) break
                    out.write(buf, 0, r)
                    copied += r
                    onProgress((copied / (1024 * 1024)).toInt())
                }
            }
        }
        val dest = File(dir, name)
        tmp.renameTo(dest)
        return dest
    }
}
