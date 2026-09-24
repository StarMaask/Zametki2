package com.example.util

data class LectureDefinition(
    val term: String,
    val definition: String
)

data class LectureActionItem(
    val text: String,
    val isUrgent: Boolean = false
)

data class LectureAnalysisResult(
    val titleSuggestion: String?,
    val summary: String,
    val keyPoints: List<String>,
    val definitions: List<LectureDefinition>,
    val actionItems: List<LectureActionItem>,
    val reviewQuestions: List<String>,
    val readingTimeMinutes: Int,
    val wordCount: Int
)

object LectureSummaryExtractor {

    private val ACTION_KEYWORDS = listOf(
        "дз", "д/з", "домашнее", "задание", "к следующ", "сдать", "прочитать",
        "выучить", "подготовить", "решить", "повторить", "написать", "запомнить",
        "обратите внимание", "на экзамен", "в зачет", "лабораторн", "практическ",
        "дедлайн", "deadline", "homework", "task", "assignment", "todo"
    )

    private val SUMMARY_LEAD_MARKERS = listOf(
        "итак", "таким образом", "в итоге", "главный вывод", "в заключение",
        "следовательно", "основная мысль", "подводя итог", "in summary", "to conclude"
    )

    /**
     * Performs instant on-device extraction of structure, key takeaways, definitions,
     * action items (homework/tasks), and self-test review questions from lecture text.
     */
    fun analyze(title: String, content: String): LectureAnalysisResult {
        val cleanContent = content.trim()
        if (cleanContent.isBlank()) {
            return LectureAnalysisResult(
                titleSuggestion = title.ifBlank { "Конспект лекции" },
                summary = "Текст заметки пуст. Надиктуйте речь или напишите текст для создания умного резюме.",
                keyPoints = emptyList(),
                definitions = emptyList(),
                actionItems = emptyList(),
                reviewQuestions = emptyList(),
                readingTimeMinutes = 0,
                wordCount = 0
            )
        }

        val rawWords = cleanContent.split(Regex("\\s+")).filter { it.isNotBlank() }
        val wordCount = rawWords.size
        val readingTime = (wordCount / 160).coerceAtLeast(1)

        val rawLines = cleanContent.lines().map { it.trim() }.filter { it.isNotBlank() }
        val sentences = cleanContent
            .replace(Regex("\\[\\d{1,2}:\\d{2}\\]"), "") // strip timestamps like [01:45]
            .split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.length > 10 }

        // 1. Action Items / Tasks / Homework
        val actionItems = mutableListOf<LectureActionItem>()
        rawLines.forEach { line ->
            val lower = line.lowercase()
            if (ACTION_KEYWORDS.any { lower.contains(it) }) {
                val cleanLine = line.removePrefix("- ").removePrefix("* ").removePrefix("• ").trim()
                val isUrgent = lower.contains("срочно") || lower.contains("экзамен") || lower.contains("дедлайн")
                if (cleanLine.length > 5 && actionItems.none { it.text.equals(cleanLine, ignoreCase = true) }) {
                    actionItems.add(LectureActionItem(cleanLine, isUrgent))
                }
            }
        }

        // 2. Definitions and Terminology
        val definitions = mutableListOf<LectureDefinition>()
        // Pattern 1: Term — [это] Definition
        val dashPattern = Regex("^([A-ZА-ЯЁ][^—–\\-:\n]{2,35})\\s*[—–\\-]\\s*(?:это\\s+)?(.+)", RegexOption.IGNORE_CASE)
        // Pattern 2: Term: Definition
        val colonPattern = Regex("^([A-ZА-ЯЁ][^:\n]{2,30}):\\s+(.+)", RegexOption.IGNORE_CASE)
        // Pattern 3: X называется Y / под X понимается Y
        val namedPattern = Regex("(?:под\\s+)?([А-ЯA-Zа-яa-z0-9\\s]{3,35})\\s+(?:называется|понимается|представляет собой)\\s+(.+)", RegexOption.IGNORE_CASE)

        rawLines.forEach { line ->
            val cleanLine = line.removePrefix("- ").removePrefix("* ").removePrefix("• ").trim()
            val dashMatch = dashPattern.matchEntire(cleanLine)
            if (dashMatch != null) {
                val term = dashMatch.groupValues[1].trim().trimEnd('.', ',')
                val def = dashMatch.groupValues[2].trim()
                if (term.split(" ").size <= 5 && def.length > 8 && definitions.none { it.term.equals(term, ignoreCase = true) }) {
                    definitions.add(LectureDefinition(term, def))
                }
            } else {
                val colonMatch = colonPattern.matchEntire(cleanLine)
                if (colonMatch != null) {
                    val term = colonMatch.groupValues[1].trim()
                    val def = colonMatch.groupValues[2].trim()
                    if (term.split(" ").size <= 4 && def.length > 12 && definitions.none { it.term.equals(term, ignoreCase = true) }) {
                        definitions.add(LectureDefinition(term, def))
                    }
                }
            }
        }

