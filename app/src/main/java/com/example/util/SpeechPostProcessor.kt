package com.example.util

import java.util.Locale

/**
 * Processor for transcribed speech text.
 * - Replaces misheard/misrecognized words according to user-defined dictionary rules and academic glossary.
 * - Converts spoken punctuation commands into punctuation marks.
 * - Filters verbal stutters and filler sounds ("эээ", "ну", repeated words).
 * - Structures raw continuous lecture transcripts into organized notes with headings and bullet points.
 */
object SpeechPostProcessor {

    /**
     * Default common speech recognition corrections for Russian, academic lectures, and common loanwords.
     */
    val DEFAULT_CORRECTIONS = mapOf(
        "итд" to "и т. д.",
        "итп" to "и т. п.",
        "тд" to "т. д.",
        "тп" to "т. п.",
        "тчк" to ".",
        "зпт" to ",",
        "ватсап" to "WhatsApp",
        "вацап" to "WhatsApp",
        "телеграм" to "Telegram",
        "телеграмм" to "Telegram",
        "ютуб" to "YouTube",
        "вайфай" to "Wi-Fi",
        "онлайн" to "онлайн",
        "офлайн" to "офлайн",
        "ии" to "ИИ",
        "джпт" to "GPT",
        // Academic lecture terms and connectors
        "во первых" to "во-первых,",
        "во вторых" to "во-вторых,",
        "в третьих" to "в-третьих,",
        "в четвертых" to "в-четвертых,",
        "в пятых" to "в-пятых,",
        "таким образом" to "таким образом,",
        "следовательно" to "следовательно,",
        "то есть" to "то есть",
        "так как" to "так как",
        "например" to "например,",
        "к примеру" to "к примеру,",
        "обратите внимание" to "Обратите внимание:",
        "подведем итог" to "Подведем итог:",
        "в итоге" to "в итоге,",
        "по определению" to "по определению",
        "согласно теореме" to "согласно теореме",
        "формула" to "формула",
        "коэффициент" to "коэффициент",
        "дифференциал" to "дифференциал",
        "интеграл" to "интеграл",
        "производная" to "производная",
        "алгоритм" to "алгоритм",
        "гипотеза" to "гипотеза",
        "вероятность" to "вероятность"
    )

    /**
     * Eliminates degenerate repetition loops (e.g. "вот, вот, вот, вот...", "слово. слово. слово...",
     * or repeating multi-word phrases) frequently caused by autoregressive decoding during pauses or silence.
     */
    fun collapseRepetitionLoops(text: String): String {
        if (text.isBlank()) return text
        var res = text

        // 1. Token-level N-gram repetition loop collapse (handles 1-word up to 12-word repeating phrases)
        // This is O(N) and immune to catastrophic regex backtracking or punctuation discrepancies.
        res = collapseTokenRepetitionLoops(res)

        // 2. Single word repetition loops with any punctuation (commas, dots, spaces, dashes, newlines)
        res = res.replace(Regex("(?iU)\\b([\\p{L}\\p{Nd}]+)(?:[\\s,;:.!?—\\-\n\r]+(?i:\\1)){2,}\\b")) { matchResult ->
            matchResult.groupValues[1]
        }

        // 3. Multi-word phrase loops (2-5 words) repeated 2+ additional times
        res = res.replace(Regex("(?iU)\\b([\\p{L}\\p{Nd}]+(?:\\s+[\\p{L}\\p{Nd}]+){1,4})(?:[\\s,;:.!?—\\-\n\r]+(?i:\\1)){2,}\\b")) { matchResult ->
            matchResult.groupValues[1]
        }

        // 4. Clean leftover consecutive duplicate punctuation or spaces
        res = res.replace(Regex("([,;])\\s*[,;]+"), "$1")
        res = res.replace(Regex("([.!?])\\s*\\1+"), "$1")
        res = res.replace(Regex("[ \\t]{2,}"), " ")
        return res.trim()
    }

