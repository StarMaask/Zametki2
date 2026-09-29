package com.example.util

data class TocItem(
    val title: String,
    val level: Int,
    val characterOffset: Int,
    val lineNumber: Int,
    val pageNumber: Int = 1,
    val timestampTag: String? = null
)

object TableOfContentsExtractor {

    private val TIMESTAMP_REGEX = Regex("""^\[(\d{1,2}:\d{2})\]\s*(.*)""")
    private val HEADING_REGEX = Regex("""^(#{1,4})\s+(.+)""")
    private val SUB_NUMBERED_SECTION_REGEX = Regex("""^(\d+\.\d+(\.\d+)?)\.?\s+(.+)""")
    private val TOPIC_MARKERS = listOf("тема:", "глава", "раздел", "лекция", "вопрос", "вывод", "д/з", "дз:", "задание:")

    /**
     * Standard academic GOST page capacity (A4, 14pt Times New Roman, 1.5 line spacing).
     * ~28-30 lines of text or ~1800 characters with spaces per physical page.
     */
    const val LINES_PER_PAGE = 28
    const val CHARS_PER_PAGE = 1800

    fun isMajorNumberedChapter(trimmed: String): Boolean {
        // Matches "1. ТЕОРЕТИЧЕСКАЯ ЧАСТЬ", "2. ПРАКТИЧЕСКИЙ РАЗДЕЛ", "1. ОБЩИЕ ПОЛОЖЕНИЯ"
        val match = Regex("""^(\d{1,3})\.\s+(.+)""").matchEntire(trimmed) ?: return false
        val title = match.groupValues[2].trim()
        if (title.length !in 3..90) return false

        // Must NOT contain URLs, citation marks, pages indicator "с.", or end with a period/semicolon typical of list sentences
        if (title.contains("URL", ignoreCase = true) || title.contains("http") ||
            title.contains("[") || title.contains("]") ||
            (title.contains("—") && title.contains("с.")) ||
            title.endsWith(";") || title.contains(":")
        ) {
            return false
        }

        // Major chapters are either explicitly keywords (ГЛАВА, РАЗДЕЛ, ЧАСТЬ)
        // OR written in uppercase title style without typical sentence lowercase text
        val upperOnly = title.matches(Regex("""^[А-ЯЁA-Z\s\d—\-«»]+$"""))
        val hasChapterKeyword = title.startsWith("ГЛАВА", ignoreCase = true) ||
                title.startsWith("РАЗДЕЛ", ignoreCase = true) ||
                title.contains("ЧАСТЬ", ignoreCase = true)

        return upperOnly || hasChapterKeyword
    }

    fun isNumberedListItem(trimmed: String): Boolean {
        if (isMajorNumberedChapter(trimmed)) return false
        // Matches "1. ", "12. ", "1) ", "12) ", "а) ", "б) ", "a) ", "b) ", "[1] ", "[12] "
        return trimmed.matches(Regex("""^(\d{1,4}[\.\)]|[a-zA-Zа-яА-ЯёЁ][\.\)]|\[\d{1,4}\])\s+.*"""))
    }

    fun isBulletListItem(trimmed: String): Boolean {
        return trimmed.startsWith("- ") || trimmed.startsWith("* ") ||
                trimmed.startsWith("•\t") || trimmed.startsWith("• ") ||
                trimmed.startsWith("– ") || trimmed.startsWith("— ")
    }

