package com.example

import com.example.util.GlyphCategory
import com.example.util.HandwritingGlyphManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HandwritingGlyphManagerTest {

    @Test
    fun testAllTargetCharactersListCompleteness() {
        val targets = HandwritingGlyphManager.getAllTargetCharacters()
        // 33 lowercase + 33 uppercase + 10 digits + 10 punctuation = 86
        assertEquals(86, targets.size)

        val lowercase = targets.filter { it.second == GlyphCategory.LOWERCASE }
        assertEquals(33, lowercase.size)
        assertTrue(lowercase.any { it.first == 'а' })
        assertTrue(lowercase.any { it.first == 'я' })
        assertTrue(lowercase.any { it.first == 'ё' })

        val uppercase = targets.filter { it.second == GlyphCategory.UPPERCASE }
        assertEquals(33, uppercase.size)
        assertTrue(uppercase.any { it.first == 'А' })
        assertTrue(uppercase.any { it.first == 'Я' })
        assertTrue(uppercase.any { it.first == 'Ё' })

        val digits = targets.filter { it.second == GlyphCategory.DIGIT }
        assertEquals(10, digits.size)

        val punctuations = targets.filter { it.second == GlyphCategory.PUNCTUATION }
        assertEquals(10, punctuations.size)
        assertTrue(punctuations.any { it.first == '!' })
        assertTrue(punctuations.any { it.first == '?' })
        assertTrue(punctuations.any { it.first == '.' })
    }
}