    /**
     * Scans tokens to detect and collapse repetitive sequences of length 1 to 12 words
     * that repeat 3 or more times consecutively.
     */
    private fun collapseTokenRepetitionLoops(input: String): String {
        val lines = input.split("\n")
        val processedLines = lines.map { line ->
            if (line.isBlank()) return@map line
            val tokens = line.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            if (tokens.size < 3) return@map line

            // Normalized token for comparison (lowercase, trimmed punctuation)
            val normalized = tokens.map { it.lowercase().replace(Regex("[^\\p{L}\\p{Nd}]"), "") }

            val resultTokens = mutableListOf<String>()
            var i = 0
            val n = tokens.size

            while (i < n) {
                var foundLoop = false
                // Check pattern lengths from 1 to 10 tokens
                val maxPatternLen = minOf(10, (n - i) / 3)
                for (pLen in 1..maxPatternLen) {
                    val pattern = normalized.subList(i, i + pLen)
                    // Ensure the pattern is non-empty
                    if (pattern.all { it.isEmpty() }) continue

                    var matchCount = 1
                    var cursor = i + pLen
                    while (cursor + pLen <= n) {
                        val candidate = normalized.subList(cursor, cursor + pLen)
                        if (candidate == pattern) {
                            matchCount++
                            cursor += pLen
                        } else {
                            break
                        }
                    }

                    // If repeated 3 or more times consecutively, keep only 1 occurrence!
                    if (matchCount >= 3) {
                        for (k in 0 until pLen) {
                            resultTokens.add(tokens[i + k])
                        }
                        i = cursor
                        foundLoop = true
                        break
                    }
                }

                if (!foundLoop) {
                    resultTokens.add(tokens[i])
                    i++
                }
            }

            resultTokens.joinToString(" ")
        }
        return processedLines.joinToString("\n")
    }

    /**
     * Filters verbal hesitations and stutters common in live lectures.
     */
    fun cleanVerbalFillers(text: String): String {
        var res = text
        // Remove isolated hesitation sounds like "эээ", "ммм", "ааа", "ээ", "мм"
        res = res.replace(Regex("(?iu)\\b(э{2,}|м{2,}|а{2,}|гм|хм|хмм)\\b[,.]?"), "")
        // Clean double repeated words like "в в", "на на", "что что", "мы мы"
        res = res.replace(Regex("(?iu)\\b([а-яa-z]{1,4})\\s+\\1\\b"), "$1")
        // Collapse repetition loops (e.g. "вот, вот, вот...", "да, да, да...")
        res = collapseRepetitionLoops(res)
        // Clean consecutive spaces
        res = res.replace(Regex("[ \\t]{2,}"), " ")
        return res.trim()
    }

    fun process(
        text: String,
        enableSmartPunctuation: Boolean = true,
        replacements: Map<String, String> = emptyMap()
    ): String {
        if (text.isBlank()) return text

        var result = cleanVerbalFillers(text)

        // 1. Merge default corrections and user-defined dictionary rules (user overrides take precedence)
        val allRules = LinkedHashMap<String, String>()
        allRules.putAll(DEFAULT_CORRECTIONS)
        allRules.putAll(replacements)

        for ((wrongWord, correctWord) in allRules) {
            if (wrongWord.isBlank() || correctWord.isBlank()) continue
            try {
                // Word boundary replacement with case matching
                val pattern = "\\b${Regex.escape(wrongWord.trim())}\\b"
                val regex = Regex(pattern, RegexOption.IGNORE_CASE)
                result = result.replace(regex) { matchResult ->
                    val matchedText = matchResult.value
                    if (matchedText.isNotEmpty() && matchedText[0].isUpperCase()) {
                        correctWord.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                    } else {
                        correctWord
                    }
                }
            } catch (_: Exception) {}
        }

        // 2. Smart Punctuation (voice commands -> punctuation marks)
        if (enableSmartPunctuation) {
            result = applySmartPunctuation(result)
        }

        // 3. Final anti-loop pass to clean any punctuation-generated repetition artifacts
        result = collapseRepetitionLoops(result)

        return result
    }

    private fun applySmartPunctuation(text: String): String {
        var res = text

        // New lines and paragraphs
        res = res.replace(Regex("(?i)\\b(с новой строки|новая строка|новый абзац|абзац)\\b"), "\n")

        // Spoken punctuation commands
        res = res.replace(Regex("(?i)\\s*\\bточка с запятой\\b\\s*"), "; ")
        res = res.replace(Regex("(?i)\\s*\\bвосклицательный знак\\b\\s*"), "! ")
        res = res.replace(Regex("(?i)\\s*\\bвопросительный знак\\b\\s*"), "? ")
        res = res.replace(Regex("(?i)\\s*\\bмноготочие\\b\\s*"), "... ")
        res = res.replace(Regex("(?i)\\s*\\bдвоеточие\\b\\s*"), ": ")
        res = res.replace(Regex("(?i)\\s*\\bтире\\b\\s*"), " — ")
        res = res.replace(Regex("(?i)\\s*\\bдефис\\b\\s*"), "-")
        res = res.replace(Regex("(?i)\\s*\\bточка\\b\\s*"), ". ")
        res = res.replace(Regex("(?i)\\s*\\bзапятая\\b\\s*"), ", ")

        // Quotations: "в кавычках <текст> закрыть кавычки"
        res = res.replace(Regex("(?i)\\bоткрыть кавычки\\b\\s*"), " «")
        res = res.replace(Regex("(?i)\\s*\\bзакрыть кавычки\\b"), "» ")

        // Fix duplicate spaces and spaces right before punctuation
        res = res.replace(Regex("[ \\t]+([.,!?:;»])"), "$1")
        res = res.replace(Regex("([«])\\s+"), "$1")
        res = res.replace(Regex("([.,!?:;])([А-Яа-яA-Za-z0-9])"), "$1 $2")

        // Capitalize sentences after '.', '!', '?', or newlines
        val capitalized = StringBuilder()
        var capitalizeNext = true
        for (i in res.indices) {
            val c = res[i]
            if (capitalizeNext && c.isLetter()) {
                capitalized.append(c.uppercaseChar())
                capitalizeNext = false
            } else {
                capitalized.append(c)
                if (c == '.' || c == '!' || c == '?' || c == '\n') {
                    capitalizeNext = true
                }
            }
        }

        return capitalized.toString().trim()
    }

