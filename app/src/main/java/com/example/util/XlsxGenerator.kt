package com.example.util

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Universal Office Open XML (.xlsx) and Spreadsheet Generator.
 * 
 * Creates valid, native .xlsx workbooks with formatted tables, bold headers,
 * borders and proper cell types that open smoothly in Microsoft Excel,
 * Google Sheets, WPS Office, and LibreOffice Calc.
 */
object XlsxGenerator {

    data class TableData(
        val sheetName: String = "Лист 1",
        val title: String? = null,
        val headers: List<String>,
        val rows: List<List<String>>
    )

    /**
     * Extracts tables from markdown text (lines starting/containing '|').
     */
    fun extractTablesFromMarkdown(markdown: String): List<TableData> {
        val lines = markdown.lines()
        val tables = mutableListOf<TableData>()
        val currentTableLines = mutableListOf<String>()
        var precedingTitle: String? = null

        fun flushCurrentTable() {
            if (currentTableLines.isNotEmpty()) {
                val parsedRows = mutableListOf<List<String>>()
                for (line in currentTableLines) {
                    val trimmed = line.trim()
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
                    val dataRows = if (parsedRows.size > 1) parsedRows.subList(1, parsedRows.size) else emptyList()
                    tables.add(TableData(
                        sheetName = "Таблица ${tables.size + 1}",
                        title = precedingTitle,
                        headers = headers,
                        rows = dataRows
                    ))
                }
                currentTableLines.clear()
                precedingTitle = null
            }
        }

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
                currentTableLines.add(trimmed)
            } else {
                if (currentTableLines.isNotEmpty()) {
                    flushCurrentTable()
                }
                if (trimmed.startsWith("#") || (trimmed.isNotEmpty() && trimmed.length < 80 && !trimmed.contains("."))) {
                    precedingTitle = trimmed.removePrefix("#").trim()
                }
            }
        }
        flushCurrentTable()
        return tables
    }

    /**
     * Generates a native .xlsx file in the cache directory and returns the File.
     */
    fun generateXlsxFile(
        context: Context,
        fileName: String,
        headers: List<String>,
        rows: List<List<String>>,
        tableTitle: String? = null
    ): File {
        val cacheDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val sanitized = fileName.replace(Regex("[^a-zA-Z0-9а-яА-ЯёЁ_\\-]"), "_").take(40)
        val file = File(cacheDir, "${sanitized}_${System.currentTimeMillis()}.xlsx")

        val sheetXml = buildSheetXml(headers, rows, tableTitle)

        FileOutputStream(file).use { fos ->
            ZipOutputStream(fos).use { zos ->
                // [Content_Types].xml
                zos.putNextEntry(ZipEntry("[Content_Types].xml"))
                zos.write(CONTENT_TYPES_XML.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                // _rels/.rels
                zos.putNextEntry(ZipEntry("_rels/.rels"))
                zos.write(ROOT_RELS_XML.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                // xl/_rels/workbook.xml.rels
                zos.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
                zos.write(WORKBOOK_RELS_XML.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                // xl/workbook.xml
                zos.putNextEntry(ZipEntry("xl/workbook.xml"))
                zos.write(WORKBOOK_XML.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                // xl/styles.xml
                zos.putNextEntry(ZipEntry("xl/styles.xml"))
                zos.write(STYLES_XML.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                // xl/worksheets/sheet1.xml
                zos.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
                zos.write(sheetXml.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }

        return file
    }

    private fun buildSheetXml(
        headers: List<String>,
        rows: List<List<String>>,
        tableTitle: String?
    ): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sb.append("<sheetData>")

        var rowIndex = 1

        // Title row if present
        if (!tableTitle.isNullOrBlank()) {
            sb.append("""<row r="$rowIndex" ht="26" customHeight="1">""")
            sb.append("""<c r="A$rowIndex" t="inlineStr" s="1">""")
            sb.append("<is><t>").append(escapeXml(tableTitle)).append("</t></is>")
            sb.append("</c>")
            sb.append("</row>")
            rowIndex++
        }

        // Headers row
        sb.append("""<row r="$rowIndex" ht="22" customHeight="1">""")
        headers.forEachIndexed { colIdx, header ->
            val colLetter = getColumnLetter(colIdx + 1)
            val cellRef = "$colLetter$rowIndex"
            sb.append("""<c r="$cellRef" t="inlineStr" s="2">""")
            sb.append("<is><t>").append(escapeXml(header)).append("</t></is>")
            sb.append("</c>")
        }
        sb.append("</row>")
        rowIndex++

        // Data rows
        for (row in rows) {
            sb.append("""<row r="$rowIndex">""")
            for (colIdx in 0 until maxOf(headers.size, row.size)) {
                val value = row.getOrNull(colIdx) ?: ""
                val colLetter = getColumnLetter(colIdx + 1)
                val cellRef = "$colLetter$rowIndex"

                // Check if numeric
                val doubleVal = value.replace(" ", "").replace(",", ".").toDoubleOrNull()
                if (doubleVal != null && !value.contains(":") && !value.contains("-") && value.length < 15) {
                    sb.append("""<c r="$cellRef" s="3">""")
                    sb.append("<v>").append(doubleVal.toString()).append("</v>")
                    sb.append("</c>")
                } else {
                    sb.append("""<c r="$cellRef" t="inlineStr" s="4">""")
                    sb.append("<is><t>").append(escapeXml(value)).append("</t></is>")
                    sb.append("</c>")
                }
            }
            sb.append("</row>")
            rowIndex++
        }

        sb.append("</sheetData>")
        sb.append("</worksheet>")
        return sb.toString()
    }

    private fun getColumnLetter(columnNumber: Int): String {
        var temp = columnNumber
        val sb = StringBuilder()
        while (temp > 0) {
            val rem = (temp - 1) % 26
            sb.append((65 + rem).toChar())
            temp = (temp - 1) / 26
        }
        return sb.reverse().toString()
    }

    private fun escapeXml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private const val CONTENT_TYPES_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>"""

    private const val ROOT_RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private const val WORKBOOK_RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

    private const val WORKBOOK_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="Лист 1" sheetId="1" r:id="rId1"/>
  </sheets>
</workbook>"""

    private const val STYLES_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="3">
    <font><name val="Calibri"/><sz val="11"/></font>
    <font><b/><name val="Calibri"/><sz val="14"/><color rgb="FF0D47A1"/></font>
    <font><b/><name val="Calibri"/><sz val="11"/><color rgb="FFFFFFFF"/></font>
  </fonts>
  <fills count="3">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF1976D2"/><bgColor indexed="64"/></patternFill></fill>
  </fills>
  <borders count="2">
    <border><left/><right/><top/><bottom/></border>
    <border>
      <left style="thin"><color rgb="FFB0BEC5"/></left>
      <right style="thin"><color rgb="FFB0BEC5"/></right>
      <top style="thin"><color rgb="FFB0BEC5"/></top>
      <bottom style="thin"><color rgb="FFB0BEC5"/></bottom>
    </border>
  </borders>
  <cellStyleXfs count="1">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
  </cellStyleXfs>
  <cellXfs count="5">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>
    <xf numFmtId="0" fontId="2" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
    <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1"><alignment horizontal="right" vertical="center"/></xf>
    <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1"><alignment horizontal="left" vertical="center"/></xf>
  </cellXfs>
</styleSheet>"""
}
