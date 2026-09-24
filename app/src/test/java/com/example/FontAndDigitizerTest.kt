package com.example

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FontAndDigitizerTest {

    @Test
    fun testFontFilesExistAndHaveData() {
        val caveat = File("src/main/res/font/caveat.ttf")
        val marck = File("src/main/res/font/marck_script.ttf")
        val playfair = File("src/main/res/font/playfair_display.ttf")
        val robotoMono = File("src/main/res/font/roboto_mono.ttf")

        assertTrue("Caveat font should exist", caveat.exists())
        assertTrue("Caveat font should have size > 50KB for Cyrillic support", caveat.length() > 50_000)

        assertTrue("Marck Script font should exist", marck.exists())
        assertTrue("Marck Script font should have size > 30KB for Cyrillic support", marck.length() > 30_000)

        assertTrue("Playfair font should exist", playfair.exists())
        assertTrue("Roboto Mono font should exist", robotoMono.exists())
    }
}
