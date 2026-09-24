package com.example

import com.example.presentation.components.SessionPhase
import com.example.util.AmbientSound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyFocusTimerTest {

    @Test
    fun testAmbientSoundCatalog() {
        val sounds = AmbientSound.values()
        assertTrue("Should have multiple ambient sounds", sounds.size >= 4)
        assertNotNull(AmbientSound.valueOf("RAIN"))
        assertNotNull(AmbientSound.valueOf("WHITE_NOISE"))
        assertNotNull(AmbientSound.valueOf("ALPHA_WAVES"))
        assertNotNull(AmbientSound.valueOf("CLOCK_TICK"))
        assertNotNull(AmbientSound.valueOf("NONE"))
    }

    @Test
    fun testSessionPhases() {
        assertEquals(25, SessionPhase.FOCUS_STANDARD.defaultMinutes)
        assertEquals(50, SessionPhase.FOCUS_DEEP.defaultMinutes)
        assertEquals(5, SessionPhase.SHORT_BREAK.defaultMinutes)
        assertEquals(15, SessionPhase.LONG_BREAK.defaultMinutes)
    }

    @Test
    fun testTimerProgression() {
        val totalSeconds = SessionPhase.FOCUS_STANDARD.defaultMinutes * 60
        assertEquals(1500, totalSeconds)

        val remaining = 1500 - 300 // 5 minutes elapsed
        val progress = (totalSeconds - remaining).toFloat() / totalSeconds.toFloat()
        assertEquals(0.2f, progress, 0.001f)
    }
}
