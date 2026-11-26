package com.example.calendar.articles

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class RussianArticleTranslator(context: Context) {
    private val cache = context.applicationContext.getSharedPreferences("russian_article_previews_v1", Context.MODE_PRIVATE)
    private val mutex = Mutex()

    suspend fun preview(article: DailyArticle): RussianArticleTeaser = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (article.summary.isBlank()) throw IOException("The article has no abstract")
            val cached = runCatching { JSONObject(cache.getString(article.url, null) ?: "{}") }.getOrNull()
            if (cached != null && cached.optString("original") == article.summary) {
                val translation = cached.optString("translation")
                if (translation.isNotBlank()) return@withLock russianArticleTeaser(article, translation)
            }
            val translated = if (article.summary.count { it in 'А'..'я' || it == 'ё' || it == 'Ё' } > article.summary.length / 3) {
                article.summary
            } else {
                val translator = Translation.getClient(TranslatorOptions.Builder()
                    .setSourceLanguage(TranslateLanguage.ENGLISH)
                    .setTargetLanguage(TranslateLanguage.RUSSIAN).build())
                try {
                    withTimeout(90_000) {
                        translator.downloadModelIfNeeded(DownloadConditions.Builder().requireWifi().build()).awaitResult()
                        translator.translate(article.summary).awaitResult()
                    }
                } catch (failure: TimeoutCancellationException) { throw IOException("Russian translation timed out", failure) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { throw IOException("Russian translation failed", failure) }
                finally { translator.close() }
            }
            val result = try { russianArticleTeaser(article, translated) }
            catch (failure: IllegalArgumentException) { throw IOException("Russian translation is unavailable", failure) }
            cache.edit().putString(article.url, JSONObject().put("original", article.summary)
                .put("translation", translated).toString()).apply()
            result
        }
    }
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
    addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
