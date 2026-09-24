package com.example.util

data class FlashcardItem(
    val id: String,
    val front: String,
    val back: String,
    val category: String = "Термин",
    var isMastered: Boolean = false
)

object FlashcardExtractor {

    /**
     * Extracts a list of flashcards from note title and content using NLP heuristics
     * and explicit card syntax.
     */
    fun extract(title: String, content: String): List<FlashcardItem> {
        val cleanContent = content.trim()
        if (cleanContent.isBlank()) return emptyList()

        val cards = mutableListOf<FlashcardItem>()
        val seenFronts = mutableSetOf<String>()

        // 1. Explicit flashcards: "Front :: Back"
        cleanContent.lines().forEachIndexed { index, line ->
            val trimmed = line.trim()
            if (trimmed.contains("::")) {
                val parts = trimmed.split("::", limit = 2)
                val front = parts[0].trim().removePrefix("- ").removePrefix("* ")
                val back = parts[1].trim()
                if (front.length in 2..80 && back.isNotBlank() && seenFronts.add(front.lowercase())) {
                    cards.add(
                        FlashcardItem(
                            id = "explicit_$index",
                            front = front,
                            back = back,
                            category = "Карточка"
                        )
                    )
                }
            }
        }

        // 2. Q&A format: "Q: ... \n A: ..." or "В: ... \n О: ..."
        val lines = cleanContent.lines()
        for (i in 0 until lines.size - 1) {
            val l1 = lines[i].trim()
            val l2 = lines[i + 1].trim()
            val isQ = l1.startsWith("Q:", ignoreCase = true) || l1.startsWith("В:", ignoreCase = true) || l1.startsWith("Вопрос:", ignoreCase = true)
            val isA = l2.startsWith("A:", ignoreCase = true) || l2.startsWith("О:", ignoreCase = true) || l2.startsWith("Ответ:", ignoreCase = true)
            if (isQ && isA) {
                val front = l1.substringAfter(":").trim()
                val back = l2.substringAfter(":").trim()
                if (front.isNotBlank() && back.isNotBlank() && seenFronts.add(front.lowercase())) {
                    cards.add(
                        FlashcardItem(
                            id = "qa_$i",
                            front = front,
                            back = back,
                            category = "Вопрос-Ответ"
                        )
                    )
                }
            }
        }

        // 3. Definitions from LectureSummaryExtractor
        val analysis = LectureSummaryExtractor.analyze(title, content)
        analysis.definitions.forEachIndexed { index, def ->
            if (seenFronts.add(def.term.lowercase())) {
                cards.add(
                    FlashcardItem(
                        id = "def_$index",
                        front = def.term,
                        back = def.definition,
                        category = "📖 Определение"
                    )
                )
            }
        }

        // 4. Questions generated or found in text
        analysis.reviewQuestions.forEachIndexed { index, q ->
            if (seenFronts.add(q.lowercase())) {
                // Find matching context or answer
                val matchingDef = analysis.definitions.find { q.contains(it.term, ignoreCase = true) }
                val answer = matchingDef?.let { "${it.term}: ${it.definition}" }
                    ?: analysis.summary
                cards.add(
                    FlashcardItem(
                        id = "q_$index",
                        front = q,
                        back = answer,
                        category = "❓ Самопроверка"
                    )
                )
            }
        }

        // 5. If still few cards, extract key takeaways as "Тезис"
        if (cards.size < 3 && analysis.keyPoints.isNotEmpty()) {
            analysis.keyPoints.forEachIndexed { index, pt ->
                val parts = pt.split("—", "-", limit = 2)
                if (parts.size == 2) {
                    val front = parts[0].trim()
                    val back = parts[1].trim()
                    if (front.length in 3..60 && back.length > 5 && seenFronts.add(front.lowercase())) {
                        cards.add(
                            FlashcardItem(
                                id = "point_$index",
                                front = front,
                                back = back,
                                category = "💡 Ключевой факт"
                            )
                        )
                    }
                }
            }
        }

        return cards
    }
}
