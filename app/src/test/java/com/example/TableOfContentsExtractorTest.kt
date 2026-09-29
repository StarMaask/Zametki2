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

    @Test
    fun testTocPageNumberCalculationAndSync() {
        val academicText = """
            СОДЕРЖАНИЕ
            
            ВВЕДЕНИЕ ................................................................ 3
            1. ТЕОРЕТИЧЕСКАЯ ЧАСТЬ .................................................. 4
               1.1. Понятийный аппарат .............................................. 4
               1.2. Нормативная база ................................................ 22
            2. ПРАКТИЧЕСКАЯ ЧАСТЬ ................................................... 35
            ЗАКЛЮЧЕНИЕ .............................................................. 40
            СПИСОК ЛИТЕРАТУРЫ ....................................................... 45
            
            --- РАЗРЫВ СТРАНИЦЫ ---
            
            ВВЕДЕНИЕ
            Актуальность темы исследования.
            Цель и задачи работы.
            
            --- РАЗРЫВ СТРАНИЦЫ ---
            
            1. ТЕОРЕТИЧЕСКАЯ ЧАСТЬ
            1.1. Понятийный аппарат
            Краткое описание сущности понятийного аппарата.
            
            1.2. Нормативная база
            Нормативные правовые акты и стандарты в данной сфере.
            
            --- РАЗРЫВ СТРАНИЦЫ ---
            
            2. ПРАКТИЧЕСКАЯ ЧАСТЬ
            Расчеты и практические выкладки.
            
            --- РАЗРЫВ СТРАНИЦЫ ---
            
            ЗАКЛЮЧЕНИЕ
            Основные результаты и выводы исследования.
            
            --- РАЗРЫВ СТРАНИЦЫ ---
            
            СПИСОК ЛИТЕРАТУРЫ
            1. Федеральный закон.
        """.trimIndent()

        val items = TableOfContentsExtractor.extract(academicText)
        // 1.1 and 1.2 are within the same section and short text budget -> must have identical page numbers!
        val item11 = items.firstOrNull { it.title.contains("1.1") }
        val item12 = items.firstOrNull { it.title.contains("1.2") }
        assertNotNull(item11)
        assertNotNull(item12)
        assertEquals("Subsections 1.1 and 1.2 on same page must have identical page number", item11!!.pageNumber, item12!!.pageNumber)

        // Total pages must not be 45, it should be realistic (around 5-6 pages for this document)
        val totalPages = TableOfContentsExtractor.calculateTotalPages(academicText)
        org.junit.Assert.assertTrue("Total pages should be <= 8, got $totalPages", totalPages <= 8)

        // Synchronize document TOC
        val synced = TableOfContentsExtractor.synchronizeDocumentToc(academicText)
        // The bogus page '22' for 1.2 should be replaced with the real page of 1.2 (which matches 1.1, page 3)
        org.junit.Assert.assertFalse("Synced text must not contain bogus page 22", synced.contains("22"))
        org.junit.Assert.assertFalse("Synced text must not contain bogus page 35", synced.contains("35"))
        org.junit.Assert.assertFalse("Synced text must not contain bogus page 40", synced.contains("40"))
        org.junit.Assert.assertFalse("Synced text must not contain bogus page 45", synced.contains("45"))
    }

    @Test
    fun testListsAndLiteratureDoNotTriggerPageBreaks() {
        val noteContent = """
            ВВЕДЕНИЕ
            Задачи исследования:
            1. Изучить теоретические основы и понятийный аппарат.
            2. Проанализировать нормативно-правовую базу.
            3. Разработать практические рекомендации.
            
            СПИСОК ИСПОЛЬЗОВАННЫХ ИСТОЧНИКОВ
            1. Конституция Российской Федерации.
            12. Электронный ресурс: «Культурное наследие Новгородской области» [сайт]. URL: http://nasledie.nov.ru (дата обращения: 15.05.2026).
            13. Статистический ежегодник Новгородской области. — Великий Новгород: Новгородстат, 2025. — 150 с.
        """.trimIndent()

        val dummyNote = com.example.domain.model.Note(
            id = 1,
            title = "Тестовый реферат",
            content = noteContent
        )

        val structure = com.example.util.DocxGenerator.parseStructure(dummyNote)
        val paragraphs = structure.bodyElements.filterIsInstance<com.example.util.DocxGenerator.BodyElement.Paragraph>()

        // Check task list items 1, 2, 3
        val task1 = paragraphs.firstOrNull { it.text.startsWith("1. Изучить") }
        val task2 = paragraphs.firstOrNull { it.text.startsWith("2. Проанализировать") }
        val task3 = paragraphs.firstOrNull { it.text.startsWith("3. Разработать") }
        assertNotNull(task1)
        assertNotNull(task2)
        assertNotNull(task3)
        org.junit.Assert.assertFalse("Task 1 must not have page break before", task1!!.isPageBreakBefore)
        org.junit.Assert.assertFalse("Task 2 must not have page break before", task2!!.isPageBreakBefore)
        org.junit.Assert.assertFalse("Task 3 must not have page break before", task3!!.isPageBreakBefore)

        // Check literature list items 12 and 13
        val lit12 = paragraphs.firstOrNull { it.text.startsWith("12. Электронный ресурс") }
        val lit13 = paragraphs.firstOrNull { it.text.startsWith("13. Статистический ежегодник") }
        assertNotNull(lit12)
        assertNotNull(lit13)
        org.junit.Assert.assertFalse("Literature item 12 must NOT have page break before", lit12!!.isPageBreakBefore)
        org.junit.Assert.assertFalse("Literature item 13 must NOT have page break before", lit13!!.isPageBreakBefore)
        org.junit.Assert.assertFalse("Literature item 12 must NOT be centered", lit12.isCentered)
        org.junit.Assert.assertFalse("Literature item 13 must NOT be centered", lit13.isCentered)
        org.junit.Assert.assertFalse("Literature item 12 must NOT be a heading", lit12.isHeading)
        org.junit.Assert.assertFalse("Literature item 13 must NOT be a heading", lit13.isHeading)

        // Verify TableOfContentsExtractor does not extract literature entries as headings
        val tocItems = com.example.util.TableOfContentsExtractor.extract(noteContent)
        val hasLitInToc = tocItems.any { it.title.contains("Культурное наследие") || it.title.contains("Статистический ежегодник") }
        org.junit.Assert.assertFalse("Literature items must not be in Table of Contents", hasLitInToc)
    }
}
