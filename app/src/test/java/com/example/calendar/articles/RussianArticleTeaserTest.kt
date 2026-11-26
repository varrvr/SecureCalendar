package com.example.calendar.articles

import org.junit.Assert.*
import org.junit.Test

class RussianArticleTeaserTest {
    private val article = DailyArticle("OPAQUE: Password authentication", "https://eprint.iacr.org/2018/163")

    @Test fun `uses one real Russian sentence with a topic specific hook`() {
        val teaser = russianArticleTeaser(article,
            "Авторы изучают защиту паролей при взломе сервера. Предлагается новый протокол.")
        assertEquals("Что на самом деле защищает ваш пароль?", teaser.headline)
        assertEquals("Авторы изучают защиту паролей при взломе сервера.", teaser.text)
    }

    @Test fun `keeps preview short at a word boundary`() {
        val teaser = russianArticleTeaser(article, "Исследуется защита " + "паролей ".repeat(70))
        assertTrue(teaser.text.length <= 180)
        assertTrue(teaser.text.endsWith("…"))
        assertFalse(teaser.text.endsWith(" …"))
    }

    @Test(expected = IllegalArgumentException::class) fun `English is never passed off as a Russian translation`() {
        russianArticleTeaser(article, "We propose a new password protocol.")
    }

    @Test fun `hooks are Russian and do not invent successful attack claims`() {
        val titles = listOf("Zero-Knowledge Proofs", "Private Set Intersection", "Post-quantum signatures",
            "Multiparty Computation", "Cryptanalysis of ciphers", "Signature schemes", "Novel encryption", "Unknown topic")
        titles.forEach { title ->
            val hook = russianArticleHeadline(title)
            assertTrue(hook.any { it in 'А'..'я' })
            assertFalse(hook.contains("взломан", ignoreCase = true))
        }
    }
}
