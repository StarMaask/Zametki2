package com.example.util

data class TocItem(
    val title: String,
    val level: Int,
    val characterOffset: Int,
    val lineNumber: Int,
    val timestampTag: String? = null
)

object TableOfContentsExtractor {

    private val TIMESTAMP_REGEX = Regex("^\\[(\\d{1,2}:\\d{2})\\]\\s*(.*)")
    private val HEADING_REGEX = Regex("^(#{1,4})\\s+(.+)")
    private val TOPIC_MARKERS = listOf("тема:", "глава", "раздел", "лекция", "вопрос", "вывод", "д/з", "дз:", "задание:")

    /**
     * Parses note content into structured outline items for rapid navigation.
     */
    fun extract(content: String): List<TocItem> {
        if (content.isBlank()) return emptyList()

        val items = mutableListOf<TocItem>()
        val lines = content.lines()
        var currentOffset = 0

        lines.forEachIndexed { index, rawLine ->
            val trimmed = rawLine.trim()

            // 1. Markdown headings (#, ##, ###)
            val headingMatch = HEADING_REGEX.matchEntire(trimmed)
            if (headingMatch != null) {
                val hashes = headingMatch.groupValues[1]
                val title = headingMatch.groupValues[2].trim()
                items.add(
                    TocItem(
                        title = title,
                        level = hashes.length,
                        characterOffset = currentOffset,
                        lineNumber = index + 1
                    )
                )
            } else {
                // 2. Audio Timestamp markers: [01:23] Subtitle
                val tsMatch = TIMESTAMP_REGEX.matchEntire(trimmed)
                if (tsMatch != null) {
                    val ts = tsMatch.groupValues[1]
                    val sub = tsMatch.groupValues[2].trim().ifBlank { "Метка времени" }
                    items.add(
                        TocItem(
                            title = "⏱ [$ts] $sub",
                            level = 3,
                            characterOffset = currentOffset,
                            lineNumber = index + 1,
                            timestampTag = ts
                        )
                    )
                } else {
                    // 3. Keyword based structural markers
                    val lower = trimmed.lowercase()
                    if (TOPIC_MARKERS.any { lower.startsWith(it) } && trimmed.length in 5..80) {
                        items.add(
                            TocItem(
                                title = trimmed,
                                level = 2,
                                characterOffset = currentOffset,
                                lineNumber = index + 1
                            )
                        )
                    }
                }
            }

            // advance character offset: line length + newline character
            currentOffset += rawLine.length + 1
        }

        return items
    }
}