        // 3. Key Points / Takeaways
        val keyPoints = mutableListOf<String>()
        // Look for list items
        rawLines.forEach { line ->
            if (line.startsWith("- ") || line.startsWith("* ") || line.startsWith("• ") || line.matches(Regex("^\\d+\\.\\s+.+"))) {
                val point = line.replace(Regex("^[\\-*•\\d.]+\\s+"), "").trim()
                if (point.length in 12..250 && !actionItems.any { it.text.contains(point) }) {
                    keyPoints.add(point)
                }
            }
        }

        // If list items are scarce, pick informative sentences
        if (keyPoints.size < 4) {
            sentences.forEach { s ->
                val lower = s.lowercase()
                if (SUMMARY_LEAD_MARKERS.any { lower.contains(it) } || lower.contains("важно") || lower.contains("следует отметить")) {
                    if (s.length in 15..250 && keyPoints.none { it.equals(s, ignoreCase = true) }) {
                        keyPoints.add(s)
                    }
                }
            }
        }

        // If still few, take the first 3-4 distinct meaningful sentences
        if (keyPoints.isEmpty() && sentences.isNotEmpty()) {
            keyPoints.addAll(sentences.take(4))
        }

        // 4. Executive Summary
        val summarySentence = sentences.firstOrNull { s ->
            val l = s.lowercase()
            SUMMARY_LEAD_MARKERS.any { l.contains(it) }
        } ?: sentences.firstOrNull { it.length > 25 } ?: cleanContent.take(150)

        val summary = if (sentences.size > 1 && summarySentence != sentences.first()) {
            "${sentences.first()} $summarySentence"
        } else {
            summarySentence
        }

        // 5. Review Questions for self-testing / exam prep
        val reviewQuestions = mutableListOf<String>()
        // Questions explicitly present in the text
        sentences.filter { it.endsWith("?") }.forEach { q ->
            if (q.length in 10..180 && !reviewQuestions.contains(q)) {
                reviewQuestions.add(q)
            }
        }
        // Synthesize questions from definitions
        definitions.take(5).forEach { d ->
            val genQ = "Что такое «${d.term}» и каково его определение?"
            if (!reviewQuestions.contains(genQ)) {
                reviewQuestions.add(genQ)
            }
        }
        if (reviewQuestions.isEmpty() && keyPoints.isNotEmpty()) {
            reviewQuestions.add("Каковы ключевые тезисы и основные выводы данной темы?")
        }

        val titleSuggestion = when {
            title.isNotBlank() -> title
            definitions.isNotEmpty() -> "Конспект: ${definitions.first().term}"
            rawLines.isNotEmpty() -> rawLines.first().take(35)
            else -> "Конспект лекции"
        }

        return LectureAnalysisResult(
            titleSuggestion = titleSuggestion,
            summary = summary,
            keyPoints = keyPoints.take(8),
            definitions = definitions.take(10),
            actionItems = actionItems.take(8),
            reviewQuestions = reviewQuestions.take(8),
            readingTimeMinutes = readingTime,
            wordCount = wordCount
        )
    }

    /**
     * Formats the generated analysis into clean, readable Markdown that can be appended directly
     * to the user's note.
     */
    fun formatAsMarkdownSummary(result: LectureAnalysisResult): String {
        return buildString {
            append("\n\n---\n")
            append("## 🧠 Краткий конспект и тезисы лекции\n\n")

            append("**📌 Главная мысль:**\n")
            append("> ${result.summary}\n\n")

            if (result.keyPoints.isNotEmpty()) {
                append("**💡 Ключевые тезисы:**\n")
                result.keyPoints.forEach { pt ->
                    append("- $pt\n")
                }
                append("\n")
            }

            if (result.definitions.isNotEmpty()) {
                append("**📖 Термины и определения:**\n")
                result.definitions.forEach { d ->
                    append("- **${d.term}:** ${d.definition}\n")
                }
                append("\n")
            }

            if (result.actionItems.isNotEmpty()) {
                append("**🎯 Задания и действия:**\n")
                result.actionItems.forEach { act ->
                    val badge = if (act.isUrgent) "⚡ " else "☐ "
                    append("- $badge${act.text}\n")
                }
                append("\n")
            }

            if (result.reviewQuestions.isNotEmpty()) {
                append("**❓ Вопросы для самопроверки:**\n")
                result.reviewQuestions.forEachIndexed { i, q ->
                    append("${i + 1}. $q\n")
                }
                append("\n")
            }

            append("⏱ *Время чтения: ~${result.readingTimeMinutes} мин. | Слов: ${result.wordCount}*\n")
        }
    }
}
