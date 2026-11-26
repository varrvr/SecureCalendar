package com.example.calendar.articles

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

class DailyArticleTest {
    private val day = LocalDate.of(2026, 11, 25)
    private val articles = listOf(DailyArticle("First", "https://eprint.iacr.org/2026/1", "First abstract."),
        DailyArticle("Second", "https://eprint.iacr.org/2026/2", "Second abstract."))

    @Test fun `RSS titles support entities and CDATA and links are validated`() {
        val feed = """<?xml version="1.0"?><rss><channel>
            <item><title>A &amp; B</title><link>http://eprint.iacr.org/2026/1</link></item>
            <item><title><![CDATA[ A < B ]]></title><link>https://eprint.iacr.org/2026/2</link></item>
            <item><title>Wrong host</title><link>https://example.com/2026/3</link></item>
            <item><title>Duplicate</title><link>https://eprint.iacr.org/2026/1</link></item>
            <item><title>Not a paper</title><link>https://eprint.iacr.org/rss/</link></item>
            </channel></rss>"""
        val parsed = parseEprintFeed(feed)
        assertEquals(listOf("A & B", "A < B"), parsed.map { it.title })
        assertEquals(articles.map { it.url }, parsed.map { it.url })
    }

    @Test fun `untrusted or non article URLs are rejected`() {
        listOf("javascript:alert(1)", "https://eprint.iacr.org.evil.com/2026/1",
            "https://user@eprint.iacr.org/2026/1", "https://eprint.iacr.org:123/2026/1",
            "https://eprint.iacr.org/2026/1?redirect=bad", "https://eprint.iacr.org/2026/1.pdf")
            .forEach { assertNull(it, eprintArticleUrl(it)) }
    }

    @Test(expected = IOException::class) fun `DTD is rejected`() {
        parseEprintFeed("""<!DOCTYPE rss [<!ENTITY x SYSTEM "file:///nonexistent">]><rss><channel/></rss>""")
    }

    @Test fun `same date remains fixed and a day's feed is downloaded only once`() = runBlocking {
        val store = MemoryStore()
        var calls = 0
        val repo = DailyArticleRepository(store, { day }) { calls++; articles }
        val first = repo.articleFor(day)
        assertEquals(first, repo.articleFor(day))
        assertNotEquals(first, repo.articleFor(day.plusDays(1)))
        assertEquals(1, calls)
        val recreated = DailyArticleRepository(store, { day }) { error("Should use persisted article") }
        assertEquals(first, recreated.articleFor(day))
    }

    @Test fun `next day's feed refresh preserves already selected articles`() = runBlocking {
        val store = MemoryStore()
        var now = day
        var calls = 0
        val repo = DailyArticleRepository(store, { now }) {
            calls++
            if (calls == 1) articles else listOf(DailyArticle("New", "https://eprint.iacr.org/2026/3", "New abstract."))
        }
        val first = repo.articleFor(day)
        now = day.plusDays(1)
        assertEquals("New", repo.articleFor(now).title)
        assertEquals(first, repo.articleFor(day))
        assertEquals(2, calls)
    }

    @Test fun `offline uses previously cached feed`() = runBlocking {
        val store = MemoryStore().apply { feed = DailyArticleFeed(day.minusDays(1), articles) }
        val repo = DailyArticleRepository(store, { day }) { throw IOException("Offline") }
        assertTrue(repo.articleFor(day) in articles)
        assertEquals(day.minusDays(1), store.feed?.fetchedOn)
    }

    @Test(expected = IOException::class) fun `empty feed fails without storing a broken recommendation`() = runBlocking {
        val store = MemoryStore()
        DailyArticleRepository(store, { day }) { emptyList() }.articleFor(day)
        assertNull(store.articleFor(day))
    }

    @Test fun `RSS carries short author abstract without HTML`() {
        val parsed = parseEprintFeed("""<rss><channel><item><title>Paper</title>
            <link>https://eprint.iacr.org/2026/1</link><description><![CDATA[
            <p><b>Abstract:</b> We study A &amp; B. We provide a proof. Extra details.</p>
            ]]></description></item></channel></rss>""")
        assertEquals("We study A & B. We provide a proof.", parsed.single().summary)
    }

    @Test fun `page abstract excludes metadata and is bounded`() {
        assertEquals("First sentence. Second sentence.", articleSummaryFromPage("""
            <h5 class="heading">Abstract</h5><div><p>First sentence. Second sentence. Third sentence.</p></div>
            <h5>Metadata</h5><p>Not part of the abstract.</p>"""))
        val longText = "word ".repeat(300)
        val summary = shortArticleSummary(longText)
        assertTrue(summary.length <= 600)
        assertTrue(summary.endsWith("…"))
    }

    @Test fun `old cached article receives summary without changing its selection`() = runBlocking {
        val store = MemoryStore().apply { saveArticle(day, articles.first().copy(summary = "")) }
        var detailsCalls = 0
        val repo = DailyArticleRepository(store, { day }, loadDetails = {
            detailsCalls++
            it.copy(summary = "A restored abstract.")
        }, fetch = { error("Must keep old article") })
        val updated = repo.articleFor(day)
        assertEquals(articles.first().url, updated.url)
        assertEquals("A restored abstract.", updated.summary)
        assertEquals(updated, repo.articleFor(day))
        assertEquals(1, detailsCalls)
    }

    @Test fun `missing summary offline preserves existing article link`() = runBlocking {
        val stored = articles.first().copy(summary = "")
        val store = MemoryStore().apply { saveArticle(day, stored) }
        val repo = DailyArticleRepository(store, { day }, loadDetails = { throw IOException("Offline") },
            fetch = { error("Must keep old article") })
        assertEquals(stored, repo.articleFor(day))
    }

    private class MemoryStore : DailyArticleStore {
        override var feed: DailyArticleFeed? = null
        private val selections = mutableMapOf<LocalDate, DailyArticle>()
        override fun articleFor(date: LocalDate) = selections[date]
        override fun saveArticle(date: LocalDate, article: DailyArticle) { selections[date] = article }
    }
}