    fun isMajorAcademicSection(upperOrRaw: String): Boolean {
        val trimmed = upperOrRaw.trim().removePrefix("#").trim()
        val upper = trimmed.uppercase()
        if (upper.isBlank()) return false

        // If it has dot leader or ends with page number, it's a TOC line, never a section header!
        if (upper.contains("...") || upper.contains("…") || upper.matches(Regex(""".*?[\s\.\—\-\t]+\d{1,4}$"""))) {
            return false
        }

        // List items (literature list, task items, questions, etc.) or bullets must NEVER start on a new page!
        if (isNumberedListItem(trimmed) || isBulletListItem(trimmed)) {
            return false
        }

        if (isMajorNumberedChapter(trimmed)) {
            return true
        }

        return upper == "СОДЕРЖАНИЕ" || upper == "ОГЛАВЛЕНИЕ" ||
                upper == "ПЕРЕЧЕНЬ СОКРАЩЕНИЙ И УСЛОВНЫХ ОБОЗНАЧЕНИЙ" ||
                upper == "СПИСОК СОКРАЩЕНИЙ" ||
                upper == "ВВЕДЕНИЕ" ||
                upper == "ЗАКЛЮЧЕНИЕ" ||
                upper == "СПИСОК ИСПОЛЬЗОВАННЫХ ИСТОЧНИКОВ" ||
                upper == "СПИСОК ЛИТЕРАТУРЫ" ||
                upper == "СПИСОК ИСТОЧНИКОВ" ||
                upper == "БИБЛИОГРАФИЧЕСКИЙ СПИСОК" ||
                upper == "СПИСОК ИСПОЛЬЗОВАННОЙ ЛИТЕРАТУРЫ" ||
                upper == "ПРИЛОЖЕНИЯ" ||
                upper.matches(Regex("""^(РАЗДЕЛ|ГЛАВА)\s+\d+.*"""))
    }

    fun isTocEntry(trimmed: String): Boolean {
        val clean = trimmed.removePrefix("#").trim()
        if (clean.isBlank()) return false
        val upper = clean.uppercase()

        // Explicitly NEVER a TOC entry if it's a figure, table, appendix, or inline image
        if (upper.startsWith("РИСУНОК") || upper.startsWith("ТАБЛИЦА") ||
            upper.startsWith("ПРИЛОЖЕНИЕ") || upper.startsWith("СХЕМА") ||
            upper.startsWith("ДИАГРАММА") || upper.startsWith("ИЛЛЮСТРАЦИЯ") ||
            upper.startsWith("ГРАФИК") || upper.startsWith("![") ||
            upper.startsWith("[РИСУНОК") || upper.startsWith("[ТАБЛИЦА") ||
            upper.startsWith("[ПРИЛОЖЕНИЕ") || upper.startsWith("[СХЕМА")
        ) {
            return false
        }

        // Standard dot leader pattern (e.g. "Введение ........... 3")
        if (clean.contains("...") || clean.contains("…") || clean.contains(". . .")) return true
        // Markdown anchor link pattern (e.g. "[Введение](#_toc123)")
        if (clean.contains("](#") || clean.contains("](#_")) return true
        // Section title ending with page number (e.g. "1.1. Название раздела  15")
        if (clean.matches(Regex("""^(\d+(\.\d+)*|[A-ZА-ЯЁ][\.\)]|[A-ZА-ЯЁ]\b).*?[\s\.\—\-\t]+\d{1,4}$"""))) return true
        return false
    }

    /**
     * Normalizes section title for matching between TOC entries and document headings.
     */
    fun normalizeTitle(title: String): String {
        return title.trim()
            .lowercase()
            .replace(Regex("^[#\\s\\d\\.\\-—]+"), "")
            .replace(Regex("[^a-zA-Zа-яА-ЯёЁ0-9]"), "")
    }

    /**
     * Extracts section numbering prefix like "1", "1.1", "2.1.2".
     */
    fun extractNumberPrefix(title: String): String? {
        val trimmed = title.trim().removePrefix("#").trim()
        val match = Regex("""^(\d+(\.\d+)*)""").find(trimmed)
        return match?.groupValues?.get(1)
    }

