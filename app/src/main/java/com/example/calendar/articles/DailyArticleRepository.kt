package com.example.calendar.articles

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

data class DailyArticleFeed(val fetchedOn: LocalDate, val articles: List<DailyArticle>)

interface DailyArticleStore {
    var feed: DailyArticleFeed?
    fun articleFor(date: LocalDate): DailyArticle?
    fun saveArticle(date: LocalDate, article: DailyArticle)
}

class DailyArticleRepository(
    private val store: DailyArticleStore,
    private val today: () -> LocalDate = { LocalDate.now() },
    private val loadDetails: (DailyArticle) -> DailyArticle = ::fetchEprintArticleDetails,
    private val fetch: () -> List<DailyArticle> = ::fetchEprintArticles
) {
    private val mutex = Mutex()

    suspend fun articleFor(date: LocalDate): DailyArticle = withContext(Dispatchers.IO) {
        mutex.withLock {
            store.articleFor(date)?.let { return@withLock withSummary(date, it) }
            val cached = store.feed
            val articles = if (cached?.fetchedOn == today() && cached.articles.isNotEmpty()) cached.articles
            else try {
                val fresh = fetch().distinctBy { it.url }.sortedBy { it.url }
                if (fresh.isEmpty()) throw IOException("Empty ePrint feed")
                store.feed = DailyArticleFeed(today(), fresh)
                fresh
            } catch (failure: IOException) {
                cached?.articles?.takeIf { it.isNotEmpty() } ?: throw failure
            }
            // A recommendation for the selected calendar day, not its publication date.
            val article = articles[Math.floorMod(date.toEpochDay(), articles.size.toLong()).toInt()]
            withSummary(date, article)
        }
    }

    private fun withSummary(date: LocalDate, article: DailyArticle): DailyArticle {
        val detailed = if (article.summary.isNotBlank()) article else try {
            loadDetails(article).copy(title = article.title, url = article.url)
        } catch (_: IOException) { article }
        store.saveArticle(date, detailed)
        return detailed
    }
}

private fun fetchEprintArticles(): List<DailyArticle> {
    return try { parseEprintFeed(fetchEprintText("https://eprint.iacr.org/rss/rss.xml?order=recent")) }
    catch (failure: org.xmlpull.v1.XmlPullParserException) { throw IOException("Invalid ePrint feed", failure) }
}

private fun fetchEprintArticleDetails(article: DailyArticle): DailyArticle {
    val url = eprintArticleUrl(article.url) ?: throw IOException("Invalid article URL")
    return article.copy(summary = articleSummaryFromPage(fetchEprintText(url)))
}

private fun fetchEprintText(url: String): String {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml, text/html")
        if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("ePrint HTTP ${connection.responseCode}")
        val bytes = connection.inputStream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count == -1) break
                if (output.size() + count > 2 * 1024 * 1024) throw IOException("ePrint feed is too large")
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        return bytes.toString(Charsets.UTF_8)
    } finally {
        connection.disconnect()
    }
}
