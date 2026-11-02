package com.mykolzyai.studio

import org.jsoup.Jsoup
import java.net.URLEncoder

/**
 * Поиск по открытой выдаче DuckDuckGo HTML.
 * Не требует ключа API, но доступность зависит от сайта и сети.
 */
object WebSearchClient {
    data class SearchResult(val title: String, val url: String, val snippet: String)

    fun search(query: String, limit: Int = 5): List<SearchResult> {
        require(query.isNotBlank()) { "Введите поисковый запрос." }
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val document = Jsoup.connect("https://html.duckduckgo.com/html/?q=$encoded")
            .userAgent("Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
            .timeout(12_000)
            .followRedirects(true)
            .get()

        return document.select(".result").mapNotNull { item ->
            val link = item.selectFirst("a.result__a") ?: return@mapNotNull null
            val href = link.absUrl("href").ifBlank { link.attr("href") }
            if (!href.startsWith("http")) return@mapNotNull null
            val title = link.text().trim()
            val snippet = item.selectFirst(".result__snippet")?.text()?.trim().orEmpty()
            if (title.isBlank()) null else SearchResult(title, href, snippet)
        }.distinctBy { it.url }.take(limit.coerceIn(1, 10))
    }
}
