package com.example.calendar.horoscope

import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.random.Random

/**
 * Локальный офлайн-генератор гороскопов на цепях Маркова.
 *
 * • Детерминизм: один и тот же (eventId, nonce, salt) → один и тот же текст.
 * • Офлайн: ноль сетевых вызовов.
 * • Слотовая модель: 4 независимые цепи собираются по шаблону.
 */

data class Horoscope(val text: String, val seed: Long)

enum class Slot { OPENER, SUBJECT, ACTION, CLOSER }

class MarkovChain(private val order: Int = 2) {

    private val transitions = HashMap<List<String>, MutableList<String>>()
    private val starts = ArrayList<List<String>>()

    fun train(phrase: String) {
        val cleaned = phrase.trim().trimEnd('.', ',', '!', '?', ';', ':')
        val tokens = tokenize(cleaned)
        if (tokens.isEmpty()) return

        starts.add(tokens.take(order.coerceAtMost(tokens.size)))
        if (tokens.size <= order) return

        for (n in 1..order) {
            if (tokens.size <= n) continue
            for (i in 0..tokens.size - n - 1) {
                val key = tokens.subList(i, i + n).toList()
                transitions.getOrPut(key) { ArrayList() }.add(tokens[i + n])
            }
        }
    }

    fun generate(random: Random, maxTokens: Int = 20): String {
        if (starts.isEmpty()) return ""
        val out = starts.random(random).toMutableList()
        var steps = 0
        while (steps < maxTokens) {
            val next = nextToken(out, random) ?: break
            out.add(next)
            steps++
        }
        return render(out)
    }

    private fun nextToken(out: List<String>, random: Random): String? {
        for (n in minOf(order, out.size) downTo 1) {
            val key = out.takeLast(n)
            transitions[key]?.let { return it.random(random) }
        }
        return null
    }

    private fun render(tokens: List<String>): String {
        if (tokens.isEmpty()) return ""
        val sb = StringBuilder()
        for ((i, t) in tokens.withIndex()) {
            if (t.length == 1 && t[0] in PUNCT) {
                sb.append(t)
            } else {
                if (i > 0 && sb.isNotEmpty()) sb.append(' ')
                sb.append(t)
            }
        }
        val result = sb.toString().trim()
        return if (result.isEmpty()) "" else result[0].uppercaseChar() + result.substring(1)
    }

    private fun tokenize(text: String): List<String> {
        val result = ArrayList<String>()
        val sb = StringBuilder()
        for (ch in text) {
            when {
                ch.isWhitespace() -> if (sb.isNotEmpty()) { result.add(sb.toString()); sb.clear() }
                ch in PUNCT -> {
                    if (sb.isNotEmpty()) { result.add(sb.toString()); sb.clear() }
                    result.add(ch.toString())
                }
                else -> sb.append(ch)
            }
        }
        if (sb.isNotEmpty()) result.add(sb.toString())
        return result
    }

    companion object {
        private val PUNCT = setOf('.', ',', '!', '?', ';', ':')
    }
}

class HoroscopeGenerator(
    private val corpus: Map<Slot, MarkovChain>,
    private val salt: ByteArray,
) {
    init {
        require(Slot.values().all { corpus.containsKey(it) }) {
            "Corpus must define all slots: ${Slot.values().toList()}"
        }
    }

    fun forMeeting(eventId: String, nonce: Long = 0L): Horoscope {
        val seed = seedFor(eventId, nonce, salt)
        val random = Random(seed)

        val opener  = corpus.getValue(Slot.OPENER ).generate(random).trimEnd('.')
        val subject = corpus.getValue(Slot.SUBJECT).generate(random).trimEnd('.')
        val action  = corpus.getValue(Slot.ACTION ).generate(random).trimEnd('.')
        val closer  = corpus.getValue(Slot.CLOSER ).generate(random).trimEnd('.')

        val text = buildString {
            append(opener)
            append(", ")
            append(subject.replaceFirstChar { it.lowercase() })
            append(' ')
            append(action.replaceFirstChar { it.lowercase() })
            append(". ")
            append(closer)
            append('.')
        }
        return Horoscope(text, seed)
    }

    private fun seedFor(eventId: String, nonce: Long, salt: ByteArray): Long {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(eventId.toByteArray(Charsets.UTF_8))
        md.update(ByteBuffer.allocate(8).putLong(nonce).array())
        md.update(salt)
        return ByteBuffer.wrap(md.digest(), 0, 8).long
    }
}

object SecureCalendarCorpus {

    fun build(): Map<Slot, MarkovChain> = mapOf(
        Slot.OPENER to chainOf(
            "Сегодня",
            "Как ни странно",
            "Судя по всему",
            "Скорее всего",
            "Возможно",
            "Вопреки прогнозам",
            "Не без помощи Венеры",
            "Полагаясь на интуицию",
            "Судя по расположению планет",
            "В ближайшие часы",
            "В этот день",
            "По воле случая",
            "Неожиданно для всех",
            "Вопреки здравому смыслу",
            "По звёздному календарю",
        ),
        Slot.SUBJECT to chainOf(
            "эта встреча",
            "ваш собеседник",
            "переговорный процесс",
            "обсуждение сроков",
            "повестка дня",
            "встреча в календаре",
            "следующий созвон",
            "ваш партнёр",
            "команда",
            "дедлайн",
            "общий календарь",
            "сравнение слотов",
            "криптографический протокол",
            "сеанс связи",
            "проектная встреча",
        ),
        Slot.ACTION to chainOf(
            "пройдёт гладко",
            "потребует гибкости",
            "окажется плодотворнее, чем кажется",
            "натолкнётся на неожиданное препятствие",
            "принесёт неожиданные плоды",
            "проверит ваше терпение",
            "подарит ясность",
            "затянется дольше обычного",
            "разрешится быстрее, чем вы ждёте",
            "удивит вас",
            "пойдёт не по плану",
            "сложится в вашу пользу",
            "потребует компромисса",
            "вскроет старые договорённости",
            "приведёт к неожиданному решению",
        ),
        Slot.CLOSER to chainOf(
            "Держите ключи при себе",
            "Не забывайте про fail-closed",
            "Пусть коммиты будут зелёными",
            "Доверяйте, но проверяйте",
            "Храните секреты в Keystore",
            "Доверяйте интуиции",
            "Не торопите события",
            "Слушайте собеседника внимательнее обычного",
            "Перепроверьте договорённости",
            "Сохраните резервную копию",
            "Проверьте дважды, отправьте один раз",
            "Дайте себе время на размышление",
        ),
    )

    private fun chainOf(vararg phrases: String): MarkovChain =
        MarkovChain(order = 2).apply { phrases.forEach { train(it) } }
}