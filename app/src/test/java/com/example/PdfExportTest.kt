package com.example

import com.example.util.ShareExportUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfExportTest {

    @Test
    fun testParseImageUris_validJson() {
        val json = """["file:///data/img1.png", "file:///data/img2.jpg"]"""
        val uris = ShareExportUtil.parseImageUris(json)
        assertEquals(2, uris.size)
        assertEquals("file:///data/img1.png", uris[0])
        assertEquals("file:///data/img2.jpg", uris[1])
    }

    @Test
    fun testParseImageUris_empty() {
        val uris = ShareExportUtil.parseImageUris("")
        assertTrue(uris.isEmpty())
    }

    @Test
    fun testParseImageUris_commaSeparatedFallback() {
        val commaSeparated = """["/data/drawing1.png", "/data/drawing2.png"]"""
        val uris = ShareExportUtil.parseImageUris(commaSeparated)
        assertEquals(2, uris.size)
        assertEquals("/data/drawing1.png", uris[0])
    }
}
