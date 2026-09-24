package com.example.util

import java.util.Locale

/**
 * Processor for transcribed speech text.
 * - Replaces misheard/misrecognized words according to user-defined dictionary rules.
 * - Converts spoken punctuation commands into punctuation marks.
 * - Normalizes spacing and capitalization after punctuation.
 */
object SpeechPostProcessor {

    /**
     * Default common speech recognition corrections for Russian and common loanwords.
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
        "джпт" to "GPT"
    )

    fun process(
        text: String,
        enableSmartPunctuation: Boolean = true,
        replacements: Map<String, String> = emptyMap()
    ): String {
        if (text.isBlank()) return text

        var result = text

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

        return candidates.first()
    }
}