    /**
     * Intelligently structures raw continuous lecture speech into clean, formatted notes:
     * - Breaks long monologue into logical paragraphs
     * - Converts enumerations ("во-первых...", "пункт 1...") into markdown bullet lists
     * - Highlights academic definitions, theorems, and conclusions
     */
    fun structureLectureTranscript(rawText: String): String {
        if (rawText.isBlank()) return rawText

        var cleaned = cleanVerbalFillers(rawText)
        cleaned = process(cleaned, enableSmartPunctuation = true)

        // Split sentences by sentence-ending punctuation
        val sentences = cleaned.split(Regex("(?<=[.!?])\\s+")).map { it.trim() }.filter { it.isNotBlank() }
        if (sentences.isEmpty()) return cleaned

        val output = StringBuilder()
        var inBulletList = false

        for (sentence in sentences) {
            val lower = sentence.lowercase(Locale.getDefault())

            // 1. Topic or Chapter headings
            if (lower.startsWith("тема лекции") || lower.startsWith("тема:") || lower.startsWith("запишите тему")) {
                if (output.isNotEmpty()) output.append("\n\n")
                output.append("## ").append(sentence).append("\n")
                inBulletList = false
                continue
            }

            // 2. Definitions or Theorems
            if (lower.startsWith("определение") || lower.startsWith("теорема") || lower.startsWith("лемма") || lower.startsWith("правило")) {
                if (output.isNotEmpty()) output.append("\n\n")
                output.append("> **").append(sentence).append("**\n")
                inBulletList = false
                continue
            }

            // 3. Enumerated items ("во-первых", "пункт", "1.", etc.)
            val isEnumeration = lower.startsWith("во-первых") || lower.startsWith("во-вторых") ||
                    lower.startsWith("в-третьих") || lower.startsWith("в-четвертых") ||
                    lower.startsWith("пункт ") || lower.startsWith("следующий пункт") ||
                    lower.startsWith("первый пункт") || lower.startsWith("второй пункт")

            if (isEnumeration) {
                if (!inBulletList && output.isNotEmpty()) output.append("\n")
                output.append("• ").append(sentence).append("\n")
                inBulletList = true
                continue
            }

            // 4. Important remarks or conclusions
            if (lower.startsWith("обратите внимание") || lower.startsWith("важно помнить") ||
                lower.startsWith("вывод:") || lower.startsWith("подведем итог")) {
                if (output.isNotEmpty()) output.append("\n\n")
                output.append("💡 **").append(sentence).append("**\n")
                inBulletList = false
                continue
            }

            // Regular sentence: group into paragraphs every ~3-4 sentences
            if (inBulletList) {
                output.append("\n")
                inBulletList = false
            }
            if (output.isNotEmpty() && !output.endsWith("\n\n") && !output.endsWith("\n")) {
                output.append(" ")
            }
            output.append(sentence)
        }

        return output.toString().trim()
    }

    /**
     * Given multiple speech recognition alternatives, picks the best candidate,
     * prioritizing ones that match user dictionary replacements or have better structure.
     */
    fun selectBestCandidate(
        candidates: List<String>?,
        replacements: Map<String, String>
    ): String? {
        if (candidates.isNullOrEmpty()) return null
        if (candidates.size == 1) return candidates.first()

        // If any candidate matches a known dictionary key, prioritize it
        val keys = replacements.keys.filter { it.isNotBlank() }
        if (keys.isNotEmpty()) {
            for (candidate in candidates) {
                if (keys.any { key -> candidate.contains(key, ignoreCase = true) }) {
                    return candidate
                }
            }
        }

        // Return candidate with the most words (most complete transcription)
        return candidates.maxByOrNull { it.split(Regex("\\s+")).size } ?: candidates.first()
    }
}

