package com.example

import com.example.util.LectureSummaryExtractor
import org.junit.Assert.*
import org.junit.Test

class LectureSummaryExtractorTest {

    @Test
    fun testEmptyContent() {
        val result = LectureSummaryExtractor.analyze("Лекция", "")
        assertTrue(result.keyPoints.isEmpty())
        assertTrue(result.definitions.isEmpty())
        assertEquals(0, result.wordCount)
    }

    @Test
    fun testExtractionOfDefinitionsAndActionItems() {
        val lecture = """
            Тема: Архитектура операционных систем.
            Процесс — это экземпляр выполняющейся программы с изолированным адресным пространством.
            Поток: наименьшая единица обработки, исполняемая планировщиком ОС.
            
            Главные свойства:
            - Изоляция памяти ядра от пользовательских приложений
            - Прерывания обеспечивают асинхронное взаимодействие с устройствами
            
            В итоге мы видим важность контекстного переключения.
            
            Домашнее задание: прочитать главу 4 книги Таненбаума к следующей среде.
            Сдать лабораторную работу №2 до пятницы.
            
            Какие существуют состояния жизненного цикла процесса?
        """.trimIndent()

        val result = LectureSummaryExtractor.analyze("ОС Лекция 1", lecture)

        // Definitions
        assertTrue(result.definitions.isNotEmpty())
        val processDef = result.definitions.find { it.term.contains("Процесс", ignoreCase = true) }
        assertNotNull(processDef)

        // Action items
        assertTrue(result.actionItems.isNotEmpty())
        val hw = result.actionItems.find { it.text.contains("Таненбаум", ignoreCase = true) }
        assertNotNull(hw)

        // Review questions
        assertTrue(result.reviewQuestions.isNotEmpty())
        val q = result.reviewQuestions.find { it.contains("состояния", ignoreCase = true) }
        assertNotNull(q)

        // Markdown format
        val md = LectureSummaryExtractor.formatAsMarkdownSummary(result)
        assertTrue(md.contains("Краткий конспект"))
        assertTrue(md.contains("Термины и определения"))
    }
}
