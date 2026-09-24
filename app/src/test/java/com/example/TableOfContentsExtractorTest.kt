package com.example

import com.example.util.TableOfContentsExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TableOfContentsExtractorTest {

    @Test
    fun testExtractHeadingsAndTimestamps() {
        val content = """
            # Введение в архитектуру
            Некоторый вступительный текст.
            
            ## Основные компоненты
            Подробности компонентов.
            
            [05:20] Вопрос из аудитории
            Ответ лектора.
            
            Тема: Параллельные вычисления
            Описание.
        """.trimIndent()

        val items = TableOfContentsExtractor.extract(content)
        assertEquals(4, items.size)

        assertEquals("Введение в архитектуру", items[0].title)
        assertEquals(1, items[0].level)

        assertEquals("Основные компоненты", items[1].title)
        assertEquals(2, items[1].level)

        assertEquals("05:20", items[2].timestampTag)
        assertEquals(3, items[2].level)

        assertEquals("Тема: Параллельные вычисления", items[3].title)
    }
}
