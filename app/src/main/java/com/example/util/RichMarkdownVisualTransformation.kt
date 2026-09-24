package com.example.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

class RichMarkdownVisualTransformation(
    private val baseTextColor: Color
) : VisualTransformation {

    private val markupRegex = Regex(
        "(\\*\\*(.+?)\\*\\*)|" +                                 // 1: bold ** (group 1, inner: group 2)
        "(?<!\\*)(\\*([^*\\n]+?)\\*)(?!\\*)|" +                  // 2: italic * (group 3, inner: group 4)
        "(<u>(.+?)</u>)|" +                                      // 3: <u> (group 5, inner: group 6)
        "(__([^_\\n]+?)__)|" +                                   // 4: __ (group 7, inner: group 8)
        "(~~(.+?)~~)|" +                                         // 5: ~~ (group 9, inner: group 10)
        "(==(.+?)==)|" +                                         // 6: == (group 11, inner: group 12)
        "(\\[color=(#[0-9a-fA-F]{6})\\](.*?)\\[/color\\])"       // 7: color (group 13, hex: group 14, inner: group 15)
    )

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (raw.isEmpty()) {
            return TransformedText(text, OffsetMapping.Identity)
        }

        val origToTrans = IntArray(raw.length + 1)
        val transToOrigList = ArrayList<Int>(raw.length + 1)
        val builder = AnnotatedString.Builder()

        var rawIndex = 0

        for (match in markupRegex.findAll(raw)) {
            val matchStart = match.range.first
            val matchEnd = match.range.last + 1

            // 1. Process plain text before this match
            while (rawIndex < matchStart) {
                origToTrans[rawIndex] = builder.length
                transToOrigList.add(rawIndex)
                builder.append(raw[rawIndex])
                rawIndex++
            }

            // 2. Determine tag lengths and SpanStyle
            var openTagLen = 0
            var closeTagLen = 0
            var styleToApply: SpanStyle? = null

            val matchVal = match.value
            when {
                matchVal.startsWith("**") && matchVal.endsWith("**") && matchVal.length >= 4 -> {
                    openTagLen = 2
                    closeTagLen = 2
                    styleToApply = SpanStyle(fontWeight = FontWeight.Bold)
                }
                matchVal.startsWith("*") && matchVal.endsWith("*") && matchVal.length >= 2 -> {
                    openTagLen = 1
                    closeTagLen = 1
                    styleToApply = SpanStyle(fontStyle = FontStyle.Italic)
                }
                matchVal.startsWith("<u>") && matchVal.endsWith("</u>") && matchVal.length >= 7 -> {
                    openTagLen = 3
                    closeTagLen = 4
                    styleToApply = SpanStyle(textDecoration = TextDecoration.Underline)
                }
                matchVal.startsWith("__") && matchVal.endsWith("__") && matchVal.length >= 4 -> {
                    openTagLen = 2
                    closeTagLen = 2
                    styleToApply = SpanStyle(textDecoration = TextDecoration.Underline)
                }
                matchVal.startsWith("~~") && matchVal.endsWith("~~") && matchVal.length >= 4 -> {
                    openTagLen = 2
                    closeTagLen = 2
                    styleToApply = SpanStyle(textDecoration = TextDecoration.LineThrough)
                }
                matchVal.startsWith("==") && matchVal.endsWith("==") && matchVal.length >= 4 -> {
                    openTagLen = 2
                    closeTagLen = 2
                    styleToApply = SpanStyle(background = Color(0xFFFEF08A), color = Color(0xFF1E293B))
                }
                matchVal.startsWith("[color=") -> {
                    val hex = match.groups[14]?.value ?: "#000000"
                    openTagLen = 8 + hex.length
                    closeTagLen = 8
                    val parsedColor = try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { baseTextColor }
                    styleToApply = SpanStyle(color = parsedColor)
                }
            }

            val innerStartInRaw = matchStart + openTagLen
            val innerEndInRaw = matchEnd - closeTagLen

            // Map open tag characters (hidden from display)
            while (rawIndex < innerStartInRaw) {
                origToTrans[rawIndex] = builder.length
                rawIndex++
            }

            // Append inner text and map characters
            val styleStart = builder.length
            while (rawIndex < innerEndInRaw) {
                origToTrans[rawIndex] = builder.length
                transToOrigList.add(rawIndex)
                builder.append(raw[rawIndex])
                rawIndex++
            }
            val styleEnd = builder.length

            if (styleToApply != null && styleEnd > styleStart) {
                builder.addStyle(styleToApply, styleStart, styleEnd)
            }

            // Map close tag characters (hidden from display)
            while (rawIndex < matchEnd) {
                origToTrans[rawIndex] = builder.length
                rawIndex++
            }
        }

        // 3. Process remaining plain text
        while (rawIndex < raw.length) {
            origToTrans[rawIndex] = builder.length
            transToOrigList.add(rawIndex)
            builder.append(raw[rawIndex])
            rawIndex++
        }

        // End of string mapping
        origToTrans[raw.length] = builder.length
        transToOrigList.add(raw.length)
        val transToOrig = transToOrigList.toIntArray()

        val transformedAnnotated = builder.toAnnotatedString()

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val safe = offset.coerceIn(0, raw.length)
                return origToTrans[safe].coerceIn(0, transformedAnnotated.length)
            }

            override fun transformedToOriginal(offset: Int): Int {
                val safe = offset.coerceIn(0, transToOrig.size - 1)
                return transToOrig[safe].coerceIn(0, raw.length)
            }
        }

        return TransformedText(transformedAnnotated, offsetMapping)
    }
}
