package com.example.calendar.articles

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.StringReader
import java.net.URI

data class DailyArticle(val title: String, val url: String, val summary: String = "")

/** Only article landing pages on the official archive can be opened from the feed. */
internal fun eprintArticleUrl(value: String): String? = runCatching {
    val uri = URI(value.trim())
    if (uri.scheme !in setOf("http", "https") || uri.host != "eprint.iacr.org" ||
        uri.userInfo != null || uri.port != -1 || uri.query != null || uri.fragment != null ||
        !Regex("/\\d{4}/\\d+").matches(uri.path.orEmpty())) null
    else "https://eprint.iacr.org${uri.path}"
}.getOrNull()

internal fun parseEprintFeed(xml: String): List<DailyArticle> {
    val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
    parser.setInput(StringReader(xml))
    val articles = mutableListOf<DailyArticle>()
    var inItem = false
    var title = ""
    var link = ""
    var description = ""
    var field: String? = null
    val content = StringBuilder()
    while (parser.eventType != XmlPullParser.END_DOCUMENT) {
        when (parser.eventType) {
            XmlPullParser.DOCDECL -> throw IOException("Unexpected document declaration in ePrint feed")
            XmlPullParser.START_TAG -> when {
                parser.name == "item" -> { inItem = true; title = ""; link = ""; description = "" }
                inItem && parser.name in setOf("title", "link", "description") -> { field = parser.name; content.clear() }
            }
            XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.ENTITY_REF -> if (field != null) {
                content.append(parser.text ?: "")
            }
            XmlPullParser.END_TAG -> when {
                parser.name == field -> {
                    if (field == "title") title = content.toString().replace(Regex("\\s+"), " ").trim()
                    if (field == "link") link = content.toString().trim()
                    if (field == "description") description = content.toString()
                    field = null
                }
                parser.name == "item" -> {
                    val url = eprintArticleUrl(link)
                    if (title.isNotBlank() && url != null) articles += DailyArticle(title, url, shortArticleSummary(description))
                    inItem = false
                }
            }
        }
        parser.nextToken()
    }
    return articles.distinctBy { it.url }.sortedBy { it.url }
}

/** Plain text excerpt of the authors' abstract; no generated claims or automatic translation. */
internal fun shortArticleSummary(html: String): String {
    val text = html.replace(Regex("(?is)<(script|style)\\b[^>]*>.*?</\\1>"), " ")
        .replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'")
        .replace(Regex("&#(x[0-9a-fA-F]+|[0-9]+);")) { match ->
            val code = match.groupValues[1].let { if (it.startsWith("x")) it.drop(1).toIntOrNull(16) else it.toIntOrNull() }
            if (code != null && Character.isValidCodePoint(code) && code !in 0xD800..0xDFFF)
                String(Character.toChars(code)) else " "
        }
        .replace(Regex("\\s+"), " ").trim()
    val abstract = text.replace(Regex("(?is)^.*?\\bAbstract\\s*[:.]\\s*"), "").trim()
    val sentences = Regex("(?<=[.!?])\\s+(?=[A-ZА-Я])").split(abstract, limit = 3).take(2).joinToString(" ")
    if (sentences.length <= 600) return sentences
    val excerpt = sentences.take(597).substringBeforeLast(' ', sentences.take(597)).trimEnd('.', ',', ';')
    return "$excerpt…"
}

internal fun articleSummaryFromPage(html: String): String {
    val abstract = Regex("(?is)<h[1-6][^>]*>\\s*Abstract\\s*</h[1-6]>(.*?)(?=<h[1-6]\\b|$)")
        .find(html)?.groupValues?.get(1).orEmpty()
    return shortArticleSummary(abstract)
}