    /**
     * Parses note content into structured outline items with realistic physical page numbers.
     */
    fun extract(content: String): List<TocItem> {
        if (content.isBlank()) return emptyList()

        val items = mutableListOf<TocItem>()
        val lines = content.lines()
        var currentOffset = 0

        // 1. Check if document has Title Page
        val (titleInfo, titleEndIdx) = DocxGenerator.tryExtractTitlePage(lines)
        val hasTitlePage = titleInfo != null

        // If title page exists, it occupies page 1. TOC (if present) occupies page 2.
        var currentPage = 1
        var linesOnCurrentPage = 0
        var charsOnCurrentPage = 0
        var pageHasContent = false

        fun advanceToNewPage() {
            if (pageHasContent || currentPage == 1) {
                currentPage++
                linesOnCurrentPage = 0
                charsOnCurrentPage = 0
                pageHasContent = false
            }
        }

        var inToc = false

        for (index in lines.indices) {
            val rawLine = lines[index]
            val trimmed = rawLine.trim()
            val upper = trimmed.uppercase().removePrefix("#").trim()

            // If we just passed the title page boundary:
            if (hasTitlePage && index == titleEndIdx) {
                advanceToNewPage()
            }

            // Detect Page Breaks
            if (upper == "--- РАЗРЫВ СТРАНИЦЫ ---" || upper == "[РАЗРЫВ СТРАНИЦЫ]" ||
                upper == "[PAGE_BREAK]" || (trimmed == "---" && index > 0)) {
                inToc = false
                advanceToNewPage()
                currentOffset += rawLine.length + 1
                continue
            }

            // Detect Table of Contents block
            if (upper == "СОДЕРЖАНИЕ" || upper == "ОГЛАВЛЕНИЕ" ||
                upper.startsWith("СОДЕРЖАНИЕ ") || upper.startsWith("ОГЛАВЛЕНИЕ ")) {
                inToc = true
                if (hasTitlePage && currentPage < 2) {
                    currentPage = 2
                    linesOnCurrentPage = 0
                    charsOnCurrentPage = 0
                }
                currentOffset += rawLine.length + 1
                pageHasContent = true
                continue
            }

            // Skip lines inside TOC block so they are not treated as body headings
            if (inToc) {
                if (isTocEntry(trimmed)) {
                    currentOffset += rawLine.length + 1
                    continue
                } else if (trimmed.isBlank()) {
                    currentOffset += rawLine.length + 1
                    continue
                } else {
                    inToc = false
                    advanceToNewPage()
                }
            }

            // Skip lines belonging to the title page
            if (hasTitlePage && index < titleEndIdx) {
                currentOffset += rawLine.length + 1
                continue
            }

            var detectedItem: TocItem? = null

            if (isNumberedListItem(trimmed) || isBulletListItem(trimmed)) {
                // List items (tasks, literature references, enumerated items) are NEVER outline headings
                // and must NEVER advance to a new page individually!
            } else {
                // 1. Markdown heading (#, ##, ###)
                val headingMatch = HEADING_REGEX.matchEntire(trimmed)
                if (headingMatch != null) {
                    val hashes = headingMatch.groupValues[1]
                    val title = headingMatch.groupValues[2].trim()
                    val isMajor = hashes.length == 1 && isMajorAcademicSection(title.uppercase())
                    if (isMajor && pageHasContent) {
                        advanceToNewPage()
                    }
                    detectedItem = TocItem(
                        title = title,
                        level = hashes.length,
                        characterOffset = currentOffset,
                        lineNumber = index + 1,
                        pageNumber = currentPage
                    )
                } else if (isMajorAcademicSection(upper)) {
                    // Major academic section without markdown hash (e.g. ВВЕДЕНИЕ, ЗАКЛЮЧЕНИЕ, ГЛАВА 1)
                    if (pageHasContent) {
                        advanceToNewPage()
                    }
                    detectedItem = TocItem(
                        title = trimmed,
                        level = 1,
                        characterOffset = currentOffset,
                        lineNumber = index + 1,
                        pageNumber = currentPage
                    )
                } else {
                    // Subsection (e.g. 1.1. Наименование, 2.1.2. Анализ)
                    val subMatch = SUB_NUMBERED_SECTION_REGEX.matchEntire(trimmed)
                    if (subMatch != null) {
                        val num = subMatch.groupValues[1]
                        val dotCount = num.count { it == '.' }
                        val level = if (dotCount >= 2) 3 else 2
                        detectedItem = TocItem(
                            title = trimmed,
                            level = level,
                            characterOffset = currentOffset,
                            lineNumber = index + 1,
                            pageNumber = currentPage
                        )
                    } else {
                        // Audio timestamp marker
                        val tsMatch = TIMESTAMP_REGEX.matchEntire(trimmed)
                        if (tsMatch != null) {
                            val ts = tsMatch.groupValues[1]
                            val sub = tsMatch.groupValues[2].trim().ifBlank { "Метка времени" }
                            detectedItem = TocItem(
                                title = "⏱ [$ts] $sub",
                                level = 3,
                                characterOffset = currentOffset,
                                lineNumber = index + 1,
                                pageNumber = currentPage,
                                timestampTag = ts
                            )
                        } else {
                            // Keyword based structural markers
                            val lower = trimmed.lowercase()
                            if (TOPIC_MARKERS.any { lower.startsWith(it) } && trimmed.length in 5..80) {
                                detectedItem = TocItem(
                                    title = trimmed,
                                    level = 2,
                                    characterOffset = currentOffset,
                                    lineNumber = index + 1,
                                    pageNumber = currentPage
                                )
                            }
                        }
                    }
                }
            }

            if (detectedItem != null) {
                items.add(detectedItem)
                pageHasContent = true
            } else if (trimmed.isNotBlank()) {
                pageHasContent = true
                linesOnCurrentPage++
                charsOnCurrentPage += trimmed.length

                // Advance page if physical page capacity reached
                if (linesOnCurrentPage >= LINES_PER_PAGE || charsOnCurrentPage >= CHARS_PER_PAGE) {
                    advanceToNewPage()
                }
            }

            currentOffset += rawLine.length + 1
        }

        return items
    }

