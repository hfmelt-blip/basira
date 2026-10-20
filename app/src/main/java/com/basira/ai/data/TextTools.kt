package com.basira.ai.data

object TextTools {
    private val diacritics = Regex("[\\u064B-\\u0652\\u0640]")
    private val word = Regex("[\\p{L}\\p{N}]{2,}")
    private val prefixes = listOf("وال", "بال", "كال", "فال", "لل", "ال")

    val STOP = setOf(
        "في", "من", "على", "الى", "عن", "مع", "هذا", "هذه", "ذلك", "التي", "الذي", "هو", "هي", "ما", "ماذا",
        "كيف", "هل", "لا", "ان", "او", "ثم", "كان", "شنو", "شلون", "اكو", "ماكو", "the", "and", "for", "what", "how",
        "is", "are", "of", "to", "in", "on", "with", "اريد", "ممكن", "عندي", "يعني"
    )

    fun normalize(s: String): String = s.lowercase()
        .replace(diacritics, "")
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
        .replace('ة', 'ه').replace('ى', 'ي').replace('ؤ', 'و').replace('ئ', 'ي')

    fun tokens(normalized: String): List<String> = word.findAll(normalized).map { m ->
        var t = m.value
        for (p in prefixes) {
            if (t.length > p.length + 2 && t.startsWith(p)) { t = t.removePrefix(p); break }
        }
        t
    }.toList()

    /** تقطيع النص لأجزاء ~900 حرف مع تداخل بسيط. */
    fun chunk(text: String, size: Int = 900, overlap: Int = 150): List<String> {
        val paras = text.split(Regex("\\n+")).map { it.trim() }.filter { it.isNotEmpty() }
        val out = mutableListOf<String>()
        var cur = StringBuilder()
        for (para in paras) {
            var p = para
            while (p.length > size) {
                if (cur.isNotEmpty()) { out += cur.toString(); cur = StringBuilder() }
                out += p.take(size)
                p = p.drop(size - overlap)
            }
            if (cur.length + p.length + 1 > size && cur.isNotEmpty()) {
                val tail = cur.takeLast(overlap)
                out += cur.toString()
                cur = StringBuilder(tail).append('\n').append(p)
            } else {
                if (cur.isNotEmpty()) cur.append('\n')
                cur.append(p)
            }
        }
        if (cur.isNotBlank()) out += cur.toString()
        return out.filter { it.trim().length > 30 }
    }

    /** يشيل أي تفكير داخلي لو النموذج طلّعه */
    fun cleanAnswer(s: String): String =
        s.replace(Regex("<think>[\\s\\S]*?(</think>|$)"), "").trimStart()
}
