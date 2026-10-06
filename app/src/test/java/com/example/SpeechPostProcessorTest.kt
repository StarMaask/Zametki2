package com.example

import com.example.util.SpeechPostProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechPostProcessorTest {

    @Test
    fun testCollapseSingleWordRepetitionLoopWithCommas() {
        val input = "Сегодня на лекции мы разбирали слово, слово, слово, слово, слово, слово и затем перешли к теме."
        val result = SpeechPostProcessor.collapseRepetitionLoops(input)
        assertTrue(result.contains("слово"))
        val occurrences = Regex("(?iU)\\bслово\\b").findAll(result).count()
        assertEquals(1, occurrences)
    }

    @Test
    fun testCollapseSingleWordRepetitionWithDotsAndQuestions() {
        val input = "Почему? Почему? Почему? Почему? Мы должны это знать."
        val result = SpeechPostProcessor.collapseRepetitionLoops(input)
        val occurrences = Regex("(?iU)\\bпочему\\b").findAll(result).count()
        assertEquals(1, occurrences)
    }

    @Test
    fun testCollapseMultiWordPhraseLoop() {
        val input = "В этом примере в этом примере в этом примере рассматривается интеграл."
        val result = SpeechPostProcessor.collapseRepetitionLoops(input)
        val occurrences = Regex("(?iU)в этом примере").findAll(result).count()
        assertEquals(1, occurrences)
    }

    @Test
    fun testNormalSpeechNotAltered() {
        val input = "Да, мы согласны с утверждением лектора."
        val result = SpeechPostProcessor.collapseRepetitionLoops(input)
        assertEquals(input, result)
    }

    @Test
    fun testLong55MinuteLoopCollapse() {
        // Simulate a hallucinated repeating tail of 200 words
        val base = "Теорема доказана."
        val repeatingTail = " также".repeat(200)
        val input = base + repeatingTail
        val result = SpeechPostProcessor.collapseRepetitionLoops(input)
        val occurrences = Regex("(?iU)\\bтакже\\b").findAll(result).count()
        assertEquals(1, occurrences)
    }
}