    /**
     * Calculates total pages of the document based on academic layout.
     */
    fun calculateTotalPages(content: String): Int {
        val items = extract(content)
        val maxFromItems = items.maxOfOrNull { it.pageNumber } ?: 1
        // Also estimate based on total lines/characters
        val lines = content.lines().filter { it.isNotBlank() }
        val estimatedFromLines = (lines.size / LINES_PER_PAGE) + 1
        return maxOf(maxFromItems, estimatedFromLines).coerceAtLeast(1)
    }

    /**
     * Synchronizes all page numbers inside the document's СОДЕРЖАНИЕ / ОГЛАВЛЕНИЕ block
     * with the actual physical page numbers of each section.
     */
    fun synchronizeDocumentToc(content: String): String {
        if (content.isBlank()) return content

        val items = extract(content)
        if (items.isEmpty()) return content

        val lines = content.lines().toMutableList()
        val tocStartIdx = lines.indexOfFirst {
            val u = it.trim().uppercase().removePrefix("#").trim()
            u == "СОДЕРЖАНИЕ" || u == "ОГЛАВЛЕНИЕ" || u.startsWith("СОДЕРЖАНИЕ ") || u.startsWith("ОГЛАВЛЕНИЕ ")
        }
        if (tocStartIdx == -1) return content

        // Find end of TOC
        var tocEndIdx = lines.size
        for (i in (tocStartIdx + 1) until lines.size) {
            val trimmed = lines[i].trim()
            val upper = trimmed.uppercase().removePrefix("#").trim()
            if (upper.contains("РАЗРЫВ СТРАНИЦЫ") || (trimmed == "---" && i > tocStartIdx + 1)) {
                tocEndIdx = i
                break
            }
            if (trimmed.isNotBlank() && !isTocEntry(trimmed)) {
                tocEndIdx = i
                break
            }
        }

        var modified = false
        for (i in (tocStartIdx + 1) until tocEndIdx) {
            val line = lines[i]
            val trimmed = line.trim()
            if (trimmed.isBlank() || !isTocEntry(trimmed)) continue

            // Extract entry title (strip trailing dots, dashes, and old page numbers)
            val titlePart = trimmed
                .replace(Regex("""[\.\s\—\-\t…]+$"""), "")
                .replace(Regex("""[\.\s\—\-\t…]+\d{1,4}$"""), "")
                .trim()

            val normTitle = normalizeTitle(titlePart)
            val numPrefix = extractNumberPrefix(titlePart)

            // Find matching TocItem
            val matchedItem = items.firstOrNull { item ->
                val itemNorm = normalizeTitle(item.title)
                val itemNum = extractNumberPrefix(item.title)
                (numPrefix != null && itemNum != null && numPrefix == itemNum) ||
                (normTitle.isNotBlank() && itemNorm.isNotBlank() && (normTitle == itemNorm || normTitle.contains(itemNorm) || itemNorm.contains(normTitle)))
            }

            if (matchedItem != null) {
                val realPage = matchedItem.pageNumber
                val indent = line.takeWhile { it == ' ' || it == '\t' }
                val cleanTitle = titlePart.removePrefix("#").trim()

                // Generate clean, uniform dot leaders according to GOST standard
                val targetLineWidth = 72
                val baseLen = indent.length + cleanTitle.length + 1 + realPage.toString().length
                val dotsCount = (targetLineWidth - baseLen).coerceIn(3, 45)
                val dots = " " + ".".repeat(dotsCount) + " "

                lines[i] = "$indent$cleanTitle$dots$realPage"
                modified = true
            }
        }

        return if (modified) lines.joinToString("\n") else content
    }

