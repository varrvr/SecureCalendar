package com.example.calendar.articles

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

class AndroidDailyArticleStore(context: Context) : DailyArticleStore {
    private val preferences = context.applicationContext.getSharedPreferences("daily_eprint_articles", Context.MODE_PRIVATE)

    override var feed: DailyArticleFeed?
        get() = runCatching {
            val json = JSONObject(preferences.getString("feed", null) ?: return null)
            val items = json.getJSONArray("articles")
            DailyArticleFeed(LocalDate.parse(json.getString("date")),
                (0 until items.length()).mapNotNull { decode(items.getJSONObject(it)) })
        }.getOrNull()
        set(value) {
            if (value == null) preferences.edit().remove("feed").apply()
            else preferences.edit().putString("feed", JSONObject().apply {
                put("date", value.fetchedOn.toString())
                put("articles", JSONArray().apply { value.articles.forEach { put(encode(it)) } })
            }.toString()).apply()
        }

    override fun articleFor(date: LocalDate): DailyArticle? = runCatching {
        decode(JSONObject(preferences.getString("day:$date", null) ?: return null))
    }.getOrNull()

    override fun saveArticle(date: LocalDate, article: DailyArticle) {
        preferences.edit().putString("day:$date", encode(article).toString()).apply()
    }

    private fun encode(article: DailyArticle) = JSONObject().put("title", article.title).put("url", article.url).put("summary", article.summary)
    private fun decode(json: JSONObject): DailyArticle? {
        val url = eprintArticleUrl(json.getString("url")) ?: return null
        return json.getString("title").takeIf { it.isNotBlank() }?.let { DailyArticle(it, url, json.optString("summary", "")) }
    }
}
