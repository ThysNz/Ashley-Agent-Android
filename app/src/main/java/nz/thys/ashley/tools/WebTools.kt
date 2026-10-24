package nz.thys.ashley.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URLDecoder

/** Port of Tools/WebTools.cs: web_fetch and web_search (DuckDuckGo's HTML page, same as the desktop). */
object WebTools {

    private const val UA = "Mozilla/5.0 (Linux; Android 16) AshleyMobile/0.1"

    fun create(http: OkHttpClient): List<Tool> = listOf(
        Tool(
            "web_fetch",
            "Download a web page or text file and return its text (HTML is converted to plain text).",
            Schema.obj(
                Prop("url", "string", "http:// or https:// address.", true),
                Prop("max_chars", "integer", "Cut the result to this many characters. Default 12000, max 40000."),
            ),
        ) { a -> fetch(http, a.req("url"), (a.int("max_chars") ?: 12000).coerceIn(500, 40000)) },

        Tool(
            "web_search",
            "Search the web. Returns titles, URLs and snippets; use web_fetch to read a result.",
            Schema.obj(Prop("query", "string", "What to search for.", true)),
        ) { a -> search(http, a.req("query")) },
    )

    suspend fun fetch(http: OkHttpClient, url: String, maxChars: Int): String = withContext(Dispatchers.IO) {
        val u = url.toHttpUrlOrNull() ?: return@withContext "Error: '$url' is not an http(s) address."
        val req = Request.Builder().url(u).header("User-Agent", UA).build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext "Error: HTTP ${resp.code} from $url"
            val type = resp.header("Content-Type")?.lowercase() ?: ""
            val textual = type.startsWith("text/") || "json" in type || "xml" in type || "javascript" in type || type.isEmpty()
            if (!textual) return@withContext "Error: $url is $type, not text."
            val body = resp.body?.string() ?: ""
            val text = if ("html" in type || body.trimStart().startsWith("<")) {
                val doc = Jsoup.parse(body, url)
                doc.select("script, style, noscript, svg, nav, footer").remove()
                val title = doc.title()
                (if (title.isNotBlank()) "$title\n\n" else "") + doc.body().wholeText()
                    .lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
            } else body
            if (text.length > maxChars) text.take(maxChars) + "\n... [cut at $maxChars characters]" else text
        }
    }

    suspend fun search(http: OkHttpClient, query: String): String = withContext(Dispatchers.IO) {
        val u = "https://html.duckduckgo.com/html/".toHttpUrlOrNull()!!.newBuilder().addQueryParameter("q", query).build()
        val req = Request.Builder().url(u).header("User-Agent", UA).build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext "Error: search failed (HTTP ${resp.code}). Try web_fetch on a known URL."
            val doc = Jsoup.parse(resp.body?.string() ?: "")
            val results = doc.select(".result").mapNotNull { r ->
                val a = r.selectFirst("a.result__a") ?: return@mapNotNull null
                var href = a.attr("href")
                // DuckDuckGo wraps links: //duckduckgo.com/l/?uddg=<encoded url>
                Regex("[?&]uddg=([^&]+)").find(href)?.let { href = URLDecoder.decode(it.groupValues[1], "UTF-8") }
                val snippet = r.selectFirst(".result__snippet")?.text() ?: ""
                "${a.text()}\n$href\n$snippet"
            }.take(8)
            if (results.isEmpty()) "No results (the search page may have changed). Try web_fetch on a known URL."
            else results.joinToString("\n\n")
        }
    }
}
