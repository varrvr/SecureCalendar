package com.example.calendar.articles

import java.util.Locale

data class RussianArticleTeaser(val headline: String, val text: String)

fun russianArticleHeadline(title: String): String {
    val topic = title.lowercase(Locale.ROOT)
    return when {
        Regex("zero[ -]knowledge|\\bzk\\b|zk-snark|zk-stark").containsMatchIn(topic) -> "Доказать — и не выдать секрет. Как?"
        Regex("private set intersection|\\bpsi\\b").containsMatchIn(topic) -> "Найти общее, не раскрывая лишнего"
        Regex("password|\\bpake\\b|opaque").containsMatchIn(topic) -> "Что на самом деле защищает ваш пароль?"
        Regex("post.quantum|lattice|quantum").containsMatchIn(topic) -> "Шифры против квантового будущего"
        Regex("attack|cryptanalysis|vulnerab|kleptograph").containsMatchIn(topic) -> "Где криптография даёт слабину?"
        Regex("signature|signatures").containsMatchIn(topic) -> "Подпись, которой можно доверять?"
        Regex("multiparty|multi-party|\\bmpc\\b|secret.sharing").containsMatchIn(topic) -> "Общий расчёт. У каждого — свой секрет"
        Regex("encrypt|cipher").containsMatchIn(topic) -> "Что скрывается за стойким шифром?"
        else -> "Криптография: что нового под капотом?"
    }
}

/** One actual translated sentence, shortened at a word boundary; the hook never invents a research result. */
fun russianArticleTeaser(article: DailyArticle, translated: String): RussianArticleTeaser {
    val clean = translated.replace(Regex("\\s+"), " ").trim()
    require(clean.any { it in 'А'..'я' || it == 'ё' || it == 'Ё' }) { "Russian translation is unavailable" }
    val first = Regex("(?<=[.!?])\\s+(?=[А-ЯЁA-Z])").split(clean, limit = 2).first()
    val short = if (first.length <= 180) first else
        first.take(177).substringBeforeLast(' ', first.take(177)).trimEnd('.', ',', ';') + "…"
    return RussianArticleTeaser(russianArticleHeadline(article.title), short)
}