    /**
     * Generates a complete, GOST-formatted Table of Contents (СОДЕРЖАНИЕ)
     * with exact page numbers.
     */
    fun generateAcademicToc(content: String): String {
        val items = extract(content)
        if (items.isEmpty()) return ""

        val sb = StringBuilder()
        sb.append("СОДЕРЖАНИЕ\n\n")

        for (item in items) {
            val indent = when (item.level) {
                1 -> ""
                2 -> "   "
                else -> "      "
            }
            val cleanTitle = item.title.removePrefix("#").trim()
            val targetLineWidth = 72
            val baseLen = indent.length + cleanTitle.length + 1 + item.pageNumber.toString().length
            val dotsCount = (targetLineWidth - baseLen).coerceIn(3, 45)
            val dots = " " + ".".repeat(dotsCount) + " "

            sb.append(indent).append(cleanTitle).append(dots).append(item.pageNumber).append("\n")
        }

        sb.append("\n--- РАЗРЫВ СТРАНИЦЫ ---\n")
        return sb.toString()
    }

    /**
     * Inserts or synchronizes the Table of Contents in the document.
     */
    fun insertOrUpdateAcademicToc(content: String): String {
        val lines = content.lines()
        val hasToc = lines.any {
            val u = it.trim().uppercase().removePrefix("#").trim()
            u == "СОДЕРЖАНИЕ" || u == "ОГЛАВЛЕНИЕ" || u.startsWith("СОДЕРЖАНИЕ ") || u.startsWith("ОГЛАВЛЕНИЕ ")
        }

        if (hasToc) {
            return synchronizeDocumentToc(content)
        }

        val tocBlock = generateAcademicToc(content)
        if (tocBlock.isBlank()) return content

        val (titleInfo, titleEndIdx) = DocxGenerator.tryExtractTitlePage(lines)
        return if (titleInfo != null && titleEndIdx > 0 && titleEndIdx < lines.size) {
            val titlePart = lines.subList(0, titleEndIdx).joinToString("\n").trim()
            val bodyPart = lines.subList(titleEndIdx, lines.size).joinToString("\n").trim()
            titlePart + "\n\n--- РАЗРЫВ СТРАНИЦЫ ---\n\n" + tocBlock + "\n" + bodyPart
        } else {
            tocBlock + "\n" + content
        }
    }
}
