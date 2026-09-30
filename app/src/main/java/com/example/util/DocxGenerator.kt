package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import com.example.domain.model.Note
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Universal Office Open XML (.docx) and RTF (.doc) Generator.
 * 
 * Complies strictly with state and academic standards:
 * - ГОСТ 7.32-2017: "Отчет о научно-исследовательской работе. Структура и правила оформления"
 * - ГОСТ Р 7.0.11-2011: "Диссертация и автореферат диссертации"
 * - ГОСТ 2.105-2019 ЕСКД: "Общие требования к текстовым документам"
 * - ГОСТ 7.0.5-2008: "Библиографическая ссылка"
 * - ГОСТ Р 7.0.97-2016: "Организационно-распорядительная документация"
 * - Стандарты ведущих ВУЗов РФ (МГУ, СПбГУ, МГТУ им. Баумана, НИУ ВШЭ)
 * 
 * Features:
 * - Proper academic Title Page (Титульный лист) followed by page break
 * - Table of Contents (Содержание) followed by page break
 * - List of abbreviations and symbols (Перечень сокращений и условных обозначений)
 * - Numbering of pages on all pages EXCEPT the title page (нумерация страниц, кроме титульной)
 * - Section page breaks (Введение, разделы, заключение, список источников с нового листа)
 * - Full inline markdown parsing: NO raw asterisks or markdown syntax (**bold**, *italic*, ###)
 * - Right-aligned recipient blocks for corporate documents (ГОСТ Р 7.0.97-2016)
 * - 1.25 cm first-line indent (красная строка), 1.5 line spacing, Times New Roman
 * - Standard ГОСТ margins: Left 30 mm, Right 15 mm, Top 20 mm, Bottom 20 mm
 */
object DocxGenerator {

    private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale("ru"))

    data class FormattedRun(
        val text: String,
        val isBold: Boolean = false,
        val isItalic: Boolean = false,
        val isUnderline: Boolean = false
    )

    data class TitlePageInfo(
        val organizationLines: List<String>,
        val documentType: String,
        val discipline: String?,
        val topic: String?,
        val authorLines: List<String>,
        val supervisorLines: List<String>,
        val cityAndYear: String?
    )

    data class ParsedDocumentStructure(
        val isOfficialDocument: Boolean,
        val isAcademicWork: Boolean = false,
        val titlePageInfo: TitlePageInfo? = null,
        val headerLines: List<String>,
        val documentTitle: String?,
        val bodyElements: List<BodyElement>,
        val footerLines: List<String>
    )

    sealed class BodyElement {
        data class Paragraph(
            val text: String,
            val isHeading: Boolean = false,
            val headingLevel: Int = 1,
            val isCentered: Boolean = false,
            val isRightAligned: Boolean = false,
            val isPageBreakBefore: Boolean = false,
            val runs: List<FormattedRun> = emptyList()
        ) : BodyElement()

        data class Table(
            val headers: List<String>,
            val rows: List<List<String>>
        ) : BodyElement()

        data class Image(
            val relId: String,
            val widthEmu: Long,
            val heightEmu: Long,
            val caption: String
        ) : BodyElement()

        data class SchematicFigure(
            val caption: String,
            val figureNumber: String,
            val description: String
        ) : BodyElement()

        object PageBreak : BodyElement()
    }

    data class PreparedDocxImage(
        val relId: String,
        val fileName: String,
        val extension: String,
        val bytes: ByteArray,
        val widthEmu: Long,
        val heightEmu: Long,
        val caption: String
    )

    private val OFFICIAL_TITLES = setOf(
        "ЗАЯВЛЕНИЕ", "СЛУЖЕБНАЯ ЗАПИСКА", "ДОКЛАДНАЯ ЗАПИСКА",
        "ОБЪЯСНИТЕЛЬНАЯ ЗАПИСКА", "АКТ", "АКТ ПРИЁМА-ПЕРЕДАЧИ", "АКТ ПРИЕМА-ПЕРЕДАЧИ",
        "ПРОТОКОЛ", "ПРЕТЕНЗИЯ", "УВЕДОМЛЕНИЕ", "ПРИКАЗ", "РАСПОРЯЖЕНИЕ",
        "ДОВЕРЕННОСТЬ", "ДОГОВОР", "СОГЛАШЕНИЕ", "ТРУДОВОЙ ДОГОВОР"
    )

    private val ACADEMIC_WORK_TYPES = setOf(
        "РЕФЕРАТ", "КУРСОВАЯ РАБОТА", "ВЫПУСКНАЯ КВАЛИФИКАЦИОННАЯ РАБОТА",
        "ДИПЛОМНАЯ РАБОТА", "ДИПЛОМНЫЙ ПРОЕКТ", "НАУЧНЫЙ ОТЧЕТ", "ОТЧЕТ О НАУЧНО-ИССЛЕДОВАТЕЛЬСКОЙ РАБОТЕ",
        "ОТЧЕТ ПО ПРАКТИКЕ", "НАУЧНАЯ СТАТЬЯ", "ДИССЕРТАЦИЯ", "АВТОРЕФЕРАТ"
    )

    /**
     * Parses inline markdown tokens into rich runs:
     * - Strips raw asterisks, underscores, hashes, and backticks.
     * - Produces clean run objects with accurate bold/italic flags.
     */
    fun parseInlineRuns(rawText: String, inheritBold: Boolean = false): List<FormattedRun> {
        val cleanInput = rawText.trim()
        if (cleanInput.isEmpty()) return emptyList()

        val runs = mutableListOf<FormattedRun>()
        val regex = Regex("""(\*\*\*(.*?)\*\*\*|\*\*(.*?)\*\*|\*(.*?)\*|___(.*?)___|__(.*?)__|_(.*?)_|`+(.*?)`+)""")
        var lastIndex = 0

        for (match in regex.findAll(cleanInput)) {
            val range = match.range
            if (range.first > lastIndex) {
                val plain = cleanInput.substring(lastIndex, range.first)
                val sanitizedPlain = cleanStrayMarkdown(plain)
                if (sanitizedPlain.isNotEmpty()) {
                    runs.add(FormattedRun(sanitizedPlain, isBold = inheritBold))
                }
            }

            val fullMatch = match.value
            when {
                // ***bold italic***
                match.groups[2] != null -> {
                    val content = cleanStrayMarkdown(match.groups[2]!!.value)
                    if (content.isNotEmpty()) runs.add(FormattedRun(content, isBold = true, isItalic = true))
                }
                // **bold**
                match.groups[3] != null -> {
                    val content = cleanStrayMarkdown(match.groups[3]!!.value)
                    if (content.isNotEmpty()) runs.add(FormattedRun(content, isBold = true))
                }
                // *italic*
                match.groups[4] != null -> {
                    val content = cleanStrayMarkdown(match.groups[4]!!.value)
                    if (content.isNotEmpty()) runs.add(FormattedRun(content, isBold = inheritBold, isItalic = true))
                }
                // ___bold italic___
                match.groups[5] != null -> {
                    val content = cleanStrayMarkdown(match.groups[5]!!.value)
                    if (content.isNotEmpty()) runs.add(FormattedRun(content, isBold = true, isItalic = true))
                }
                // __bold__
                match.groups[6] != null -> {
                    val content = cleanStrayMarkdown(match.groups[6]!!.value)
                    if (content.isNotEmpty()) runs.add(FormattedRun(content, isBold = true))
                }
                // _italic_
                match.groups[7] != null -> {
                    val content = cleanStrayMarkdown(match.groups[7]!!.value)
                    if (content.isNotEmpty()) runs.add(FormattedRun(content, isBold = inheritBold, isItalic = true))
                }
                // `code`
                match.groups[8] != null -> {
                    val content = cleanStrayMarkdown(match.groups[8]!!.value)
                    if (content.isNotEmpty()) runs.add(FormattedRun(content, isBold = inheritBold))
                }
                else -> {
                    val content = cleanStrayMarkdown(fullMatch)
                    if (content.isNotEmpty()) runs.add(FormattedRun(content, isBold = inheritBold))
                }
            }
            lastIndex = range.last + 1
        }

        if (lastIndex < cleanInput.length) {
            val tail = cleanInput.substring(lastIndex)
            val sanitizedTail = cleanStrayMarkdown(tail)
            if (sanitizedTail.isNotEmpty()) {
                runs.add(FormattedRun(sanitizedTail, isBold = inheritBold))
            }
        }

        return if (runs.isEmpty()) {
            val sanitized = cleanStrayMarkdown(cleanInput)
            if (sanitized.isNotEmpty()) listOf(FormattedRun(sanitized, isBold = inheritBold)) else emptyList()
        } else {
            runs
        }
    }

    /**
     * Converts a string of digits/symbols to Unicode superscripts.
     */
    fun toSuperscript(str: String): String {
        val map = mapOf(
            '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
            '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
            '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾',
            'n' to 'ⁿ', 'i' to 'ⁱ', 'x' to 'ˣ', 'y' to 'ʸ'
        )
        return str.map { map[it] ?: it }.joinToString("")
    }

    /**
     * Converts a string of digits/symbols to Unicode subscripts.
     */
    fun toSubscript(str: String): String {
        val map = mapOf(
            '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
            '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
            '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎',
            'a' to 'ₐ', 'e' to 'ₑ', 'o' to 'ₒ', 'x' to 'ₓ', 'i' to 'ᵢ',
            'j' to 'ⱼ', 'k' to 'ₖ', 'l' to 'ₗ', 'm' to 'ₘ', 'n' to 'ₙ',
            'p' to 'ₚ', 's' to 'ₛ', 't' to 'ₜ'
        )
        return str.map { map[it] ?: it }.joinToString("")
    }

    /**
     * Cleans stray markdown symbols and converts raw LaTeX/math syntax to clean, readable Unicode.
     */
    fun cleanAcademicTextAndFormulas(text: String, preserveHeadings: Boolean = true): String {
        val sanitized = FormulaSanitizer.cleanFormulasAndText(text)
        var s = sanitized
            .replace("```", "")
            .replace("`", "")

        if (!preserveHeadings) {
            s = s.replace("**", "")
                .replace("__", "")
                .replace(Regex("""(?m)^#{1,6}\s*"""), "")
                .replace(Regex("""(?m)\s*#{1,6}$"""), "")
        }

        // Strip LaTeX math delimiters: $$, \[, \], \(, \), $
        s = s.replace("$$", "")
            .replace("\\[", "")
            .replace("\\]", "")
            .replace("\\(", "")
            .replace("\\)", "")
            .replace("$", "")

        // LaTeX fractions: \frac{A}{B} -> (A) / (B)
        val fracRegex = Regex("""\\frac\s*\{([^{}]+)\}\s*\{([^{}]+)\}""")
        var fracMatch = fracRegex.find(s)
        var fracIter = 0
        while (fracMatch != null && fracIter < 25) {
            val num = fracMatch.groupValues[1].trim()
            val den = fracMatch.groupValues[2].trim()
            s = s.replaceRange(fracMatch.range, "($num) / ($den)")
            fracMatch = fracRegex.find(s)
            fracIter++
        }

        // LaTeX roots
        s = s.replace(Regex("""\\sqrt\s*\[(.*?)\]\s*\{(.*?)\}""")) { "(${it.groupValues[1]})√(${it.groupValues[2]})" }
        s = s.replace(Regex("""\\sqrt\s*\{(.*?)\}""")) { "√(${it.groupValues[1]})" }

        // Math operators and symbols
        s = s.replace("\\cdot", "·")
            .replace("\\times", "×")
            .replace("\\pm", "±")
            .replace("\\approx", "≈")
            .replace("\\neq", "≠")
            .replace("\\ne", "≠")
            .replace("\\leq", "≤")
            .replace("\\le", "≤")
            .replace("\\geq", "≥")
            .replace("\\ge", "≥")
            .replace("\\infty", "∞")
            .replace("\\sum", "∑")
            .replace("\\int", "∫")
            .replace("\\in", "∈")
            .replace("\\degree", "°")
            .replace("^\\circ", "°")
            .replace("\\circ", "°")
            .replace("\\quad", " ")
            .replace("\\qquad", "   ")

        // Greek letters
        s = s.replace("\\alpha", "α")
            .replace("\\beta", "β")
            .replace("\\gamma", "γ")
            .replace("\\Gamma", "Γ")
            .replace("\\delta", "δ")
            .replace("\\Delta", "Δ")
            .replace("\\epsilon", "ε")
            .replace("\\varepsilon", "ε")
            .replace("\\zeta", "ζ")
            .replace("\\eta", "η")
            .replace("\\theta", "θ")
            .replace("\\Theta", "Θ")
            .replace("\\lambda", "λ")
            .replace("\\Lambda", "Λ")
            .replace("\\mu", "μ")
            .replace("\\nu", "ν")
            .replace("\\xi", "ξ")
            .replace("\\pi", "π")
            .replace("\\Pi", "Π")
            .replace("\\rho", "ρ")
            .replace("\\sigma", "σ")
            .replace("\\Sigma", "Σ")
            .replace("\\tau", "τ")
            .replace("\\phi", "φ")
            .replace("\\Phi", "Φ")
            .replace("\\chi", "χ")
            .replace("\\psi", "ψ")
            .replace("\\omega", "ω")
            .replace("\\Omega", "Ω")

        // \text{...}, \mathrm{...}, etc.
        s = s.replace(Regex("""\\(?:text|mathrm|mathbf|mathit|textbf|textit)\s*\{([^{}]+)\}""")) { it.groupValues[1] }

        // Exponents & subscripts with braces
        s = s.replace(Regex("""\^\{([0-9a-zA-Z+-]+)\}""")) { toSuperscript(it.groupValues[1]) }
        s = s.replace(Regex("""_\{([0-9a-zA-Z+-]+)\}""")) { toSubscript(it.groupValues[1]) }

        // Common simple exponents & subscripts
        s = s.replace("^2", "²")
            .replace("^3", "³")
            .replace("^1", "¹")
            .replace("^0", "⁰")
            .replace("^n", "ⁿ")
            .replace("_0", "₀")
            .replace("_1", "₁")
            .replace("_2", "₂")
            .replace("_3", "₃")
            .replace("_i", "ᵢ")

        // LaTeX arrows and modifiers
        s = s.replace("\\rightarrow", "→")
            .replace("\\leftarrow", "←")
            .replace("\\to", "→")
            .replace("\\limits", "")
            .replace("\\left", "")
            .replace("\\right", "")

        // Remove stray braces left over from math
        s = s.replace(Regex("""\{([0-9a-zA-Zа-яА-ЯёЁ_\-+=/· ]+)\}""")) { it.groupValues[1] }

        return s.trim()
    }

    /**
     * Cleans stray markdown symbols like **, *, __, ` so they never leak into the document text.
     */
    fun cleanStrayMarkdown(text: String): String {
        return cleanAcademicTextAndFormulas(text)
    }

    /**
     * Checks if a line represents an entry in a Table of Contents (Содержание / Оглавление).
     */
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

    private fun isIgnoredTitlePageLine(upper: String): Boolean {
        val trimmed = upper.trim().removePrefix("#").trim()
        if (trimmed.isBlank()) return true
        if (trimmed.startsWith("---") || trimmed.startsWith("===") || trimmed.startsWith("***") ||
            trimmed.startsWith("___") || trimmed.startsWith("<!--") || trimmed.startsWith("```")) {
            return true
        }
        if (trimmed.contains("ТИТУЛЬНЫЙ ЛИСТ") || trimmed.contains("ТИТУЛЬНЫЙ") ||
            trimmed.contains("РАЗРЫВ СТРАНИЦЫ") || trimmed.contains("ОБРАЗЕЦ") ||
            trimmed.contains("РЕКВИЗИТЫ") || trimmed.contains("ПРИМЕР ОФОРМЛЕНИЯ") ||
            trimmed.contains("СТРУКТУРА РАБОТЫ")
        ) {
            return true
        }
        if (trimmed == "PAGE_BREAK" || trimmed == "[PAGE_BREAK]" ||
            trimmed.startsWith("[СТРАНИЦА") || trimmed.startsWith("СТРАНИЦА 1")) {
            return true
        }
        if (trimmed.matches(Regex("""^\d+[\)\.]\s+.*""")) &&
            (trimmed.contains("ТИТУЛЬН") || trimmed.contains("ГОСТ") || trimmed.contains("ЛИСТ"))) {
            return true
        }
        if (trimmed.matches(Regex("""^#+\s*(?:1[\)\.]\s*)?.*(?:ТИТУЛЬН|ГОСТ).*"""))) {
            return true
        }
        if (trimmed.matches(Regex("""^[\(\[]?(?:ГОСТ|по\s+ГОСТ)[^()]*[\)\]]?$""", RegexOption.IGNORE_CASE))) {
            return true
        }
        return false
    }

    fun cleanQuotes(text: String): String {
        var s = text.trim()
        while ((s.startsWith("«") && s.endsWith("»")) ||
               (s.startsWith("\"") && s.endsWith("\"")) ||
               (s.startsWith("'") && s.endsWith("'"))) {
            if (s.length <= 1) break
            s = s.substring(1, s.length - 1).trim()
        }
        return s.trim(' ', '\t', '«', '»', '"', '\'')
    }

    fun cleanDisplayTopicOrDiscipline(text: String, isDiscipline: Boolean): String {
        val prefix = if (isDiscipline) "по дисциплине: «" else "на тему: «"
        val stripped = text
            .replace(Regex("""^(?i)(?:по дисциплине|дисциплина|на тему|тема курсовой работы|тема работы|тема реферата|тема проекта|тема)\s*[:—\-]?\s*"""), "")
            .trim()
        val clean = cleanQuotes(stripped)
        return "$prefix$clean»"
    }

    private fun cleanTitlePageLine(text: String): String {
        return cleanStrayMarkdown(text)
            .replace(Regex("""^\d+[\)\.]\s*"""), "")
            .replace(Regex("""^#{1,6}\s*"""), "")
            .replace(Regex("""\s*#{1,6}$"""), "")
            .replace(Regex("""^[\*\-_—~|`>•▪▫]+\s*"""), "")
            .replace(Regex("""\s*[\*\-_—~|`>•▪▫]+$"""), "")
            .replace(Regex("""^\[(.*?)\]$""")) { it.groupValues[1] }
            .replace(Regex("""\((?:ГОСТ|по\s+ГОСТ)[^()]*\)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\[(?:ГОСТ|по\s+ГОСТ)[^\[\]]*\]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\((?:образец|пример)[^()]*\)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\[(?:образец|пример)[^\[\]]*\]""", RegexOption.IGNORE_CASE), "")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&laquo;", "«")
            .replace("&raquo;", "»")
            .replace("&nbsp;", " ")
            .trim()
    }

    /**
     * Detects if the document text starts with an academic Title Page (Титульный лист).
     */
    fun tryExtractTitlePage(lines: List<String>): Pair<TitlePageInfo?, Int> {
        val nonBlankLines = lines.mapIndexed { idx, s -> idx to s.trim() }.filter { it.second.isNotBlank() }
        if (nonBlankLines.size < 5) return null to 0

        val firstTen = nonBlankLines.take(12).map { it.second.uppercase() }
        val hasMinistryOrUniv = firstTen.any {
            it.contains("МИНИСТЕРСТВО") || it.contains("УНИВЕРСИТЕТ") || it.contains("ИНСТИТУТ") ||
            it.contains("АКАДЕМИЯ") || it.contains("ФЕДЕРАЛЬНОЕ ГОСУДАРСТВЕННОЕ") || it.contains("КАФЕДРА")
        }
        val workTypeIdx = nonBlankLines.indexOfFirst { pair ->
            val upper = pair.second.uppercase().removePrefix("#").trim()
            ACADEMIC_WORK_TYPES.any { upper == it || upper.startsWith("$it ") }
        }

        if (!hasMinistryOrUniv && workTypeIdx == -1) {
            return null to 0
        }

        // Title page boundaries: ends before "--- РАЗРЫВ СТРАНИЦЫ ---", "[РАЗРЫВ СТРАНИЦЫ]", "СОДЕРЖАНИЕ", "ОГЛАВЛЕНИЕ"
        var endIndex = nonBlankLines.size
        for (i in nonBlankLines.indices) {
            val line = nonBlankLines[i].second
            val upper = line.uppercase().removePrefix("#").trim()
            val cleanUpper = upper.replace(Regex("""^#+\s*"""), "").replace(Regex("""^\d+[\)\.]\s*"""), "").trim()
            if (upper.contains("РАЗРЫВ СТРАНИЦЫ") ||
                cleanUpper == "СОДЕРЖАНИЕ" || cleanUpper == "ОГЛАВЛЕНИЕ" ||
                cleanUpper.startsWith("СОДЕРЖАНИЕ ") || cleanUpper.startsWith("ОГЛАВЛЕНИЕ ") ||
                cleanUpper.startsWith("ВВЕДЕНИЕ") || cleanUpper.startsWith("ГЛАВА") || cleanUpper.startsWith("РАЗДЕЛ") ||
                (i > 3 && isCityYearLine(upper))) {
                endIndex = if (isCityYearLine(upper)) nonBlankLines[i].first + 1 else nonBlankLines[i].first
                break
            }
        }

        val titleSlice = lines.subList(0, minOf(endIndex, lines.size)).map { it.trim() }.filter { it.isNotBlank() }
        if (titleSlice.isEmpty()) return null to 0

        val orgLines = mutableListOf<String>()
        var foundDocType = "РЕФЕРАТ"
        var discipline: String? = null
        var topic: String? = null
        val authorLines = mutableListOf<String>()
        val supervisorLines = mutableListOf<String>()
        var cityAndYear: String? = null

        var currentSection = 0 // 0=org, 1=type/topic, 2=people, 3=bottom

        for (line in titleSlice) {
            val cleanLine = cleanTitlePageLine(line)
            val upper = cleanLine.uppercase()
            if (cleanLine.isBlank() || isIgnoredTitlePageLine(upper)) continue

            val matchedType = ACADEMIC_WORK_TYPES.firstOrNull { upper == it || upper.startsWith("$it ") }

            if (matchedType != null) {
                foundDocType = matchedType
                currentSection = 1
                continue
            }

            if (currentSection == 0) {
                orgLines.add(cleanLine)
            } else if (currentSection == 1) {
                if (upper.startsWith("ПО ДИСЦИПЛИНЕ") || upper.startsWith("ДИСЦИПЛИНА:") || upper.startsWith("ДИСЦИПЛИНА ")) {
                    val disc = cleanLine.replace(Regex("""^(?i)(?:по дисциплине|дисциплина)\s*[:—\-]?\s*"""), "")
                    discipline = cleanDisplayTopicOrDiscipline(disc, isDiscipline = true)
                } else if (upper.startsWith("НА ТЕМУ") || upper.startsWith("ТЕМА:") || upper.startsWith("ТЕМА ") || upper.startsWith("ТЕМА КУРСОВОЙ") || upper.startsWith("ТЕМА РАБОТЫ")) {
                    val cleanTopic = cleanLine.replace(Regex("""^(?i)(?:на тему|тема курсовой работы|тема работы|тема реферата|тема проекта|тема)\s*[:—\-]?\s*"""), "")
                    topic = cleanDisplayTopicOrDiscipline(cleanTopic, isDiscipline = false)
                } else if (upper.startsWith("ВЫПОЛНИЛ") || upper.startsWith("АВТОР") || upper.startsWith("СТУДЕНТ")) {
                    currentSection = 2
                    val a = cleanQuotes(cleanLine.replace(Regex("""^(?i)(?:выполнил|автор)\s*[:—\-]?\s*"""), "").trim())
                    val lineText = if (cleanLine.startsWith("Студент", ignoreCase = true)) cleanLine else "Выполнил: $a"
                    authorLines.add(lineText)
                } else if (upper.startsWith("ПРОВЕРИЛ") || upper.startsWith("РУКОВОДИТЕЛЬ") || upper.startsWith("НАУЧНЫЙ")) {
                    currentSection = 2
                    val s = cleanQuotes(cleanLine.replace(Regex("""^(?i)(?:проверил|руководитель|научный руководитель)\s*[:—\-]?\s*"""), "").trim())
                    val lineText = if (cleanLine.startsWith("Научный", ignoreCase = true) || cleanLine.startsWith("Руководитель", ignoreCase = true)) cleanLine else "Проверил: $s"
                    supervisorLines.add(lineText)
                } else if (upper.startsWith("ФАКУЛЬТЕТ") || upper.startsWith("КАФЕДРА") || upper.startsWith("ОТДЕЛЕНИЕ")) {
                    orgLines.add(cleanLine)
                } else if (isCityYearLine(upper)) {
                    cityAndYear = cleanQuotes(cleanLine)
                    currentSection = 3
                } else {
                    val stripped = cleanQuotes(cleanLine.replace(Regex("""^(?i)(?:на тему|тема работы|тема)\s*[:—\-]?\s*"""), ""))
                    if (stripped.isNotBlank()) {
                        if (topic == null) {
                            topic = cleanDisplayTopicOrDiscipline(stripped, isDiscipline = false)
                        } else {
                            val innerTopic = cleanQuotes(topic!!.replace("на тему: «", "").removeSuffix("»"))
                            topic = "на тему: «$innerTopic $stripped»"
                        }
                    }
                }
            } else if (currentSection == 2) {
                if (upper.startsWith("ПРОВЕРИЛ") || upper.startsWith("РУКОВОДИТЕЛЬ") || upper.startsWith("НАУЧНЫЙ")) {
                    val s = cleanQuotes(cleanLine.replace(Regex("""^(?i)(?:проверил|руководитель|научный руководитель)\s*[:—\-]?\s*"""), "").trim())
                    val lineText = if (cleanLine.startsWith("Научный", ignoreCase = true) || cleanLine.startsWith("Руководитель", ignoreCase = true)) cleanLine else "Проверил: $s"
                    supervisorLines.add(lineText)
                } else if (isCityYearLine(upper)) {
                    cityAndYear = cleanQuotes(cleanLine)
                    currentSection = 3
                } else {
                    if (supervisorLines.isNotEmpty()) {
                        supervisorLines.add(cleanLine)
                    } else {
                        authorLines.add(cleanLine)
                    }
                }
            } else {
                if (isCityYearLine(upper)) {
                    cityAndYear = cleanQuotes(cleanLine)
                }
            }
        }

        if (cityAndYear == null) {
            cityAndYear = "Москва, ${SimpleDateFormat("yyyy", Locale.getDefault()).format(Date())}"
        }

        val filteredOrg = orgLines.filter { line ->
            val u = line.uppercase()
            !isIgnoredTitlePageLine(u) &&
            !u.startsWith("ВЫПОЛНИЛ") && !u.startsWith("ПРОВЕРИЛ") &&
            !u.startsWith("АВТОР") && !u.startsWith("СТУДЕНТ") &&
            !u.startsWith("РУКОВОДИТЕЛЬ") && !u.startsWith("НАУЧНЫЙ") &&
            !u.startsWith("ТЕМА") && !u.startsWith("НА ТЕМУ") && !u.startsWith("ДИСЦИПЛИНА") &&
            line.length in 3..140
        }
        val filteredAuthor = authorLines.filter { !isIgnoredTitlePageLine(it.uppercase()) }
        val filteredSupervisor = supervisorLines.filter { !isIgnoredTitlePageLine(it.uppercase()) }

        val info = TitlePageInfo(
            organizationLines = if (filteredOrg.isNotEmpty()) filteredOrg else listOf("МИНИСТЕРСТВО НАУКИ И ВЫСШЕГО ОБРАЗОВАНИЯ РОССИЙСКОЙ ФЕДЕРАЦИИ"),
            documentType = foundDocType,
            discipline = discipline,
            topic = topic,
            authorLines = filteredAuthor,
            supervisorLines = filteredSupervisor,
            cityAndYear = cityAndYear
        )

        return info to endIndex
    }

    private fun isCityYearLine(upper: String): Boolean {
        if (upper.contains("ГОСТ") || upper.contains("ТИТУЛЬН") || upper.contains("ГРУПП") || upper.contains("КУРС")) return false
        val hasYear = Regex("""\b(202\d|203\d)\b""").containsMatchIn(upper)
        if (!hasYear) return false
        val hasCity = upper.contains("МОСКВА") || upper.contains("САНКТ-ПЕТЕРБУРГ") ||
                upper.contains("НОВОСИБИРСК") || upper.contains("ЕКАТЕРИНБУРГ") ||
                upper.contains("КАЗАНЬ") || upper.contains("НИЖНИЙ НОВГОРОД") ||
                upper.contains("ТОМСК") || upper.contains("САМАРА") ||
                upper.contains("Г.") || upper.contains("ГОРОД") ||
                upper.matches(Regex("""^[А-ЯЁA-Z\s\.\-]+,\s*(202\d|203\d)(\s*г\.?)?$""")) ||
                upper.matches(Regex("""^(202\d|203\d)(\s*г\.?)?$"""))
        return hasCity
    }

    /**
     * Parses note content into structured requisites, title pages, sections, tables and footers.
     */
    fun parseStructure(note: Note): ParsedDocumentStructure {
        val synchronizedContent = TableOfContentsExtractor.synchronizeDocumentToc(note.content)
        val allLines = synchronizedContent.lines()
        val (titlePageInfo, titlePageEndIndex) = tryExtractTitlePage(allLines)
        val remainingLines = if (titlePageInfo != null && titlePageEndIndex < allLines.size) {
            allLines.subList(titlePageEndIndex, allLines.size)
        } else if (titlePageInfo != null) {
            emptyList()
        } else {
            allLines
        }

        val headerLines = mutableListOf<String>()
        var foundTitle: String? = null
        val bodyElements = mutableListOf<BodyElement>()
        val footerLines = mutableListOf<String>()
        val tocAppendixEntries = mutableListOf<String>()

        var phase = 0 // 0 = looking for header/title, 1 = body, 2 = footer
        val currentTableLines = mutableListOf<String>()

        fun flushTable() {
            if (currentTableLines.isNotEmpty()) {
                val tbl = parseTableFromLines(currentTableLines)
                if (tbl != null) {
                    bodyElements.add(tbl)
                }
                currentTableLines.clear()
            }
        }

        fun isTitleLine(trimmed: String): Boolean {
            val upper = trimmed.uppercase().removePrefix("#").trim()
            if (upper.isBlank()) return false
            if (OFFICIAL_TITLES.contains(upper)) return true
            if (OFFICIAL_TITLES.any { upper.startsWith(it) && (upper.length == it.length || upper[it.length] == ' ' || upper[it.length] == ':') }) return true
            if (trimmed.startsWith("# ") && upper.length < 60) return true
            return false
        }

        var inToc = false

        for (rawLine in remainingLines) {
            val trimmed = rawLine.trim()
            val upperTrimmed = trimmed.uppercase().removePrefix("#").trim()

            // Detect Page Break indicators
            if (upperTrimmed == "--- РАЗРЫВ СТРАНИЦЫ ---" || upperTrimmed == "[РАЗРЫВ СТРАНИЦЫ]" ||
                upperTrimmed == "[PAGE_BREAK]" || (trimmed == "---" && phase == 1)) {
                flushTable()
                val hasPrecedingContent = bodyElements.any { it !is BodyElement.PageBreak && (it !is BodyElement.Paragraph || it.text.isNotBlank()) }
                if (hasPrecedingContent && (bodyElements.isEmpty() || bodyElements.last() !is BodyElement.PageBreak)) {
                    bodyElements.add(BodyElement.PageBreak)
                }
                inToc = false
                continue
            }

            // Detect Table of Contents (Содержание / Оглавление)
            if (upperTrimmed == "СОДЕРЖАНИЕ" || upperTrimmed == "ОГЛАВЛЕНИЕ" ||
                upperTrimmed.startsWith("СОДЕРЖАНИЕ ") || upperTrimmed.startsWith("ОГЛАВЛЕНИЕ ")) {
                flushTable()
                val content = cleanStrayMarkdown(upperTrimmed)
                bodyElements.add(
                    BodyElement.Paragraph(
                        text = content,
                        isHeading = true,
                        headingLevel = 1,
                        isCentered = true,
                        isPageBreakBefore = (titlePageInfo == null && bodyElements.isNotEmpty()),
                        runs = parseInlineRuns(content, inheritBold = true)
                    )
                )
                inToc = true
                phase = 1
                continue
            }

            if (inToc) {
                if (isTocEntry(trimmed)) {
                    val content = cleanStrayMarkdown(trimmed)
                    if (content.contains("ПРИЛОЖЕНИЕ", ignoreCase = true) || content.contains("ПРИЛОЖЕНИЯ", ignoreCase = true)) {
                        tocAppendixEntries.add(content)
                    }
                    bodyElements.add(
                        BodyElement.Paragraph(
                            text = content,
                            isHeading = false,
                            headingLevel = 0,
                            isCentered = false,
                            isPageBreakBefore = false,
                            runs = parseInlineRuns(content)
                        )
                    )
                    continue
                } else if (trimmed.isBlank()) {
                    continue
                } else {
                    inToc = false
                    // Fall through to normal body processing so this line is NOT lost!
                }
            }

            if (phase == 0) {
                if (isTitleLine(trimmed)) {
                    foundTitle = cleanStrayMarkdown(upperTrimmed)
                    phase = 1
                } else if (headerLines.isEmpty()) {
                    if (isHeaderStart(trimmed)) {
                        headerLines.add(cleanStrayMarkdown(trimmed))
                    } else if (trimmed.isBlank()) {
                        // ignore leading blank lines
                    } else {
                        phase = 1
                        processBodyLine(rawLine, currentTableLines, bodyElements)
                    }
                } else {
                    if (trimmed.isNotBlank()) {
                        headerLines.add(cleanStrayMarkdown(trimmed))
                    }
                }
            } else if (phase == 1) {
                if (isFooterLine(trimmed)) {
                    flushTable()
                    footerLines.add(cleanStrayMarkdown(trimmed))
                    phase = 2
                } else {
                    processBodyLine(rawLine, currentTableLines, bodyElements)
                }
            } else {
                if (trimmed.isNotBlank()) {
                    footerLines.add(cleanStrayMarkdown(trimmed))
                }
            }
        }

        flushTable()

        val finalTitle = foundTitle ?: if (OFFICIAL_TITLES.any { note.title.uppercase().contains(it) }) {
            cleanStrayMarkdown(note.title.uppercase())
        } else if (headerLines.isNotEmpty()) {
            "ЗАЯВЛЕНИЕ"
        } else {
            cleanStrayMarkdown(note.title.ifBlank { "ДОКУМЕНТ" })
        }

        val isOfficial = headerLines.isNotEmpty() || foundTitle != null ||
                note.title.contains("Заявление", ignoreCase = true) ||
                note.title.contains("Служебная", ignoreCase = true) ||
                note.title.contains("Акт", ignoreCase = true) ||
                note.title.contains("Протокол", ignoreCase = true)

        val isAcademic = titlePageInfo != null ||
                note.title.contains("Реферат", ignoreCase = true) ||
                note.title.contains("Курсовая", ignoreCase = true) ||
                note.title.contains("Диплом", ignoreCase = true) ||
                note.title.contains("Отчет", ignoreCase = true)

        // GUARANTEE: If TOC or text specifies Appendices (ПРИЛОЖЕНИЯ), but body elements don't contain them,
        // automatically generate and append the missing appendices with graphical figures!
        val hasExistingAppendices = bodyElements.any {
            (it is BodyElement.Paragraph && (it.text.startsWith("ПРИЛОЖЕНИЕ", ignoreCase = true) || it.text.contains("ПРИЛОЖЕНИЕ А", ignoreCase = true))) ||
            (it is BodyElement.SchematicFigure)
        }

        if (!hasExistingAppendices && (tocAppendixEntries.isNotEmpty() || note.content.contains("ПРИЛОЖЕНИ", ignoreCase = true))) {
            val appendicesToSynthesize = if (tocAppendixEntries.isNotEmpty()) {
                tocAppendixEntries.mapNotNull { entry ->
                    val clean = entry.replace(Regex("""[\.\s\—\-\t…]+$"""), "")
                        .replace(Regex("""[\.\s\—\-\t…]+\d{1,4}$"""), "")
                        .trim()
                    val match = Regex("""ПРИЛОЖЕНИЕ\s*([А-ЯA-Z\d]+)?(?:[\.\:\—\-]\s*(.*))?""", RegexOption.IGNORE_CASE).find(clean)
                    if (match != null) {
                        val num = match.groupValues[1].ifBlank { "А" }
                        val sub = match.groupValues[2].ifBlank {
                            if (num.contains("Б") || num == "2") "Блок-схема алгоритма реализации и вычислений"
                            else "Структурно-логическая схема и модель исследования"
                        }
                        num to sub
                    } else null
                }
            } else {
                listOf(
                    "А" to "Структурно-логическая схема и функциональная архитектура",
                    "Б" to "Блок-схема алгоритма практической реализации"
                )
            }

            for ((letter, subtitle) in appendicesToSynthesize) {
                if (bodyElements.isEmpty() || bodyElements.last() !is BodyElement.PageBreak) {
                    bodyElements.add(BodyElement.PageBreak)
                }
                val appHeading = "ПРИЛОЖЕНИЕ $letter\n(справочное)"
                bodyElements.add(
                    BodyElement.Paragraph(
                        text = appHeading,
                        isHeading = true,
                        headingLevel = 1,
                        isCentered = true,
                        isPageBreakBefore = true,
                        runs = parseInlineRuns(appHeading, inheritBold = true)
                    )
                )
                bodyElements.add(
                    BodyElement.Paragraph(
                        text = subtitle,
                        isHeading = true,
                        headingLevel = 2,
                        isCentered = true,
                        isPageBreakBefore = false,
                        runs = parseInlineRuns(subtitle, inheritBold = true)
                    )
                )
                val figCaption = "Рисунок $letter.1 — $subtitle"
                val flowDesc = if (letter.contains("Б") || letter == "2") {
                    "Постановка задачи -> Сбор и верификация исходных параметров -> Итерационные вычисления -> Формирование итогового отчета"
                } else {
                    "Входные данные и нормативная база -> Модуль аналитической обработки -> Структурный синтез -> Результирующие показатели"
                }
                bodyElements.add(
                    BodyElement.SchematicFigure(
                        caption = figCaption,
                        figureNumber = "$letter.1",
                        description = flowDesc
                    )
                )
            }
        }

        return ParsedDocumentStructure(
            isOfficialDocument = isOfficial,
            isAcademicWork = isAcademic,
            titlePageInfo = titlePageInfo,
            headerLines = headerLines,
            documentTitle = finalTitle,
            bodyElements = bodyElements,
            footerLines = footerLines
        )
    }

    private fun isHeaderStart(trimmed: String): Boolean {
        return trimmed.startsWith("Кому:", ignoreCase = true) ||
                trimmed.startsWith("Кому ", ignoreCase = true) ||
                trimmed.startsWith("От кого:", ignoreCase = true) ||
                trimmed.startsWith("От кого ", ignoreCase = true) ||
                trimmed.startsWith("От:", ignoreCase = true) ||
                trimmed.startsWith("Директору", ignoreCase = true) ||
                trimmed.startsWith("Руководителю", ignoreCase = true) ||
                trimmed.startsWith("Ректору", ignoreCase = true) ||
                trimmed.startsWith("Генеральному", ignoreCase = true) ||
                trimmed.startsWith("Начальнику", ignoreCase = true) ||
                trimmed.startsWith("Председателю", ignoreCase = true) ||
                trimmed.startsWith("Декану", ignoreCase = true) ||
                trimmed.startsWith("Заведующему", ignoreCase = true) ||
                trimmed.startsWith("Командиру", ignoreCase = true) ||
                trimmed.startsWith("В совет", ignoreCase = true) ||
                trimmed.startsWith("В аттестационную", ignoreCase = true) ||
                trimmed.startsWith("В комиссию", ignoreCase = true) ||
                trimmed.startsWith("Паспорт:", ignoreCase = true) ||
                trimmed.startsWith("Проживающего", ignoreCase = true)
    }

    private fun isFooterLine(trimmed: String): Boolean {
        return trimmed.startsWith("Дата:", ignoreCase = true) ||
                trimmed.startsWith("Подпись:", ignoreCase = true) ||
                trimmed.startsWith("«___»") ||
                trimmed.contains("____________ /") ||
                trimmed.startsWith("Передал:", ignoreCase = true) ||
                trimmed.startsWith("Принял:", ignoreCase = true)
    }

    fun isTableRow(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.length < 2) return false
        if (trimmed.startsWith("|")) return true
        return trimmed.count { it == '|' } >= 2
    }

    fun isTableSeparator(line: String): Boolean {
        val clean = line.replace("|", "").replace("-", "").replace(":", "").replace(" ", "").trim()
        return clean.isEmpty() && line.contains("-")
    }

    fun parseTableRowCells(line: String): List<String> {
        val trimmed = line.trim()
        val stripped = trimmed.removePrefix("|").removeSuffix("|")
        return stripped.split("|").map { cleanStrayMarkdown(it).trim() }
    }

    fun parseTableFromLines(lines: List<String>): BodyElement.Table? {
        val parsedRows = mutableListOf<List<String>>()
        for (raw in lines) {
            val t = raw.trim()
            if (isTableSeparator(t)) continue
            val cells = parseTableRowCells(t)
            if (cells.isNotEmpty() && cells.any { it.isNotBlank() }) {
                parsedRows.add(cells)
            }
        }
        if (parsedRows.isEmpty()) return null
        val headers = parsedRows.first()
        val rows = if (parsedRows.size > 1) parsedRows.subList(1, parsedRows.size) else emptyList()
        return BodyElement.Table(headers, rows)
    }

    private fun processBodyLine(
        rawLine: String,
        tableLines: MutableList<String>,
        bodyElements: MutableList<BodyElement>
    ) {
        val trimmed = rawLine.trim()
        if (isTableRow(trimmed)) {
            tableLines.add(trimmed)
            return
        }

        if (tableLines.isNotEmpty()) {
            val tbl = parseTableFromLines(tableLines)
            if (tbl != null) {
                bodyElements.add(tbl)
            }
            tableLines.clear()
        }

        if (trimmed.isBlank()) return

        // 1. In-line Markdown Image: ![Caption](uri)
        val mdImgMatch = Regex("""^!\[(.*?)\]\((.*?)\)""").find(trimmed)
        if (mdImgMatch != null) {
            val caption = mdImgMatch.groupValues[1].trim().ifBlank { "Иллюстрационный материал" }
            val uriOrDesc = mdImgMatch.groupValues[2].trim()
            val cap = if (caption.startsWith("Рисунок", ignoreCase = true)) caption else "Рисунок — $caption"
            bodyElements.add(
                BodyElement.SchematicFigure(
                    caption = cap,
                    figureNumber = "",
                    description = uriOrDesc
                )
            )
            return
        }

        // Clean any leading markdown formatting like **[Рисунок 1 — ...]**, ### Рисунок 1, etc.
        val unformatted = trimmed
            .removePrefix("#").removePrefix("#").removePrefix("#").trim()
            .removeSurrounding("**", "**").trim()
            .removeSurrounding("*", "*").trim()
            .removeSurrounding("`", "`").trim()

        val cleanCandidate = if (unformatted.startsWith("[") && unformatted.endsWith("]")) {
            unformatted.substring(1, unformatted.length - 1).trim()
        } else {
            unformatted
        }

        // 2. Bracketed or Unbracketed Figure, Schema, Diagram, Graphic Material
        // Handles: Рисунок 1, Рисунок 1.1, Рисунок А.1, Схема 1, Графический материал, etc.
        val figRegex = Regex(
            """^(?:Рисунок|Схема|Иллюстрация|Диаграмма|График|Графический материал|Графические материалы)\s*([А-ЯA-Z\d]+[\.\d]*)?\s*[:—\-\.]?\s*(.+)""",
            RegexOption.IGNORE_CASE
        )
        val figMatch = figRegex.find(cleanCandidate)
        if (figMatch != null) {
            val rawNum = figMatch.groupValues[1].trim().trimEnd('.')
            val rawDesc = figMatch.groupValues[2].trim()
            val cleanDesc = cleanStrayMarkdown(rawDesc).trim()
            val cleanNum = rawNum.ifBlank { "" }
            val cap = if (cleanNum.isNotBlank()) "Рисунок $cleanNum — $cleanDesc" else "Рисунок — $cleanDesc"
            bodyElements.add(
                BodyElement.SchematicFigure(
                    caption = cap,
                    figureNumber = cleanNum,
                    description = cleanDesc
                )
            )
            return
        }

        // 4. Appendix Section: ПРИЛОЖЕНИЕ А / ПРИЛОЖЕНИЕ 1
        val appMatch = Regex("""^(?:#+\s*)?(ПРИЛОЖЕНИЕ\s+[А-ЯA-Z\d]+(\.|\:)?(\s+.*)?)""", RegexOption.IGNORE_CASE).find(trimmed)
        if (appMatch != null) {
            val appTitle = cleanStrayMarkdown(appMatch.groupValues[1].uppercase())
            bodyElements.add(
                BodyElement.Paragraph(
                    text = appTitle,
                    isHeading = true,
                    headingLevel = 1,
                    isCentered = true,
                    isPageBreakBefore = true,
                    runs = parseInlineRuns(appTitle, inheritBold = true)
                )
            )
            return
        }

        val upper = trimmed.uppercase().removePrefix("#").trim()
        val isMajorSection = isMajorAcademicSection(upper)

        when {
            trimmed.startsWith("# ") -> {
                val content = cleanStrayMarkdown(trimmed.removePrefix("# "))
                bodyElements.add(
                    BodyElement.Paragraph(
                        text = content,
                        isHeading = true,
                        headingLevel = 1,
                        isCentered = isMajorSection,
                        isPageBreakBefore = isMajorSection,
                        runs = parseInlineRuns(content, inheritBold = true)
                    )
                )
            }
            trimmed.startsWith("## ") -> {
                val content = cleanStrayMarkdown(trimmed.removePrefix("## "))
                bodyElements.add(
                    BodyElement.Paragraph(
                        text = content,
                        isHeading = true,
                        headingLevel = 2,
                        isCentered = false,
                        isPageBreakBefore = false,
                        runs = parseInlineRuns(content, inheritBold = true)
                    )
                )
            }
            trimmed.startsWith("### ") -> {
                val content = cleanStrayMarkdown(trimmed.removePrefix("### "))
                bodyElements.add(
                    BodyElement.Paragraph(
                        text = content,
                        isHeading = true,
                        headingLevel = 3,
                        isCentered = false,
                        isPageBreakBefore = false,
                        runs = parseInlineRuns(content, inheritBold = true)
                    )
                )
            }
            isNumberedListItem(trimmed) -> {
                val content = cleanStrayMarkdown(trimmed)
                bodyElements.add(
                    BodyElement.Paragraph(
                        text = content,
                        isHeading = false,
                        headingLevel = 0,
                        isCentered = false,
                        isPageBreakBefore = false,
                        runs = parseInlineRuns(content)
                    )
                )
            }
            isBulletListItem(trimmed) -> {
                val clean = cleanStrayMarkdown(
                    trimmed.removePrefix("- ")
                        .removePrefix("* ")
                        .removePrefix("•\t")
                        .removePrefix("• ")
                        .removePrefix("– ")
                        .removePrefix("— ")
                )
                val content = "•\t$clean"
                bodyElements.add(
                    BodyElement.Paragraph(
                        text = content,
                        isHeading = false,
                        headingLevel = 0,
                        isCentered = false,
                        isPageBreakBefore = false,
                        runs = parseInlineRuns(content)
                    )
                )
            }
            isMajorSection -> {
                val content = cleanStrayMarkdown(trimmed)
                bodyElements.add(
                    BodyElement.Paragraph(
                        text = content,
                        isHeading = true,
                        headingLevel = 1,
                        isCentered = true,
                        isPageBreakBefore = true,
                        runs = parseInlineRuns(content, inheritBold = true)
                    )
                )
            }
            else -> {
                val content = cleanStrayMarkdown(trimmed)
                bodyElements.add(
                    BodyElement.Paragraph(
                        text = content,
                        isHeading = false,
                        headingLevel = 0,
                        isCentered = false,
                        isPageBreakBefore = false,
                        runs = parseInlineRuns(content)
                    )
                )
            }
        }
    }

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
                (upper.startsWith("ПРИЛОЖЕНИЕ ") && upper.length < 40) ||
                upper.matches(Regex("""^(РАЗДЕЛ|ГЛАВА)\s+\d+.*"""))
    }

    private fun escapeXml(text: String): String {
        val sanitized = text.filter { ch ->
            ch == '\t' || ch == '\n' || ch == '\r' || (ch.code in 0x20..0xD7FF) || (ch.code in 0xE000..0xFFFD)
        }
        return sanitized.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }

    /**
     * Loads a bitmap from Android content URI, file URI, or local file path.
     */
    fun loadBitmapFromUriOrPath(context: Context, pathOrUri: String, maxWidth: Int, maxHeight: Int): Bitmap? {
        return try {
            val uri = if (pathOrUri.startsWith("content://") || pathOrUri.startsWith("file://")) {
                Uri.parse(pathOrUri)
            } else {
                Uri.fromFile(File(pathOrUri))
            }
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
            if (options.outWidth <= 0 || options.outHeight <= 0) return null

            var sampleSize = 1
            while (options.outWidth / (sampleSize * 2) >= maxWidth && options.outHeight / (sampleSize * 2) >= maxHeight) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val orig = BitmapFactory.decodeStream(stream, null, decodeOptions) ?: return null
                val scale = minOf(maxWidth.toFloat() / orig.width, maxHeight.toFloat() / orig.height, 1.0f)
                val scaledW = (orig.width * scale).toInt().coerceAtLeast(1)
                val scaledH = (orig.height * scale).toInt().coerceAtLeast(1)
                if (scaledW == orig.width && scaledH == orig.height) orig
                else Bitmap.createScaledBitmap(orig, scaledW, scaledH, true)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Generates a high-quality academic engineering blueprint/flowchart diagram
     * compliant with ГОСТ 7.32-2017 and ЕСКД standards.
     */
    fun createSchematicDiagramBitmap(title: String, figureNumber: String, description: String): Bitmap {
        val width = 1200
        val height = 620
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Background
        val bgPaint = Paint().apply {
            color = Color.rgb(250, 251, 253)
            style = Paint.Style.FILL
        }
        canvas.drawColor(Color.WHITE)
        canvas.drawRoundRect(RectF(10f, 10f, (width - 10).toFloat(), (height - 10).toFloat()), 20f, 20f, bgPaint)

        // 2. Blueprint Grid lines
        val gridPaint = Paint().apply {
            color = Color.rgb(232, 236, 241)
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }
        var gx = 25f
        while (gx < width - 20) {
            canvas.drawLine(gx, 20f, gx, (height - 20).toFloat(), gridPaint)
            gx += 35f
        }
        var gy = 25f
        while (gy < height - 20) {
            canvas.drawLine(20f, gy, (width - 20).toFloat(), gy, gridPaint)
            gy += 35f
        }

        // 3. Technical Border
        val borderPaint = Paint().apply {
            color = Color.rgb(63, 81, 181)
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        }
        canvas.drawRoundRect(RectF(14f, 14f, (width - 14).toFloat(), (height - 14).toFloat()), 16f, 16f, borderPaint)

        // 4. Header Bar
        val headerBarPaint = Paint().apply {
            color = Color.rgb(232, 234, 246)
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(RectF(14f, 14f, (width - 14).toFloat(), 72f), 16f, 16f, headerBarPaint)
        canvas.drawRect(RectF(14f, 40f, (width - 14).toFloat(), 72f), headerBarPaint)

        val headerTextPaint = Paint().apply {
            color = Color.rgb(26, 35, 126)
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val cleanHeader = if (title.length > 55) title.take(52) + "..." else title
        canvas.drawText("СХЕМА: $cleanHeader", 36f, 48f, headerTextPaint)

        val gostTagPaint = Paint().apply {
            color = Color.rgb(92, 107, 115)
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("ГОСТ 7.32-2017", (width - 36).toFloat(), 48f, gostTagPaint)

        // 5. Draw Process Flow / Functional Architecture Blocks
        val rawSteps = description.split(Regex("""(?i)(?:->|-->|→|;|\n|\t|•|—)"""))
            .map { it.trim().removePrefix("-").trim() }
            .filter { it.isNotBlank() && it.length > 2 }

        val steps = if (rawSteps.size in 2..5) {
            rawSteps
        } else {
            listOf(
                "1. Сбор и ввод данных",
                "2. Предварительная обработка",
                "3. Анализ и вычисления",
                "4. Выходные результаты"
            )
        }

        val blockCount = steps.size
        val totalSpacing = (blockCount - 1) * 35f
        val blockWidth = ((width - 120f - totalSpacing) / blockCount).coerceIn(160f, 260f)
        val blockHeight = 160f
        val startY = 190f

        val blockBgPaint = Paint().apply {
            color = Color.rgb(237, 242, 247)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val blockBorderPaint = Paint().apply {
            color = Color.rgb(33, 150, 243)
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            isAntiAlias = true
        }
        val blockTitlePaint = Paint().apply {
            color = Color.rgb(33, 33, 33)
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val arrowPaint = Paint().apply {
            color = Color.rgb(33, 150, 243)
            strokeWidth = 4f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        val arrowHeadPaint = Paint().apply {
            color = Color.rgb(33, 150, 243)
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val totalWidth = blockCount * blockWidth + totalSpacing
        var currentX = (width - totalWidth) / 2f

        for (i in 0 until blockCount) {
            val rect = RectF(currentX, startY, currentX + blockWidth, startY + blockHeight)
            canvas.drawRoundRect(rect, 14f, 14f, blockBgPaint)
            canvas.drawRoundRect(rect, 14f, 14f, blockBorderPaint)

            val tabPaint = Paint().apply {
                color = when (i % 4) {
                    0 -> Color.rgb(33, 150, 243)
                    1 -> Color.rgb(76, 175, 80)
                    2 -> Color.rgb(255, 152, 0)
                    else -> Color.rgb(156, 39, 176)
                }
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(RectF(currentX, startY, currentX + blockWidth, startY + 12f), 14f, 14f, tabPaint)

            val stepText = steps[i]
            val words = stepText.split(" ")
            var line1 = ""
            var line2 = ""
            for (w in words) {
                if ((line1 + " " + w).length < 16) {
                    line1 = (line1 + " " + w).trim()
                } else {
                    line2 = (line2 + " " + w).trim()
                }
            }
            val textY = startY + blockHeight / 2f + (if (line2.isNotBlank()) -6f else 8f)
            canvas.drawText(line1, currentX + blockWidth / 2f, textY, blockTitlePaint)
            if (line2.isNotBlank()) {
                canvas.drawText(line2, currentX + blockWidth / 2f, textY + 28f, blockTitlePaint)
            }

            if (i < blockCount - 1) {
                val arrowStartX = currentX + blockWidth + 6f
                val arrowEndX = arrowStartX + 22f
                val arrowY = startY + blockHeight / 2f
                canvas.drawLine(arrowStartX, arrowY, arrowEndX, arrowY, arrowPaint)

                val path = android.graphics.Path().apply {
                    moveTo(arrowEndX + 6f, arrowY)
                    lineTo(arrowEndX - 6f, arrowY - 8f)
                    lineTo(arrowEndX - 6f, arrowY + 8f)
                    close()
                }
                canvas.drawPath(path, arrowHeadPaint)
            }

            currentX += blockWidth + 35f
        }

        // 6. Footer badge inside box
        val footerTextPaint = Paint().apply {
            color = Color.rgb(120, 144, 156)
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Научно-исследовательский и аналитический графический материал", width / 2f, (height - 40).toFloat(), footerTextPaint)

        return bitmap
    }

    /**
     * Generates a genuine Office Open XML (.docx) package with standard layout:
     * - Numbering of pages on all pages EXCEPT title page (w:titlePg + footer1.xml)
     * - Academic Title page with page break
     * - Clean runs without stray markdown asterisks
     * - Embedded high-resolution graphic figures and attached images
     */
    fun generateDocxFile(context: Context, note: Note, includeSignature: Boolean = true): File {
        val cleanTitle = note.title.replace(Regex("[^a-zA-Zа-яА-ЯёЁ0-9_\\-]"), "_").trim('_').take(35).ifBlank { "document" }
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val docxFile = File(exportDir, "${cleanTitle}.docx")

        val structure = parseStructure(note)
        val hasSignature = includeSignature && SignatureManager.hasSignature(context)

        // Prepare all physical and schematic images
        val attachedUris = ShareExportUtil.parseImageUris(note.imageUrisJson)
        val preparedImages = mutableListOf<PreparedDocxImage>()
        val figureElements = structure.bodyElements.filterIsInstance<BodyElement.SchematicFigure>()

        var attachedIdx = 0
        for (figure in figureElements) {
            var bmp: Bitmap? = null
            var caption = figure.caption
            if (attachedIdx < attachedUris.size) {
                val uriStr = attachedUris[attachedIdx++]
                bmp = loadBitmapFromUriOrPath(context, uriStr, 1600, 1200)
            }
            if (bmp == null) {
                val t = figure.caption.removePrefix("Рисунок").trim().removePrefix("—").removePrefix(":").removePrefix("-").trim().ifBlank { "Структурная схема" }
                bmp = createSchematicDiagramBitmap(t, figure.figureNumber, figure.description)
            }
            val stream = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.PNG, 95, stream)
            val pngBytes = stream.toByteArray()
            val imgIndex = preparedImages.size + 1
            val aspect = if (bmp.height > 0) bmp.width.toDouble() / bmp.height.toDouble() else 1.6
            val hEmu = 3200000L
            val wEmu = (aspect * hEmu).toLong().coerceIn(2500000L, 5400000L)
            preparedImages.add(
                PreparedDocxImage(
                    relId = "rIdImg$imgIndex",
                    fileName = "image_$imgIndex.png",
                    extension = "png",
                    bytes = pngBytes,
                    widthEmu = wEmu,
                    heightEmu = hEmu,
                    caption = caption
                )
            )
        }

        while (attachedIdx < attachedUris.size) {
            val uriStr = attachedUris[attachedIdx++]
            val bmp = loadBitmapFromUriOrPath(context, uriStr, 1600, 1200)
            if (bmp != null) {
                val stream = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.PNG, 95, stream)
                val pngBytes = stream.toByteArray()
                val imgIndex = preparedImages.size + 1
                val aspect = if (bmp.height > 0) bmp.width.toDouble() / bmp.height.toDouble() else 1.6
                val hEmu = 3200000L
                val wEmu = (aspect * hEmu).toLong().coerceIn(2500000L, 5400000L)
                preparedImages.add(
                    PreparedDocxImage(
                        relId = "rIdImg$imgIndex",
                        fileName = "image_$imgIndex.png",
                        extension = "png",
                        bytes = pngBytes,
                        widthEmu = wEmu,
                        heightEmu = hEmu,
                        caption = "Рисунок $imgIndex — Иллюстрационный материал"
                    )
                )
            }
        }

        val documentXml = buildDocumentXml(note, structure, hasSignature, preparedImages)

        ZipOutputStream(FileOutputStream(docxFile)).use { zos ->
            // 1. [Content_Types].xml
            val contentTypesXml = buildContentTypesXml(hasSignature, preparedImages.isNotEmpty())
            zos.putNextEntry(ZipEntry("[Content_Types].xml"))
            zos.write(contentTypesXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 2. _rels/.rels
            zos.putNextEntry(ZipEntry("_rels/.rels"))
            zos.write(ROOT_RELS_XML.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 3. word/_rels/document.xml.rels
            val wordRelsXml = buildWordRelsXml(hasSignature, preparedImages)
            zos.putNextEntry(ZipEntry("word/_rels/document.xml.rels"))
            zos.write(wordRelsXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 4. word/settings.xml
            zos.putNextEntry(ZipEntry("word/settings.xml"))
            zos.write(SETTINGS_XML.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 5. word/styles.xml
            zos.putNextEntry(ZipEntry("word/styles.xml"))
            zos.write(STYLES_XML.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 6. word/footer1.xml (Page Numbering - ГОСТ 7.32: centered page number)
            zos.putNextEntry(ZipEntry("word/footer1.xml"))
            zos.write(FOOTER_XML.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 7. word/media/signature.png (if signature embedded)
            if (hasSignature) {
                val sigFile = SignatureManager.getSignatureFile(context)
                if (sigFile.exists()) {
                    zos.putNextEntry(ZipEntry("word/media/signature.png"))
                    zos.write(sigFile.readBytes())
                    zos.closeEntry()
                }
            }

            // 8. word/media/image_X.png (embedded images & schematic figures)
            for (img in preparedImages) {
                zos.putNextEntry(ZipEntry("word/media/${img.fileName}"))
                zos.write(img.bytes)
                zos.closeEntry()
            }

            // 9. word/document.xml
            zos.putNextEntry(ZipEntry("word/document.xml"))
            zos.write(documentXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        return docxFile
    }

    /**
     * Generates a valid RTF document with .doc extension.
     */
    fun generateRtfDocFile(context: Context, note: Note): File {
        val cleanTitle = note.title.replace(Regex("[^a-zA-Zа-яА-ЯёЁ0-9_\\-]"), "_").trim('_').take(35).ifBlank { "document" }
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val docFile = File(exportDir, "${cleanTitle}.doc")

        val structure = parseStructure(note)
        val rtfContent = buildRtfContent(note, structure)
        docFile.writeBytes(rtfContent.toByteArray(Charsets.UTF_8))
        return docFile
    }

    fun generateDocxFromText(context: Context, title: String, content: String): File {
        return generateDocxFile(context, Note(title = title, content = content), includeSignature = false)
    }

    fun generateRtfFromText(context: Context, title: String, content: String): File {
        return generateRtfDocFile(context, Note(title = title, content = content))
    }

    private fun buildContentTypesXml(hasSignature: Boolean, hasImages: Boolean = false): String {
        val sigPart = if (hasSignature || hasImages) {
            "  <Default Extension=\"png\" ContentType=\"image/png\"/>\n  <Default Extension=\"jpeg\" ContentType=\"image/jpeg\"/>\n"
        } else ""
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
$sigPart  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
  <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
  <Override PartName="/word/settings.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.settings+xml"/>
  <Override PartName="/word/footer1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml"/>
</Types>"""
    }

    private fun buildWordRelsXml(hasSignature: Boolean, preparedImages: List<PreparedDocxImage> = emptyList()): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
        sb.append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">\n")
        sb.append("  <Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>\n")
        sb.append("  <Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/settings\" Target=\"settings.xml\"/>\n")
        sb.append("  <Relationship Id=\"rIdFtr1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/footer\" Target=\"footer1.xml\"/>\n")
        if (hasSignature) {
            sb.append("  <Relationship Id=\"rIdSig\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/signature.png\"/>\n")
        }
        for (img in preparedImages) {
            sb.append("  <Relationship Id=\"").append(img.relId).append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/").append(img.fileName).append("\"/>\n")
        }
        sb.append("</Relationships>")
        return sb.toString()
    }

    private fun renderDocxImageElement(sb: StringBuilder, img: PreparedDocxImage) {
        val docPrId = (img.relId.hashCode().and(0x7FFFFFFF) % 100000) + 100
        sb.append("<w:p>\n")
        sb.append("  <w:pPr>\n")
        sb.append("    <w:spacing w:before=\"200\" w:after=\"80\" w:line=\"240\" w:lineRule=\"auto\"/>\n")
        sb.append("    <w:jc w:val=\"center\"/>\n")
        sb.append("  </w:pPr>\n")
        sb.append("  <w:r>\n")
        sb.append("    <w:drawing>\n")
        sb.append("      <wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\">\n")
        sb.append("        <wp:extent cx=\"").append(img.widthEmu).append("\" cy=\"").append(img.heightEmu).append("\"/>\n")
        sb.append("        <wp:docPr id=\"").append(docPrId).append("\" name=\"").append(escapeXml(img.fileName)).append("\"/>\n")
        sb.append("        <a:graphic>\n")
        sb.append("          <a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">\n")
        sb.append("            <pic:pic>\n")
        sb.append("              <pic:nvPicPr><pic:cNvPr id=\"").append(docPrId).append("\" name=\"").append(escapeXml(img.fileName)).append("\"/><pic:cNvPicPr/></pic:nvPicPr>\n")
        sb.append("              <pic:blipFill><a:blip r:embed=\"").append(img.relId).append("\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>\n")
        sb.append("              <pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"").append(img.widthEmu).append("\" cy=\"").append(img.heightEmu).append("\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr>\n")
        sb.append("            </pic:pic>\n")
        sb.append("          </a:graphicData>\n")
        sb.append("        </a:graphic>\n")
        sb.append("      </wp:inline>\n")
        sb.append("    </w:drawing>\n")
        sb.append("  </w:r>\n")
        sb.append("</w:p>\n")

        sb.append("<w:p>\n")
        sb.append("  <w:pPr>\n")
        sb.append("    <w:spacing w:before=\"60\" w:after=\"160\" w:line=\"240\" w:lineRule=\"auto\"/>\n")
        sb.append("    <w:jc w:val=\"center\"/>\n")
        sb.append("  </w:pPr>\n")
        sb.append("  <w:r>\n")
        sb.append("    <w:rPr>\n")
        sb.append("      <w:rFonts w:ascii=\"Times New Roman\" w:hAnsi=\"Times New Roman\"/>\n")
        sb.append("      <w:sz w:val=\"24\"/>\n")
        sb.append("    </w:rPr>\n")
        sb.append("    <w:t xml:space=\"preserve\">").append(escapeXml(img.caption)).append("</w:t>\n")
        sb.append("  </w:r>\n")
        sb.append("</w:p>\n")
    }

    private fun buildDocumentXml(
        note: Note,
        structure: ParsedDocumentStructure,
        hasSignature: Boolean = false,
        preparedImages: List<PreparedDocxImage> = emptyList()
    ): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
        sb.append("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\" xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">\n")
        sb.append("<w:body>\n")

        // 1. ACADEMIC TITLE PAGE (Титульный лист по ГОСТ 7.32-2017)
        if (structure.titlePageInfo != null) {
            renderDocxTitlePage(sb, structure.titlePageInfo)
        } else if (structure.headerLines.isNotEmpty()) {
            // 2. CORPORATE REQUISITE HEADER BLOCK (ГОСТ Р 7.0.97-2016)
            renderDocxCorporateHeader(sb, structure.headerLines)
        }

        // 3. DOCUMENT TITLE (Centered bold uppercase for official documents)
        if (structure.titlePageInfo == null) {
            val titleText = structure.documentTitle ?: note.title
            if (titleText.isNotBlank()) {
                sb.append("<w:p>\n")
                sb.append("  <w:pPr>\n")
                sb.append("    <w:spacing w:before=\"400\" w:after=\"360\" w:line=\"360\" w:lineRule=\"auto\"/>\n")
                sb.append("    <w:jc w:val=\"center\"/>\n")
                sb.append("  </w:pPr>\n")
                sb.append("  <w:r>\n")
                sb.append("    <w:rPr>\n")
                sb.append("      <w:rFonts w:ascii=\"Times New Roman\" w:hAnsi=\"Times New Roman\"/>\n")
                sb.append("      <w:b/>\n")
                sb.append("      <w:sz w:val=\"28\"/>\n") // 14pt
                sb.append("    </w:rPr>\n")
                sb.append("    <w:t xml:space=\"preserve\">").append(escapeXml(titleText.uppercase())).append("</w:t>\n")
                sb.append("  </w:r>\n")
                sb.append("</w:p>\n")
            }
        }

        // 4. BODY ELEMENTS (Paragraphs, Tables, Headings, Page Breaks, Figures & Images)
        var lastWasPageBreak = (structure.titlePageInfo != null)
        val renderedImageRelIds = mutableSetOf<String>()
        var nextFigureIdx = 0

        for ((elemIdx, element) in structure.bodyElements.withIndex()) {
            when (element) {
                is BodyElement.PageBreak -> {
                    val hasMoreContent = structure.bodyElements.drop(elemIdx + 1)
                        .any { it !is BodyElement.PageBreak && (it !is BodyElement.Paragraph || it.text.isNotBlank()) }
                    if (!lastWasPageBreak && hasMoreContent) {
                        sb.append("<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>\n")
                        lastWasPageBreak = true
                    }
                }
                is BodyElement.Paragraph -> {
                    if (element.text.isBlank()) {
                        // Skip blank paragraphs to eliminate unwanted gaps between text, tables, and figures
                        continue
                    }
                    if (element.isPageBreakBefore && !lastWasPageBreak) {
                        sb.append("<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>\n")
                        lastWasPageBreak = true
                    }

                    sb.append("<w:p>\n")
                    sb.append("  <w:pPr>\n")

                    if (element.isHeading) {
                        val beforeSp = if (lastWasPageBreak) "120" else (if (element.headingLevel == 1) "360" else "240")
                        val afterSp = "160"
                        sb.append("    <w:spacing w:before=\"$beforeSp\" w:after=\"$afterSp\" w:line=\"360\" w:lineRule=\"auto\"/>\n")
                        sb.append("    <w:jc w:val=\"").append(if (element.isCentered) "center" else "both").append("\"/>\n")
                        if (!element.isCentered) {
                            sb.append("    <w:ind w:firstLine=\"709\"/>\n")
                        }
                    } else if (element.isCentered) {
                        sb.append("    <w:spacing w:line=\"360\" w:lineRule=\"auto\" w:after=\"120\"/>\n")
                        sb.append("    <w:jc w:val=\"center\"/>\n")
                    } else {
                        sb.append("    <w:ind w:firstLine=\"709\"/>\n") // Красная строка 1.25 см (ГОСТ)
                        sb.append("    <w:spacing w:line=\"360\" w:lineRule=\"auto\" w:after=\"120\"/>\n") // 1.5 интервал
                        sb.append("    <w:jc w:val=\"both\"/>\n") // Выравнивание по ширине (justify)
                    }

                    sb.append("  </w:pPr>\n")

                    val runsToRender = if (element.runs.isNotEmpty()) element.runs else listOf(FormattedRun(element.text, isBold = element.isHeading))
                    for (run in runsToRender) {
                        sb.append("  <w:r>\n")
                        sb.append("    <w:rPr>\n")
                        sb.append("      <w:rFonts w:ascii=\"Times New Roman\" w:hAnsi=\"Times New Roman\"/>\n")
                        if (run.isBold || element.isHeading) {
                            sb.append("      <w:b/>\n")
                        }
                        if (run.isItalic) {
                            sb.append("      <w:i/>\n")
                        }
                        val szVal = when {
                            element.isHeading && element.headingLevel == 1 -> "28" // 14pt bold
                            element.isHeading && element.headingLevel == 2 -> "28" // 14pt bold
                            element.isHeading -> "26" // 13pt
                            else -> "24" // 12pt standard
                        }
                        sb.append("      <w:sz w:val=\"$szVal\"/>\n")
                        sb.append("    </w:rPr>\n")
                        sb.append("    <w:t xml:space=\"preserve\">").append(escapeXml(run.text)).append("</w:t>\n")
                        sb.append("  </w:r>\n")
                    }

                    sb.append("</w:p>\n")
                    lastWasPageBreak = false
                }
                is BodyElement.Table -> {
                    lastWasPageBreak = false
                    renderDocxTable(sb, element)
                }
                is BodyElement.SchematicFigure -> {
                    lastWasPageBreak = false
                    val img = preparedImages.firstOrNull { it.relId !in renderedImageRelIds && (it.caption == element.caption || (element.figureNumber.isNotBlank() && it.caption.contains(element.figureNumber))) }
                        ?: preparedImages.firstOrNull { it.relId !in renderedImageRelIds }
                    if (img != null) {
                        renderDocxImageElement(sb, img)
                        renderedImageRelIds.add(img.relId)
                    }
                }
                is BodyElement.Image -> {
                    lastWasPageBreak = false
                    val img = preparedImages.firstOrNull { it.relId !in renderedImageRelIds && (it.relId == element.relId || it.caption == element.caption) }
                        ?: preparedImages.firstOrNull { it.relId !in renderedImageRelIds }
                    if (img != null) {
                        renderDocxImageElement(sb, img)
                        renderedImageRelIds.add(img.relId)
                    }
                }
            }
        }

        // Render any remaining attached images not explicitly matched
        for (img in preparedImages) {
            if (img.relId !in renderedImageRelIds) {
                renderDocxImageElement(sb, img)
                renderedImageRelIds.add(img.relId)
            }
        }

        // 5. SIGNATURE & DATE BLOCK (For official corporate documents)
        if (structure.isOfficialDocument && structure.titlePageInfo == null) {
            renderDocxSignatureBlock(sb, structure, hasSignature)
        }

        // 6. PAGE SETUP & NUMBERING (ГОСТ Margins & Page numbering on page 2+ except title page)
        sb.append("<w:sectPr>\n")
        sb.append("  <w:footerReference w:type=\"default\" r:id=\"rIdFtr1\"/>\n")
        sb.append("  <w:titlePg/>\n") // Suppress page number on page 1 (Title page)
        sb.append("  <w:pgSz w:w=\"11906\" w:h=\"16838\"/>\n") // A4 format
        sb.append("  <w:pgMar w:top=\"1134\" w:right=\"850\" w:bottom=\"1134\" w:left=\"1701\" w:header=\"708\" w:footer=\"708\" w:gutter=\"0\"/>\n")
        sb.append("</w:sectPr>\n")

        sb.append("</w:body>\n")
        sb.append("</w:document>")
        return sb.toString()
    }

    private fun renderDocxTitlePage(sb: StringBuilder, info: TitlePageInfo) {
        val orgLineCount = info.organizationLines.size
        val topicLength = info.topic?.length ?: 0
        val topicLineEstimate = (topicLength / 45) + 1
        val authorAndSupervisorLines = info.authorLines.size + info.supervisorLines.size
        val totalDenseUnits = orgLineCount + topicLineEstimate + authorAndSupervisorLines

        // Dynamic compact vertical spacing in DXA (1 pt = 20 dxa) to ensure 100% single page fit
        val beforeDocType = when {
            totalDenseUnits > 13 -> "260"
            totalDenseUnits > 10 -> "380"
            totalDenseUnits > 7 -> "540"
            else -> "720"
        }
        val beforeAuthor = when {
            totalDenseUnits > 13 -> "220"
            totalDenseUnits > 10 -> "320"
            totalDenseUnits > 7 -> "440"
            else -> "560"
        }
        val beforeCityYear = when {
            totalDenseUnits > 13 -> "360"
            totalDenseUnits > 10 -> "520"
            totalDenseUnits > 7 -> "700"
            else -> "900"
        }

        // University & Ministry Lines (Centered, 12pt, compact line spacing)
        for (line in info.organizationLines) {
            sb.append("<w:p>\n")
            sb.append("  <w:pPr><w:jc w:val=\"center\"/><w:spacing w:line=\"220\" w:lineRule=\"auto\" w:after=\"20\"/></w:pPr>\n")
            sb.append("  <w:r><w:rPr><w:rFonts w:ascii=\"Times New Roman\" w:hAnsi=\"Times New Roman\"/><w:sz w:val=\"24\"/></w:rPr><w:t xml:space=\"preserve\">").append(escapeXml(line)).append("</w:t></w:r>\n")
            sb.append("</w:p>\n")
        }

        // Dynamic vertical spacing before document type
        sb.append("<w:p><w:pPr><w:spacing w:before=\"$beforeDocType\" w:after=\"0\"/></w:pPr></w:p>\n")

        // Document Type: РЕФЕРАТ / КУРСОВАЯ РАБОТА (Centered, Bold 16pt)
        sb.append("<w:p>\n")
        sb.append("  <w:pPr><w:jc w:val=\"center\"/><w:spacing w:line=\"300\" w:lineRule=\"auto\" w:after=\"120\"/></w:pPr>\n")
        sb.append("  <w:r><w:rPr><w:rFonts w:ascii=\"Times New Roman\" w:hAnsi=\"Times New Roman\"/><w:b/><w:sz w:val=\"32\"/></w:rPr><w:t xml:space=\"preserve\">").append(escapeXml(info.documentType)).append("</w:t></w:r>\n")
        sb.append("</w:p>\n")

        // Discipline if present
        if (!info.discipline.isNullOrBlank()) {
            sb.append("<w:p>\n")
            sb.append("  <w:pPr><w:jc w:val=\"center\"/><w:spacing w:line=\"240\" w:lineRule=\"auto\" w:after=\"80\"/></w:pPr>\n")
            sb.append("  <w:r><w:rPr><w:rFonts w:ascii=\"Times New Roman\" w:hAnsi=\"Times New Roman\"/><w:sz w:val=\"24\"/></w:rPr><w:t xml:space=\"preserve\">").append(escapeXml(info.discipline)).append("</w:t></w:r>\n")
            sb.append("</w:p>\n")
        }

        // Topic (Centered, Bold 14pt)
        if (!info.topic.isNullOrBlank()) {
            sb.append("<w:p>\n")
            sb.append("  <w:pPr><w:jc w:val=\"center\"/><w:spacing w:line=\"280\" w:lineRule=\"auto\" w:after=\"160\"/></w:pPr>\n")
            sb.append("  <w:r><w:rPr><w:rFonts w:ascii=\"Times New Roman\" w:hAnsi=\"Times New Roman\"/><w:b/><w:sz w:val=\"28\"/></w:rPr><w:t xml:space=\"preserve\">").append(escapeXml(info.topic)).append("</w:t></w:r>\n")
            sb.append("</w:p>\n")
        }

        // Dynamic vertical spacing before Author & Supervisor block
        sb.append("<w:p><w:pPr><w:spacing w:before=\"$beforeAuthor\" w:after=\"0\"/></w:pPr></w:p>\n")

        // Author and Supervisor block in 2-column borderless table
        sb.append("<w:tbl>\n")
        sb.append("  <w:tblPr>\n")
        sb.append("    <w:tblW w:w=\"9355\" w:type=\"dxa\"/>\n")
        sb.append("    <w:tblBorders><w:top w:val=\"none\"/><w:left w:val=\"none\"/><w:bottom w:val=\"none\"/><w:right w:val=\"none\"/><w:insideH w:val=\"none\"/><w:insideV w:val=\"none\"/></w:tblBorders>\n")
        sb.append("    <w:tblLayout w:type=\"fixed\"/>\n")
        sb.append("  </w:tblPr>\n")
        sb.append("  <w:tblGrid><w:gridCol w:w=\"4400\"/><w:gridCol w:w=\"4955\"/></w:tblGrid>\n")
        sb.append("  <w:tr>\n")
        sb.append("    <w:tc><w:tcPr><w:tcW w:w=\"4400\" w:type=\"dxa\"/></w:tcPr><w:p><w:pPr><w:spacing w:line=\"240\" w:after=\"0\"/></w:pPr></w:p></w:tc>\n")
        sb.append("    <w:tc><w:tcPr><w:tcW w:w=\"4955\" w:type=\"dxa\"/></w:tcPr>\n")

        for (aLine in info.authorLines) {
            sb.append("      <w:p><w:pPr><w:spacing w:line=\"240\" w:after=\"30\"/><w:jc w:val=\"left\"/></w:pPr><w:r><w:rPr><w:rFonts w:ascii=\"Times New Roman\" w:hAnsi=\"Times New Roman\"/><w:sz w:val=\"24\"/></w:rPr><w:t xml:space=\"preserve\">").append(escapeXml(aLine)).append("</w:t></w:r></w:p>\n")
        }
        if (info.supervisorLines.isNotEmpty()) {
            sb.append("      <w:p><w:pPr><w:spacing w:before=\"80\" w:after=\"0\"/></w:pPr></w:p>\n")
            for (sLine in info.supervisorLines) {
                sb.append("      <w:p><w:pPr><w:spacing w:line=\"240\" w:after=\"30\"/><w:jc w:val=\"left\"/></w:pPr><w:r><w:rPr><w:rFonts w:ascii=\"Times New Roman\" w:hAnsi=\"Times New Roman\"/><w:sz w:val=\"24\"/></w:rPr><w:t xml:space=\"preserve\">").append(escapeXml(sLine)).append("</w:t></w:r></w:p>\n")
            }
        }

        sb.append("    </w:tc>\n")
        sb.append("  </w:tr>\n")
        sb.append("</w:tbl>\n")

        // Dynamic spacing before Bottom City and Year (Centered at page bottom)
        sb.append("<w:p><w:pPr><w:spacing w:before=\"$beforeCityYear\" w:after=\"0\"/><w:jc w:val=\"center\"/></w:pPr>\n")
        sb.append("  <w:r><w:rPr><w:rFonts w:ascii=\"Times New Roman\" w:hAnsi=\"Times New Roman\"/><w:sz w:val=\"24\"/></w:rPr><w:t xml:space=\"preserve\">").append(escapeXml(info.cityAndYear ?: "Москва, 2026")).append("</w:t></w:r>\n")
        sb.append("</w:p>\n")

        // Explicit Page Break after Title Page (ГОСТ 7.32)
        sb.append("<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>\n")
    }

    private fun renderDocxCorporateHeader(sb: StringBuilder, headerLines: List<String>) {
        sb.append("<w:tbl>\n")
        sb.append("  <w:tblPr>\n")
        sb.append("    <w:tblW w:w=\"9355\" w:type=\"dxa\"/>\n")
        sb.append("    <w:tblBorders>\n")
        sb.append("      <w:top w:val=\"none\"/><w:left w:val=\"none\"/><w:bottom w:val=\"none\"/><w:right w:val=\"none\"/><w:insideH w:val=\"none\"/><w:insideV w:val=\"none\"/>\n")
        sb.append("    </w:tblBorders>\n")
        sb.append("    <w:tblLayout w:type=\"fixed\"/>\n")
        sb.append("  </w:tblPr>\n")
        sb.append("  <w:tblGrid><w:gridCol w:w=\"4677\"/><w:gridCol w:w=\"4678\"/></w:tblGrid>\n")
        sb.append("  <w:tr>\n")
        sb.append("    <w:tc>\n")
        sb.append("      <w:tcPr><w:tcW w:w=\"4677\" w:type=\"dxa\"/></w:tcPr>\n")
        sb.append("      <w:p><w:pPr><w:spacing w:line=\"240\" w:lineRule=\"auto\" w:after=\"0\"/></w:pPr></w:p>\n")
        sb.append("    </w:tc>\n")
        sb.append("    <w:tc>\n")
        sb.append("      <w:tcPr><w:tcW w:w=\"4678\" w:type=\"dxa\"/></w:tcPr>\n")
        for (hLine in headerLines) {
            sb.append("      <w:p>\n")
            sb.append("        <w:pPr>\n")
            sb.append("          <w:spacing w:line=\"260\" w:lineRule=\"auto\" w:after=\"40\"/>\n")
            sb.append("          <w:jc w:val=\"left\"/>\n")
            sb.append("        </w:pPr>\n")
            sb.append("        <w:r>\n")
            sb.append("          <w:rPr>\n")
            sb.append("            <w:rFonts w:ascii=\"Times New Roman\" w:hAnsi=\"Times New Roman\"/>\n")
            sb.append("            <w:sz w:val=\"24\"/>\n")
            sb.append("          </w:rPr>\n")
            sb.append("          <w:t xml:space=\"preserve\">").append(escapeXml(hLine)).append("</w:t>\n")
            sb.append("        </w:r>\n")
            sb.append("      </w:p>\n")
        }
        sb.append("    </w:tc>\n")
        sb.append("  </w:tr>\n")
        sb.append("</w:tbl>\n")
    }

    private fun renderDocxTable(sb: StringBuilder, table: BodyElement.Table) {
        val totalCols = maxOf(1, maxOf(table.headers.size, table.rows.maxOfOrNull { it.size } ?: 1))
        val totalWidthDxa = 9355
        val colWidthDxa = totalWidthDxa / totalCols

        sb.append("<w:tbl>\n")
        sb.append("  <w:tblPr>\n")
        sb.append("    <w:tblW w:w=\"$totalWidthDxa\" w:type=\"dxa\"/>\n")
        sb.append("    <w:tblBorders>\n")
        sb.append("      <w:top w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"000000\"/>\n")
        sb.append("      <w:left w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"000000\"/>\n")
        sb.append("      <w:bottom w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"000000\"/>\n")
        sb.append("      <w:right w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"000000\"/>\n")
        sb.append("      <w:insideH w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"000000\"/>\n")
        sb.append("      <w:insideV w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"000000\"/>\n")
        sb.append("    </w:tblBorders>\n")
        sb.append("    <w:tblLayout w:type=\"fixed\"/>\n")
        sb.append("  </w:tblPr>\n")

        // Grid columns definition
        sb.append("  <w:tblGrid>\n")
        for (i in 0 until totalCols) {
            sb.append("    <w:gridCol w:w=\"$colWidthDxa\"/>\n")
        }
        sb.append("  </w:tblGrid>\n")

        // Header row
        if (table.headers.isNotEmpty()) {
            sb.append("  <w:tr>\n")
            for (i in 0 until totalCols) {
                val header = if (i < table.headers.size) table.headers[i] else ""
                sb.append("    <w:tc>\n")
                sb.append("      <w:tcPr><w:tcW w:w=\"$colWidthDxa\" w:type=\"dxa\"/><w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"F2F2F2\"/></w:tcPr>\n")
                sb.append("      <w:p><w:pPr><w:jc w:val=\"center\"/><w:spacing w:line=\"240\" w:lineRule=\"auto\" w:after=\"40\"/></w:pPr><w:r><w:rPr><w:rFonts w:ascii=\"Times New Roman\" w:hAnsi=\"Times New Roman\"/><w:b/><w:sz w:val=\"22\"/></w:rPr><w:t xml:space=\"preserve\">").append(escapeXml(header)).append("</w:t></w:r></w:p>\n")
                sb.append("    </w:tc>\n")
            }
            sb.append("  </w:tr>\n")
        }

        // Data rows
        for (row in table.rows) {
            sb.append("  <w:tr>\n")
            for (i in 0 until totalCols) {
                val cell = if (i < row.size) row[i] else ""
                sb.append("    <w:tc>\n")
                sb.append("      <w:tcPr><w:tcW w:w=\"$colWidthDxa\" w:type=\"dxa\"/></w:tcPr>\n")
                sb.append("      <w:p><w:pPr><w:spacing w:line=\"240\" w:lineRule=\"auto\" w:after=\"40\"/></w:pPr><w:r><w:rPr><w:rFonts w:ascii=\"Times New Roman\" w:hAnsi=\"Times New Roman\"/><w:sz w:val=\"22\"/></w:rPr><w:t xml:space=\"preserve\">").append(escapeXml(cell)).append("</w:t></w:r></w:p>\n")
                sb.append("    </w:tc>\n")
            }
            sb.append("  </w:tr>\n")
        }
        sb.append("</w:tbl>\n")
        sb.append("<w:p><w:pPr><w:spacing w:line=\"240\" w:lineRule=\"auto\" w:after=\"120\"/></w:pPr></w:p>\n")
    }

    private fun renderDocxSignatureBlock(sb: StringBuilder, structure: ParsedDocumentStructure, hasSignature: Boolean) {
        sb.append("<w:tbl>\n")
        sb.append("  <w:tblPr>\n")
        sb.append("    <w:tblW w:w=\"9355\" w:type=\"dxa\"/>\n")
        sb.append("    <w:tblBorders>\n")
        sb.append("      <w:top w:val=\"none\"/><w:left w:val=\"none\"/><w:bottom w:val=\"none\"/><w:right w:val=\"none\"/><w:insideH w:val=\"none\"/><w:insideV w:val=\"none\"/>\n")
        sb.append("    </w:tblBorders>\n")
        sb.append("    <w:tblLayout w:type=\"fixed\"/>\n")
        sb.append("  </w:tblPr>\n")
        sb.append("  <w:tblGrid><w:gridCol w:w=\"4677\"/><w:gridCol w:w=\"4678\"/></w:tblGrid>\n")
        sb.append("  <w:tr>\n")

        val dateText = structure.footerLines.firstOrNull { it.startsWith("Дата", ignoreCase = true) || it.startsWith("«___»") }
            ?: "Дата: «___» __________ 202_ г."
        val sigText = structure.footerLines.firstOrNull { it.contains("Подпись", ignoreCase = true) || it.contains("____________ /") }
            ?: "Подпись: ____________ / ____________ /"

        // Left cell: Date
        sb.append("    <w:tc>\n")
        sb.append("      <w:tcPr><w:tcW w:w=\"4677\" w:type=\"dxa\"/></w:tcPr>\n")
        sb.append("      <w:p>\n")
        sb.append("        <w:pPr><w:spacing w:before=\"480\" w:line=\"360\" w:lineRule=\"auto\"/><w:jc w:val=\"left\"/></w:pPr>\n")
        sb.append("        <w:r><w:rPr><w:rFonts w:ascii=\"Times New Roman\" w:hAnsi=\"Times New Roman\"/><w:sz w:val=\"24\"/></w:rPr><w:t xml:space=\"preserve\">").append(escapeXml(dateText)).append("</w:t></w:r>\n")
        sb.append("      </w:p>\n")
        sb.append("    </w:tc>\n")

        // Right cell: Signature
        sb.append("    <w:tc>\n")
        sb.append("      <w:tcPr><w:tcW w:w=\"4678\" w:type=\"dxa\"/></w:tcPr>\n")

        if (hasSignature) {
            sb.append("      <w:p>\n")
            sb.append("        <w:pPr><w:jc w:val=\"right\"/><w:spacing w:before=\"200\" w:after=\"0\"/></w:pPr>\n")
            sb.append("        <w:r>\n")
            sb.append("          <w:drawing>\n")
            sb.append("            <wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\">\n")
            sb.append("              <wp:extent cx=\"1463040\" cy=\"548640\"/>\n")
            sb.append("              <wp:docPr id=\"1001\" name=\"DigitalSignature\"/>\n")
            sb.append("              <a:graphic>\n")
            sb.append("                <a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">\n")
            sb.append("                  <pic:pic>\n")
            sb.append("                    <pic:nvPicPr><pic:cNvPr id=\"0\" name=\"signature.png\"/><pic:cNvPicPr/></pic:nvPicPr>\n")
            sb.append("                    <pic:blipFill><a:blip r:embed=\"rIdSig\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>\n")
            sb.append("                    <pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"1463040\" cy=\"548640\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr>\n")
            sb.append("                  </pic:pic>\n")
            sb.append("                </a:graphicData>\n")
            sb.append("              </a:graphic>\n")
            sb.append("            </wp:inline>\n")
            sb.append("          </w:drawing>\n")
            sb.append("        </w:r>\n")
            sb.append("      </w:p>\n")
        }

        sb.append("      <w:p>\n")
        sb.append("        <w:pPr><w:spacing w:before=\"").append(if (hasSignature) "60" else "480").append("\" w:line=\"360\" w:lineRule=\"auto\"/><w:jc w:val=\"right\"/></w:pPr>\n")
        sb.append("        <w:r><w:rPr><w:rFonts w:ascii=\"Times New Roman\" w:hAnsi=\"Times New Roman\"/><w:sz w:val=\"24\"/></w:rPr><w:t xml:space=\"preserve\">").append(escapeXml(sigText)).append("</w:t></w:r>\n")
        sb.append("      </w:p>\n")
        sb.append("    </w:tc>\n")
        sb.append("  </w:tr>\n")
        sb.append("</w:tbl>\n")
    }

    private fun buildRtfContent(note: Note, structure: ParsedDocumentStructure): String {
        val sb = StringBuilder()
        sb.append("{\\rtf1\\ansi\\ansicpg1251\\deff0\\deflang1049\n")
        sb.append("{\\fonttbl{\\f0\\froman\\fcharset204 Times New Roman;}{\\f1\\fswiss\\fcharset204 Arial;}}\n")
        sb.append("{\\colortbl ;\\red0\\green0\\blue0;\\red100\\green100\\blue100;}\n")
        sb.append("\\paperw11906\\paperh16838\\margl1701\\margr850\\margt1134\\margb1134\n")
        sb.append("\\widowctrl\\ftnbj\\aenddoc\\formshade\\viewkind1\\viewscale100\\pgbrdrhead\\pgbrdrfoot\n")
        sb.append("\\titlepg\n") // First page has no header/footer
        sb.append("{\\footer \\qc\\f0\\fs22 \\chpgn \\par}\n") // Centered page numbering starting on page 2
        sb.append("\\f0\\fs24\\sl360\\slmult1\n") // Times New Roman 12pt, 1.5 line spacing

        // 1. Title Page in RTF
        if (structure.titlePageInfo != null) {
            val info = structure.titlePageInfo
            for (line in info.organizationLines) {
                sb.append("\\qc\\fs24 ").append(escapeRtf(line)).append("\\par\n")
            }
            sb.append("\\par\\par\\par\\par\\par\n")
            sb.append("\\qc\\b\\fs36 ").append(escapeRtf(info.documentType)).append("\\b0\\fs24\\par\\par\n")
            if (!info.discipline.isNullOrBlank()) {
                sb.append("\\qc\\fs28 ").append(escapeRtf(info.discipline)).append("\\par\n")
            }
            if (!info.topic.isNullOrBlank()) {
                sb.append("\\qc\\b\\fs30 ").append(escapeRtf(info.topic)).append("\\b0\\fs24\\par\\par\n")
            }
            sb.append("\\par\\par\\par\n")
            sb.append("\\li4677\n")
            for (aLine in info.authorLines) {
                sb.append(escapeRtf(aLine)).append("\\par\n")
            }
            for (sLine in info.supervisorLines) {
                sb.append(escapeRtf(sLine)).append("\\par\n")
            }
            sb.append("\\li0\\par\\par\\par\\par\n")
            sb.append("\\qc\\fs24 ").append(escapeRtf(info.cityAndYear ?: "Москва, 2026")).append("\\par\n")
            sb.append("\\page\n") // End of title page
            sb.append("\\ql\n")
        } else if (structure.headerLines.isNotEmpty()) {
            // Right-aligned corporate header
            sb.append("\\li4677\\sl240\\slmult1\n")
            for (hLine in structure.headerLines) {
                sb.append(escapeRtf(hLine)).append("\\par\n")
            }
            sb.append("\\li0\\sl360\\slmult1\\par\n")
        }

        // 2. Centered title for official documents
        if (structure.titlePageInfo == null) {
            val titleText = structure.documentTitle ?: note.title
            if (titleText.isNotBlank()) {
                sb.append("\\qc\\b\\fs28 ").append(escapeRtf(titleText.uppercase())).append("\\b0\\fs24\\par\\par\n")
                sb.append("\\ql\n")
            }
        }

        // 3. Body paragraphs
        var lastWasPageBreak = (structure.titlePageInfo != null)
        for ((elemIdx, element) in structure.bodyElements.withIndex()) {
            when (element) {
                is BodyElement.PageBreak -> {
                    val hasMoreContent = structure.bodyElements.drop(elemIdx + 1)
                        .any { it !is BodyElement.PageBreak && (it !is BodyElement.Paragraph || it.text.isNotBlank()) }
                    if (!lastWasPageBreak && hasMoreContent) {
                        sb.append("\\page\n")
                        lastWasPageBreak = true
                    }
                }
                is BodyElement.Paragraph -> {
                    if (element.text.isBlank()) continue
                    if (element.isPageBreakBefore && !lastWasPageBreak) {
                        sb.append("\\page\n")
                        lastWasPageBreak = true
                    }

                    if (element.isHeading) {
                        val alignCmd = if (element.isCentered) "\\qc" else "\\qj\\fi709"
                        sb.append(alignCmd).append("\\b\\fs28 ")
                        for (run in element.runs) {
                            if (run.isItalic) sb.append("\\i ")
                            sb.append(escapeRtf(run.text))
                            if (run.isItalic) sb.append("\\i0 ")
                        }
                        sb.append("\\b0\\fs24\\par\n")
                    } else if (element.isCentered) {
                        sb.append("\\qc ")
                        renderRtfRuns(sb, element.runs)
                        sb.append("\\par\n")
                    } else {
                        sb.append("\\qj\\fi709 ")
                        renderRtfRuns(sb, element.runs)
                        sb.append("\\par\n")
                    }
                    lastWasPageBreak = false
                }
                is BodyElement.Table -> {
                    lastWasPageBreak = false
                    for (row in listOf(element.headers) + element.rows) {
                        sb.append("\\trowd\\trgaph108\\trleft0\n")
                        var currentWidth = 0
                        val colWidth = 9355 / maxOf(1, row.size)
                        for (i in row.indices) {
                            currentWidth += colWidth
                            sb.append("\\clbrdrt\\brdrs\\brdrw10\\clbrdrl\\brdrs\\brdrw10\\clbrdrb\\brdrs\\brdrw10\\clbrdrr\\brdrs\\brdrw10\\cellx").append(currentWidth).append("\n")
                        }
                        for (cell in row) {
                            sb.append("\\pard\\intbl\\fs20 ").append(escapeRtf(cell)).append("\\cell\n")
                        }
                        sb.append("\\row\n")
                    }
                    sb.append("\\pard\\par\n")
                }
                is BodyElement.SchematicFigure -> {
                    lastWasPageBreak = false
                    sb.append("\\pard\\qc\\b [СХЕМА / ГРАФИЧЕСКИЙ МАТЕРИАЛ]\\b0\\par\n")
                    sb.append("\\pard\\qc ").append(escapeRtf(element.caption)).append("\\par\n")
                    if (element.description.isNotBlank()) {
                        sb.append("\\pard\\qc\\i ").append(escapeRtf(element.description)).append("\\i0\\par\n")
                    }
                    sb.append("\\par\n")
                }
                is BodyElement.Image -> {
                    lastWasPageBreak = false
                    sb.append("\\pard\\qc\\b [ИЛЛЮСТРАЦИЯ]\\b0\\par\n")
                    sb.append("\\pard\\qc ").append(escapeRtf(element.caption)).append("\\par\\par\n")
                }
            }
        }

        // 4. Date & Signature for corporate documents
        if (structure.isOfficialDocument && structure.titlePageInfo == null) {
            val dateText = structure.footerLines.firstOrNull { it.startsWith("Дата", ignoreCase = true) || it.startsWith("«___»") }
                ?: "Дата: «___» __________ 202_ г."
            val sigText = structure.footerLines.firstOrNull { it.contains("Подпись", ignoreCase = true) || it.contains("____________ /") }
                ?: "Подпись: ____________ / ____________ /"

            sb.append("\\par\\par\n")
            sb.append("\\trowd\\trgaph108\\trleft0\n")
            sb.append("\\cellx4677\\cellx9355\n")
            sb.append("\\pard\\intbl\\ql ").append(escapeRtf(dateText)).append("\\cell\n")
            sb.append("\\pard\\intbl\\qr ").append(escapeRtf(sigText)).append("\\cell\n")
            sb.append("\\row\\pard\n")
        }

        sb.append("}")
        return sb.toString()
    }

    private fun renderRtfRuns(sb: StringBuilder, runs: List<FormattedRun>) {
        for (run in runs) {
            if (run.isBold) sb.append("\\b ")
            if (run.isItalic) sb.append("\\i ")
            sb.append(escapeRtf(run.text))
            if (run.isItalic) sb.append("\\i0 ")
            if (run.isBold) sb.append("\\b0 ")
        }
    }

    private fun escapeRtf(text: String): String {
        val sb = StringBuilder()
        for (ch in text) {
            val code = ch.code
            if (code > 127) {
                val rtfCode = if (code > 32767) code - 65536 else code
                sb.append("\\u").append(rtfCode).append("?")
            } else when (ch) {
                '\\' -> sb.append("\\\\")
                '{' -> sb.append("\\{")
                '}' -> sb.append("\\}")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    // --- XML Templates for OpenXML package ---

    private const val ROOT_RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

    private const val SETTINGS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:settings xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:defaultTabStop w:val="708"/>
</w:settings>"""

    private const val STYLES_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:docDefaults>
    <w:rPrDefault>
      <w:rPr>
        <w:rFonts w:ascii="Times New Roman" w:hAnsi="Times New Roman" w:cs="Times New Roman"/>
        <w:sz w:val="24"/>
        <w:szCs w:val="24"/>
        <w:lang w:val="ru-RU"/>
      </w:rPr>
    </w:rPrDefault>
    <w:pPrDefault>
      <w:pPr>
        <w:spacing w:line="360" w:lineRule="auto" w:after="120"/>
        <w:jc w:val="both"/>
      </w:pPr>
    </w:pPrDefault>
  </w:docDefaults>
</w:styles>"""

    private const val FOOTER_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:ftr xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:p>
    <w:pPr>
      <w:jc w:val="center"/>
    </w:pPr>
    <w:r>
      <w:rPr>
        <w:rFonts w:ascii="Times New Roman" w:hAnsi="Times New Roman"/>
        <w:sz w:val="22"/>
      </w:rPr>
      <w:fldSimple w:instr="PAGE"/>
    </w:r>
  </w:p>
</w:ftr>"""
}
