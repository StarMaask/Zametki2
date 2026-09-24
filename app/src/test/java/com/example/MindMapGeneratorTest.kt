package com.example

import com.example.util.MindMapGenerator
import com.example.util.MindMapNodeType
import org.junit.Assert.*
import org.junit.Test

class MindMapGeneratorTest {

    @Test
    fun testMindMapGenerationWithHeadingsAndDefinitions() {
        val note = """
            # Введение в архитектуру ОС
            Операционная система — это комплекс программ, управляющих ресурсами компьютера.
            
            ## Процессы и потоки
            Поток — наименьшая единица обработки в планировщике ОС.
            
            - Сдать лабораторную работу до пятницы
            
            Что такое планировщик задач?
        """.trimIndent()

        val graph = MindMapGenerator.generate("Лекция 1: ОС", note)
        assertNotNull(graph.root)
        assertEquals("Лекция 1: ОС", graph.root.text)
        assertTrue(graph.allNodes.size >= 4)

        val hasDefinition = graph.allNodes.any { it.type == MindMapNodeType.DEFINITION }
        val hasSection = graph.allNodes.any { it.type == MindMapNodeType.SECTION }
        assertTrue("Must contain definition", hasDefinition)
        assertTrue("Must contain section", hasSection)
        assertTrue("Must have connections between nodes", graph.connections.isNotEmpty())
    }
}
