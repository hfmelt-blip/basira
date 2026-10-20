package com.basira.ai.web

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

data class SearchResult(val title: String, val url: String, val snippet: String)

/** البحث بالنت (DuckDuckGo) وقراءة الصفحات. */
object Web {
    private const val UA =
        "Mozilla/5.0 (Linux; Android 15; SM-S938B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun search(query: String, n: Int = 3): List<SearchResult> {
        val req = Request.Builder()
            .url("https://html.duckduckgo.com/html/")
            .post(FormBody.Builder().add("q", query).add("kl", "wt-wt").build())
            .header("User-Agent", UA)
            .header("Accept-Language", "ar,en;q=0.8")
            .build()
        val html = client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw Exception("البحث رجع خطأ ${r.code}")
            r.body?.string() ?: ""
        }
        val doc = Jsoup.parse(html)
        return doc.select("div.result").filterNot { it.hasClass("result--ad") }.mapNotNull { el ->
            val a = el.selectFirst("a.result__a") ?: return@mapNotNull null
            val url = decodeLink(a.attr("href")) ?: return@mapNotNull null
            SearchResult(a.text(), url, el.selectFirst(".result__snippet")?.text() ?: "")
        }.distinctBy { it.url }.take(n)
    }

    private fun decodeLink(href: String): String? {
        if (href.startsWith("http") && !href.contains("duckduckgo.com/l/")) return href
        val m = Regex("uddg=([^&]+)").find(href) ?: return null
        return URLDecoder.decode(m.groupValues[1], "UTF-8")
    }

    /** يرجع (العنوان، النص) */
    fun fetch(url: String): Pair<String, String> {
        val req = Request.Builder().url(url).header("User-Agent", UA).header("Accept-Language", "ar,en;q=0.8").build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw Exception("الصفحة رجعت خطأ ${r.code}")
            val type = r.header("Content-Type") ?: ""
            val body = r.body?.string() ?: ""
            if (!type.contains("html")) {
                if (type.contains("text")) return url to body.take(200_000)
                throw Exception("الرابط مو صفحة نصية")
            }
            val doc = Jsoup.parse(body, url)
            doc.select("script,style,noscript,nav,footer,header,aside,form,svg,iframe").remove()
            val title = doc.title().ifBlank { url }
            val main = doc.selectFirst("article") ?: doc.selectFirst("main") ?: doc.body() ?: return title to ""
            val parts = main.select("h1,h2,h3,h4,p,li,td,pre,blockquote")
                .map { it.ownText().ifBlank { it.text() }.trim() }
                .filter { it.length > 1 }
                .distinct()
            val text = if (parts.sumOf { it.length } > 300) parts.joinToString("\n") else main.text()
            return title to text.take(200_000)
        }
    }
}
