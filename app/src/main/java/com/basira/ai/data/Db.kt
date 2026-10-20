package com.basira.ai.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Conversation(val id: Long, val title: String, val updated: Long)
data class StoredMessage(val role: String, val content: String)
data class Source(val id: Long, val url: String, val title: String, val kind: String, val chunks: Int)
data class Hit(val sourceId: Long, val title: String, val url: String, val kind: String, val text: String, val score: Double)

/** قاعدة البيانات: المحادثات + المعرفة (صفحات النت، التجارب، الملفات). */
class Db(context: Context) : SQLiteOpenHelper(context, "basira.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE conversations(id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, updated INTEGER)")
        db.execSQL("CREATE TABLE messages(id INTEGER PRIMARY KEY AUTOINCREMENT, conv_id INTEGER, role TEXT, content TEXT, created INTEGER)")
        db.execSQL("CREATE TABLE sources(id INTEGER PRIMARY KEY AUTOINCREMENT, url TEXT, title TEXT, kind TEXT, created INTEGER)")
        db.execSQL("CREATE TABLE chunks(id INTEGER PRIMARY KEY AUTOINCREMENT, source_id INTEGER, text TEXT, norm TEXT)")
        db.execSQL("CREATE INDEX idx_msg ON messages(conv_id)")
        db.execSQL("CREATE INDEX idx_chunk ON chunks(source_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    // ---------- المحادثات ----------
    fun newConversation(title: String): Long = writableDatabase.insert("conversations", null, ContentValues().apply {
        put("title", title.ifBlank { "محادثة جديدة" })
        put("updated", System.currentTimeMillis())
    })

    fun conversations(): List<Conversation> =
        readableDatabase.rawQuery("SELECT id,title,updated FROM conversations ORDER BY updated DESC", null).use { c ->
            buildList { while (c.moveToNext()) add(Conversation(c.getLong(0), c.getString(1), c.getLong(2))) }
        }

    fun renameConversation(id: Long, title: String) {
        writableDatabase.update("conversations", ContentValues().apply { put("title", title) }, "id=?", arrayOf("$id"))
    }

    fun deleteConversation(id: Long) {
        writableDatabase.delete("messages", "conv_id=?", arrayOf("$id"))
        writableDatabase.delete("conversations", "id=?", arrayOf("$id"))
    }

    fun messages(convId: Long): List<StoredMessage> =
        readableDatabase.rawQuery("SELECT role,content FROM messages WHERE conv_id=? ORDER BY id", arrayOf("$convId")).use { c ->
            buildList { while (c.moveToNext()) add(StoredMessage(c.getString(0), c.getString(1))) }
        }

    fun addMessage(convId: Long, role: String, content: String) {
        val now = System.currentTimeMillis()
        writableDatabase.insert("messages", null, ContentValues().apply {
            put("conv_id", convId); put("role", role); put("content", content); put("created", now)
        })
        writableDatabase.update("conversations", ContentValues().apply { put("updated", now) }, "id=?", arrayOf("$convId"))
    }

    // ---------- المعرفة ----------
    fun sourceExists(url: String): Boolean =
        readableDatabase.rawQuery("SELECT 1 FROM sources WHERE url=?", arrayOf(url)).use { it.moveToFirst() }

    fun addSource(url: String, title: String, kind: String, chunks: List<String>): Long {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val id = db.insert("sources", null, ContentValues().apply {
                put("url", url); put("title", title); put("kind", kind); put("created", System.currentTimeMillis())
            })
            for (t in chunks) {
                db.insert("chunks", null, ContentValues().apply {
                    put("source_id", id); put("text", t); put("norm", TextTools.normalize(t))
                })
            }
            db.setTransactionSuccessful()
            return id
        } finally {
            db.endTransaction()
        }
    }

    fun sources(): List<Source> = readableDatabase.rawQuery(
        """SELECT s.id,s.url,s.title,s.kind,COUNT(c.id) FROM sources s LEFT JOIN chunks c ON c.source_id=s.id
           GROUP BY s.id ORDER BY s.created DESC""", null
    ).use { c ->
        buildList { while (c.moveToNext()) add(Source(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getInt(4))) }
    }

    fun deleteSource(id: Long) {
        writableDatabase.delete("chunks", "source_id=?", arrayOf("$id"))
        writableDatabase.delete("sources", "id=?", arrayOf("$id"))
    }

    /** بحث بالكلمات مع وزن للكلمات النادرة (تشبه BM25 مبسطة). */
    fun search(query: String, k: Int = 4): List<Hit> {
        val qTokens = TextTools.tokens(TextTools.normalize(query)).filter { it !in TextTools.STOP }.toSet()
        if (qTokens.isEmpty()) return emptyList()
        data class Row(val sid: Long, val title: String, val url: String, val kind: String, val text: String, val tokens: Set<String>)
        val rows = readableDatabase.rawQuery(
            "SELECT s.id,s.title,s.url,s.kind,c.text,c.norm FROM chunks c JOIN sources s ON s.id=c.source_id", null
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(Row(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4),
                    TextTools.tokens(c.getString(5)).toSet()))
            }
        }
        if (rows.isEmpty()) return emptyList()
        val n = rows.size.toDouble()
        val idf = qTokens.associateWith { t ->
            val df = rows.count { t in it.tokens }
            kotlin.math.ln(1 + n / (1 + df))
        }
        val maxScore = idf.values.sum()
        return rows.mapNotNull { r ->
            val matched = qTokens.filter { it in r.tokens }
            if (matched.isEmpty()) return@mapNotNull null
            val score = matched.sumOf { idf[it] ?: 0.0 } / maxScore
            if (score < 0.34) null else Hit(r.sid, r.title, r.url, r.kind, r.text, score)
        }.sortedByDescending { it.score }.take(k)
    }
}
