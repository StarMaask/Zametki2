package com.example

import com.example.util.FlashcardExtractor
import org.junit.Assert.*
import org.junit.Test

class FlashcardExtractorTest {

    @Test
    fun testExplicitCardsExtraction() {
        val note = """
            Конспект по сетям.
            TCP :: Протокол с установлением надежного соединения
            UDP :: Протокол передачи датаграмм без гарантии доставки
            
            Q: Что такое порт?
            A: Числовой идентификатор процесса в сетевом стеке.
        """.trimIndent()

        val cards = FlashcardExtractor.extract("Сети", note)
        assertTrue(cards.size >= 3)
        val tcpCard = cards.find { it.front == "TCP" }
        assertNotNull(tcpCard)
        assertEquals("Протокол с установлением надежного соединения", tcpCard?.back)

        val portCard = cards.find { it.front.contains("порт") }
        assertNotNull(portCard)
    }

    @Test
    fun testExtractionFromLectureDefinitions() {
        val lecture = """
            Транзакция — это последовательность операций с базой данных, выполняемых как единое целое.
            Индексация: структура данных, ускоряющая поиск строк в таблице.
        """.trimIndent()

        val cards = FlashcardExtractor.extract("БД", lecture)
        assertTrue(cards.any { it.front.contains("Транзакция", ignoreCase = true) })
        assertTrue(cards.any { it.front.contains("Индексация", ignoreCase = true) })
    }
}
