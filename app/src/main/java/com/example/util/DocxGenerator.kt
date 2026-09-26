package com.example.util

import android.content.Context
import com.example.domain.model.Note
import org.json.JSONArray
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
 * Complies strictly with Russian office paperwork standard ГОСТ Р 7.0.97-2016:
 * - Recipient & applicant block ("Кому:", "От кого:") positioned on the RIGHT side of the page
 * - Centered bold uppercase document title (e.g. "ЗАЯВЛЕНИЕ", "СЛУЖЕБНАЯ ЗАПИСКА")
 * - Justified text alignment with standard 1.25 cm (709 dxa) first-line indents
 * - 1.5 line spacing, Times New Roman 12pt
 * - Standard ГОСТ margins: Left 30 mm, Right 15 mm, Top 20 mm, Bottom 20 mm
 * - Date on the left, signature on the right
 * - Native, clean ZIP-based OpenXML package that opens natively in MS Word, Word Mobile, Google Docs, WPS, LibreOffice
 *   WITHOUT triggering "damaged file" security errors.
 */
object DocxGenerator {

    private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale("ru"))

    data class ParsedDocumentStructure(
        val isOfficialDocument: Boolean,
        val headerLines: List<String>,
        val documentTitle: String?,
        val bodyElements: List<BodyElement>,
        val footerLines: List<String>
    )

    sealed class BodyElement {
        data class Paragraph(val text: String, val isHeading: Boolean = false, val headingLevel: Int = 1) : BodyElement()
        data class Table(val headers: List<String>, val rows: List<List<String>>) : BodyElement()
    }

    private val OFFICIAL_TITLES = setOf(
        "ЗАЯВЛЕНИЕ", "СЛУЖЕБНАЯ ЗАПИСКА", "ДОКЛАДНАЯ ЗАПИСКА",
        "ОБЪЯСНИТЕЛЬНАЯ ЗАПИСКА", "АКТ", "АКТ ПРИЁМА-ПЕРЕДАЧИ", "АКТ ПРИЕМА-ПЕРЕДАЧИ",
        "ПРОТОКОЛ", "ПРЕТЕНЗИЯ", "УВЕДОМЛЕНИЕ", "ПРИКАЗ", "РАСПОРЯЖЕНИЕ",
        "ДОВЕРЕННОСТЬ", "ДОГОВОР", "СОГЛАШЕНИЕ", "ОТЧЁТ", "ОТЧЕТ"
    )

    /**
     * Parses the note content to extract document requisites:
     * - Right header block ("Кому...", "От кого...")
     * - Document title
     * - Body paragraphs and markdown tables
     * - Bottom date & signature lines
     */
    fun parseStructure(note: Note): ParsedDocumentStructure {
        val lines = note.content.lines()
        val headerLines = mutableListOf<String>()
        var foundTitle: String? = null
        val bodyElements = mutableListOf<BodyElement>()
        val footerLines = mutableListOf<String>()

        var phase = 0 // 0 = looking for header/title, 1 = body, 2 = footer
        val currentTableLines = mutableListOf<String>()

        fun flushTable() {
            if (currentTableLines.isNotEmpty()) {
                val parsedRows = mutableListOf<List<String>>()
                for (tLine in currentTableLines) {
                    val trimmed = tLine.trim()
                    if (trimmed.replace(Regex("[-| :]+"), "").isBlank()) continue
                    val cells = trimmed.split("|")
                        .map { it.trim() }
                        .filterIndexed { idx, _ -> idx > 0 && idx < trimmed.split("|").lastIndex }
                    if (cells.isNotEmpty()) {
                        parsedRows.add(cells)
                    }
                }
                if (parsedRows.isNotEmpty()) {
                    val headers = parsedRows.first()
                    val rows = if (parsedRows.size > 1) parsedRows.subList(1, parsedRows.size) else emptyList()
                    bodyElements.add(BodyElement.Table(headers, rows))
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

        for (rawLine in lines) {
            val trimmed = rawLine.trim()
            val upperTrimmed = trimmed.uppercase().removePrefix("#").trim()

            if (phase == 0) {
                if (isTitleLine(trimmed)) {
                    foundTitle = upperTrimmed
                    phase = 1
                } else if (headerLines.isEmpty()) {
                    if (isHeaderStart(trimmed)) {
                        headerLines.add(trimmed)
                    } else if (trimmed.isBlank()) {
                        // ignore leading blank lines
                    } else {
                        // Not a header line, so move to body phase
                        phase = 1
                        processBodyLine(rawLine, currentTableLines, bodyElements)
                    }
                } else {
                    // Header collection already started
                    if (trimmed.isBlank()) {
                        // Empty line inside header block - keep going
                    } else {
                        // Any line before the title belongs to the requisites header block
                        headerLines.add(trimmed)
                    }
                }
            } else if (phase == 1) {
                if (isFooterLine(trimmed)) {
                    flushTable()
                    footerLines.add(trimmed)
                    phase = 2
                } else {
                    processBodyLine(rawLine, currentTableLines, bodyElements)
                }
            } else {
                // phase == 2 (footer)
                if (trimmed.isNotBlank()) {
                    footerLines.add(trimmed)
                }
            }
        }

        flushTable()

        // Fallback for title if not found in body text
        val finalTitle = foundTitle ?: if (OFFICIAL_TITLES.any { note.title.uppercase().contains(it) }) {
            note.title.uppercase()
        } else if (headerLines.isNotEmpty()) {
            "ЗАЯВЛЕНИЕ"
        } else {
            note.title.ifBlank { "ДОКУМЕНТ" }
        }

        val isOfficial = headerLines.isNotEmpty() || foundTitle != null ||
                note.title.contains("Заявление", ignoreCase = true) ||
                note.title.contains("Служебная", ignoreCase = true) ||
                note.title.contains("Акт", ignoreCase = true) ||
                note.title.contains("Протокол", ignoreCase = true)

        return ParsedDocumentStructure(
            isOfficialDocument = isOfficial,
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

    private fun processBodyLine(
        rawLine: String,
        tableLines: MutableList<String>,
        bodyElements: MutableList<BodyElement>
    ) {
        val trimmed = rawLine.trim()
        if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
            tableLines.add(trimmed)
        } else {
            if (tableLines.isNotEmpty()) {
                // flush existing table
                val parsedRows = mutableListOf<List<String>>()
                for (tLine in tableLines) {
                    val tTrimmed = tLine.trim()
                    if (tTrimmed.replace(Regex("[-| :]+"), "").isBlank()) continue
                    val cells = tTrimmed.split("|")
                        .map { it.trim() }
                        .filterIndexed { idx, _ -> idx > 0 && idx < tTrimmed.split("|").lastIndex }
                    if (cells.isNotEmpty()) {
                        parsedRows.add(cells)
                    }
                }
                if (parsedRows.isNotEmpty()) {
                    val headers = parsedRows.first()
                    val rows = if (parsedRows.size > 1) parsedRows.subList(1, parsedRows.size) else emptyList()
                    bodyElements.add(BodyElement.Table(headers, rows))
                }
                tableLines.clear()
            }

            if (trimmed.isNotBlank()) {
                if (trimmed.startsWith("# ")) {
                    bodyElements.add(BodyElement.Paragraph(trimmed.removePrefix("# ").trim(), isHeading = true, headingLevel = 1))
                } else if (trimmed.startsWith("## ")) {
                    bodyElements.add(BodyElement.Paragraph(trimmed.removePrefix("## ").trim(), isHeading = true, headingLevel = 2))
                } else if (trimmed.startsWith("### ")) {
                    bodyElements.add(BodyElement.Paragraph(trimmed.removePrefix("### ").trim(), isHeading = true, headingLevel = 3))
                } else {
                    bodyElements.add(BodyElement.Paragraph(trimmed))
                }
            }
        }
    }

    private fun escapeXml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    /**
     * Generates a genuine, standard Office Open XML (.docx) package as a ZIP file.
     */
    fun generateDocxFile(context: Context, note: Note): File {
        val cleanTitle = note.title.replace(Regex("[^a-zA-Zа-яА-Я0-9_]"), "_").take(30).ifBlank { "document" }
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val docxFile = File(exportDir, "${cleanTitle}.docx")

        val structure = parseStructure(note)
        val documentXml = buildDocumentXml(note, structure)

        ZipOutputStream(FileOutputStream(docxFile)).use { zos ->
            // 1. [Content_Types].xml
            zos.putNextEntry(ZipEntry("[Content_Types].xml"))
            zos.write(CONTENT_TYPES_XML.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 2. _rels/.rels
            zos.putNextEntry(ZipEntry("_rels/.rels"))
            zos.write(ROOT_RELS_XML.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 3. word/_rels/document.xml.rels
            zos.putNextEntry(ZipEntry("word/_rels/document.xml.rels"))
            zos.write(WORD_RELS_XML.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 4. word/settings.xml
            zos.putNextEntry(ZipEntry("word/settings.xml"))
            zos.write(SETTINGS_XML.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 5. word/styles.xml
            zos.putNextEntry(ZipEntry("word/styles.xml"))
            zos.write(STYLES_XML.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 6. word/document.xml
            zos.putNextEntry(ZipEntry("word/document.xml"))
            zos.write(documentXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        return docxFile
    }

    /**
     * Generates a valid RTF document with .doc extension.
     * Guaranteed to open in Microsoft Word, Word Mobile, LibreOffice, WordPad without corruption errors.
     */
    fun generateRtfDocFile(context: Context, note: Note): File {
        val cleanTitle = note.title.replace(Regex("[^a-zA-Zа-яА-Я0-9_]"), "_").take(30).ifBlank { "document" }
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val docFile = File(exportDir, "${cleanTitle}.doc")

        val structure = parseStructure(note)
        val rtfContent = buildRtfContent(note, structure)
        docFile.writeBytes(rtfContent.toByteArray(Charsets.UTF_8))
        return docFile
    }

    private fun buildDocumentXml(note: Note, structure: ParsedDocumentStructure): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
        sb.append("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">\n")
        sb.append("<w:body>\n")

        // 1. RIGHT-ALIGNED HEADER (ГОСТ Р 7.0.97-2016)
        // Two-column borderless table: left column 50% empty, right column 50% with recipient/applicant requisites
        if (structure.headerLines.isNotEmpty()) {
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
            // Left cell (empty)
            sb.append("    <w:tc>\n")
            sb.append("      <w:tcPr><w:tcW w:w=\"4677\" w:type=\"dxa\"/></w:tcPr>\n")
            sb.append("      <w:p><w:pPr><w:spacing w:line=\"240\" w:lineRule=\"auto\" w:after=\"0\"/></w:pPr></w:p>\n")
            sb.append("    </w:tc>\n")
            // Right cell (contains header lines)
            sb.append("    <w:tc>\n")
            sb.append("      <w:tcPr><w:tcW w:w=\"4678\" w:type=\"dxa\"/></w:tcPr>\n")
            for (hLine in structure.headerLines) {
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

        // 2. DOCUMENT TITLE (Centered, Uppercase, Bold 14pt)
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

        // 3. BODY ELEMENTS (Paragraphs & Tables)
        for (element in structure.bodyElements) {
            when (element) {
                is BodyElement.Paragraph -> {
                    if (element.isHeading) {
                        sb.append("<w:p>\n")
                        sb.append("  <w:pPr>\n")
                        sb.append("    <w:spacing w:before=\"240\" w:after=\"120\" w:line=\"360\" w:lineRule=\"auto\"/>\n")
                        sb.append("    <w:jc w:val=\"left\"/>\n")
                        sb.append("  </w:pPr>\n")
                        sb.append("  <w:r>\n")
                        sb.append("    <w:rPr>\n")
                        sb.append("      <w:rFonts w:ascii=\"Times New Roman\" w:hAnsi=\"Times New Roman\"/>\n")
                        sb.append("      <w:b/>\n")
                        sb.append("      <w:sz w:val=\"26\"/>\n")
                        sb.append("    </w:rPr>\n")
                        sb.append("    <w:t xml:space=\"preserve\">").append(escapeXml(element.text)).append("</w:t>\n")
                        sb.append("  </w:r>\n")
                        sb.append("</w:p>\n")
                    } else {
                        sb.append("<w:p>\n")
                        sb.append("  <w:pPr>\n")
                        sb.append("    <w:ind w:firstLine=\"709\"/>\n") // Красная строка 1.25 см
                        sb.append("    <w:spacing w:line=\"360\" w:lineRule=\"auto\" w:after=\"120\"/>\n") // 1.5 интервал
                        sb.append("    <w:jc w:val=\"both\"/>\n") // Выравнивание по ширине (justify)
                        sb.append("  </w:pPr>\n")
                        sb.append("  <w:r>\n")
                        sb.append("    <w:rPr>\n")
                        sb.append("      <w:rFonts w:ascii=\"Times New Roman\" w:hAnsi=\"Times New Roman\"/>\n")
                        sb.append("      <w:sz w:val=\"24\"/>\n") // 12pt
                        sb.append("    </w:rPr>\n")
                        sb.append("    <w:t xml:space=\"preserve\">").append(escapeXml(element.text)).append("</w:t>\n")
                        sb.append("  </w:r>\n")
                        sb.append("</w:p>\n")
                    }
                }
                is BodyElement.Table -> {
                    sb.append("<w:tbl>\n")
                    sb.append("  <w:tblPr>\n")
                    sb.append("    <w:tblW w:w=\"9355\" w:type=\"dxa\"/>\n")
                    sb.append("    <w:tblBorders>\n")
                    sb.append("      <w:top w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"000000\"/>\n")
                    sb.append("      <w:left w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"000000\"/>\n")
                    sb.append("      <w:bottom w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"000000\"/>\n")
                    sb.append("      <w:right w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"000000\"/>\n")
                    sb.append("      <w:insideH w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"000000\"/>\n")
                    sb.append("      <w:insideV w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"000000\"/>\n")
                    sb.append("    </w:tblBorders>\n")
                    sb.append("  </w:tblPr>\n")

                    // Header row
                    sb.append("  <w:tr>\n")
                    for (header in element.headers) {
                        sb.append("    <w:tc>\n")
                        sb.append("      <w:tcPr><w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"F2F2F2\"/></w:tcPr>\n")
                        sb.append("      <w:p><w:pPr><w:jc w:val=\"center\"/></w:pPr><w:r><w:rPr><w:b/><w:sz w:val=\"22\"/></w:rPr><w:t>").append(escapeXml(header)).append("</w:t></w:r></w:p>\n")
                        sb.append("    </w:tc>\n")
                    }
                    sb.append("  </w:tr>\n")

                    // Data rows
                    for (row in element.rows) {
                        sb.append("  <w:tr>\n")
                        for (cell in row) {
                            sb.append("    <w:tc>\n")
                            sb.append("      <w:p><w:r><w:rPr><w:sz w:val=\"22\"/></w:rPr><w:t>").append(escapeXml(cell)).append("</w:t></w:r></w:p>\n")
                            sb.append("    </w:tc>\n")
                        }
                        sb.append("  </w:tr>\n")
                    }
                    sb.append("</w:tbl>\n")
                }
            }
        }

        // 4. CHECKLIST ITEMS (if any)
        val checklistItems = parseChecklistItems(note.checkListJson)
        if (checklistItems.isNotEmpty()) {
            sb.append("<w:p>\n")
            sb.append("  <w:pPr><w:spacing w:before=\"240\" w:after=\"120\"/><w:jc w:val=\"left\"/></w:pPr>\n")
            sb.append("  <w:r><w:rPr><w:b/><w:sz w:val=\"24\"/></w:rPr><w:t>Список задач и поручений:</w:t></w:r>\n")
            sb.append("</w:p>\n")

            sb.append("<w:tbl>\n")
            sb.append("  <w:tblPr><w:tblW w:w=\"9355\" w:type=\"dxa\"/>\n")
            sb.append("    <w:tblBorders>\n")
            sb.append("      <w:top w:val=\"single\" w:sz=\"4\" w:color=\"CCCCCC\"/>\n")
            sb.append("      <w:left w:val=\"single\" w:sz=\"4\" w:color=\"CCCCCC\"/>\n")
            sb.append("      <w:bottom w:val=\"single\" w:sz=\"4\" w:color=\"CCCCCC\"/>\n")
            sb.append("      <w:right w:val=\"single\" w:sz=\"4\" w:color=\"CCCCCC\"/>\n")
            sb.append("      <w:insideH w:val=\"single\" w:sz=\"4\" w:color=\"CCCCCC\"/>\n")
            sb.append("      <w:insideV w:val=\"single\" w:sz=\"4\" w:color=\"CCCCCC\"/>\n")
            sb.append("    </w:tblBorders>\n")
            sb.append("  </w:tblPr>\n")
            sb.append("  <w:tr>\n")
            sb.append("    <w:tc><w:tcPr><w:tcW w:w=\"800\" w:type=\"dxa\"/><w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"F2F2F2\"/></w:tcPr><w:p><w:pPr><w:jc w:val=\"center\"/></w:pPr><w:r><w:rPr><w:b/><w:sz w:val=\"20\"/></w:rPr><w:t>№</w:t></w:r></w:p></w:tc>\n")
            sb.append("    <w:tc><w:tcPr><w:tcW w:w=\"1800\" w:type=\"dxa\"/><w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"F2F2F2\"/></w:tcPr><w:p><w:pPr><w:jc w:val=\"center\"/></w:pPr><w:r><w:rPr><w:b/><w:sz w:val=\"20\"/></w:rPr><w:t>Статус</w:t></w:r></w:p></w:tc>\n")
            sb.append("    <w:tc><w:tcPr><w:tcW w:w=\"6755\" w:type=\"dxa\"/><w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"F2F2F2\"/></w:tcPr><w:p><w:r><w:rPr><w:b/><w:sz w:val=\"20\"/></w:rPr><w:t>Задача / Пункт</w:t></w:r></w:p></w:tc>\n")
            sb.append("  </w:tr>\n")

            checklistItems.forEachIndexed { idx, item ->
                val statusText = if (item.second) "Выполнено" else "К исполнению"
                sb.append("  <w:tr>\n")
                sb.append("    <w:tc><w:tcPr><w:tcW w:w=\"800\" w:type=\"dxa\"/></w:tcPr><w:p><w:pPr><w:jc w:val=\"center\"/></w:pPr><w:r><w:rPr><w:sz w:val=\"20\"/></w:rPr><w:t>").append(idx + 1).append("</w:t></w:r></w:p></w:tc>\n")
                sb.append("    <w:tc><w:tcPr><w:tcW w:w=\"1800\" w:type=\"dxa\"/></w:tcPr><w:p><w:pPr><w:jc w:val=\"center\"/></w:pPr><w:r><w:rPr><w:b/><w:sz w:val=\"20\"/><w:color w:val=\"").append(if (item.second) "15803D" else "B45309").append("\"/></w:rPr><w:t>").append(statusText).append("</w:t></w:r></w:p></w:tc>\n")
                sb.append("    <w:tc><w:tcPr><w:tcW w:w=\"6755\" w:type=\"dxa\"/></w:tcPr><w:p><w:r><w:rPr><w:sz w:val=\"20\"/></w:rPr><w:t>").append(escapeXml(item.first)).append("</w:t></w:r></w:p></w:tc>\n")
                sb.append("  </w:tr>\n")
            }
            sb.append("</w:tbl>\n")
        }

        // 5. DATE & SIGNATURES (ГОСТ Р 7.0.97-2016)
        // Two-column table: Left Date, Right Signature
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
        sb.append("      <w:p>\n")
        sb.append("        <w:pPr><w:spacing w:before=\"480\" w:line=\"360\" w:lineRule=\"auto\"/><w:jc w:val=\"right\"/></w:pPr>\n")
        sb.append("        <w:r><w:rPr><w:rFonts w:ascii=\"Times New Roman\" w:hAnsi=\"Times New Roman\"/><w:sz w:val=\"24\"/></w:rPr><w:t xml:space=\"preserve\">").append(escapeXml(sigText)).append("</w:t></w:r>\n")
        sb.append("      </w:p>\n")
        sb.append("    </w:tc>\n")
        sb.append("  </w:tr>\n")
        sb.append("</w:tbl>\n")

        // Page setup: A4 with ГОСТ Margins (left 30mm = 1701 dxa, right 15mm = 850 dxa, top 20mm = 1134 dxa, bottom 20mm = 1134 dxa)
        sb.append("<w:sectPr>\n")
        sb.append("  <w:pgSz w:w=\"11906\" w:h=\"16838\"/>\n")
        sb.append("  <w:pgMar w:top=\"1134\" w:right=\"850\" w:bottom=\"1134\" w:left=\"1701\" w:header=\"708\" w:footer=\"708\" w:gutter=\"0\"/>\n")
        sb.append("</w:sectPr>\n")

        sb.append("</w:body>\n")
        sb.append("</w:document>")
        return sb.toString()
    }

    private fun buildRtfContent(note: Note, structure: ParsedDocumentStructure): String {
        val sb = StringBuilder()
        sb.append("{\\rtf1\\ansi\\ansicpg1251\\deff0\\deflang1049\n")
        sb.append("{\\fonttbl{\\f0\\froman\\fcharset204 Times New Roman;}{\\f1\\fswiss\\fcharset204 Arial;}}\n")
        sb.append("{\\colortbl ;\\red0\\green0\\blue0;\\red100\\green100\\blue100;}\n")
        sb.append("\\paperw11906\\paperh16838\\margl1701\\margr850\\margt1134\\margb1134\n")
        sb.append("\\widowctrl\\ftnbj\\aenddoc\\formshade\\viewkind1\\viewscale100\\pgbrdrhead\\pgbrdrfoot\n")
        sb.append("\\f0\\fs24\\sl360\\slmult1\n") // Times New Roman 12pt, 1.5 line spacing

        // 1. Right header in RTF
        if (structure.headerLines.isNotEmpty()) {
            sb.append("\\li4677\\sl240\\slmult1\n") // indent 8.25 cm from left margin
            for (hLine in structure.headerLines) {
                sb.append(escapeRtf(hLine)).append("\\par\n")
            }
            sb.append("\\li0\\sl360\\slmult1\\par\n")
        }

        // 2. Centered title
        val titleText = structure.documentTitle ?: note.title
        if (titleText.isNotBlank()) {
            sb.append("\\qc\\b\\fs28 ").append(escapeRtf(titleText.uppercase())).append("\\b0\\fs24\\par\\par\n")
            sb.append("\\ql\n")
        }

        // 3. Body paragraphs
        for (element in structure.bodyElements) {
            when (element) {
                is BodyElement.Paragraph -> {
                    if (element.isHeading) {
                        sb.append("\\b\\fs26 ").append(escapeRtf(element.text)).append("\\b0\\fs24\\par\n")
                    } else {
                        sb.append("\\qj\\fi709 ").append(escapeRtf(element.text)).append("\\par\n")
                    }
                }
                is BodyElement.Table -> {
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
            }
        }

        // 4. Date & Signature
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

        sb.append("}")
        return sb.toString()
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

    private fun parseChecklistItems(json: String): List<Pair<String, Boolean>> {
        if (json.isBlank()) return emptyList()
        return try {
            val list = mutableListOf<Pair<String, Boolean>>()
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val text = obj.optString("text", "")
                val isChecked = obj.optBoolean("isChecked", false)
                if (text.isNotBlank()) {
                    list.add(text to isChecked)
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    // --- XML Templates for OpenXML package ---

    private const val CONTENT_TYPES_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
  <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
  <Override PartName="/word/settings.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.settings+xml"/>
</Types>"""

    private const val ROOT_RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

    private const val WORD_RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/settings" Target="settings.xml"/>
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
}
