package com.example.util

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.data.preferences.UserPreferencesManager
import java.util.Locale

/**
 * Manager for smooth, uninterrupted continuous speech-to-text transcription.
 * - Suppresses system audio chimes/beeps during listening sessions.
 * - Uses generous silence timeouts to prevent premature cutoffs.
 * - Selects best matching candidate and applies user word replacement dictionary.
 * - Supports custom recognition languages and online high accuracy vs offline modes.
 * - Resiliently recovers from audio hardware and recognizer timeouts without infinite error loops.
 */
class LectureTranscriptionManager(private val context: Context) {

    var isRecording by mutableStateOf(false)
        private set

    var isPaused by mutableStateOf(false)
        private set

    var isListening by mutableStateOf(false)
        private set

    var durationSeconds by mutableLongStateOf(0L)
        private set

    var partialHypothesis by mutableStateOf("")
        private set

    var currentRmsDb by mutableFloatStateOf(0f)
        private set

    private val preferencesManager = UserPreferencesManager(context)
    private var speechRecognizer: SpeechRecognizer? = null
    private var onTextAppendedCallback: ((String) -> Unit)? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var consecutiveErrors = 0
    private var lastPartialRaw = ""
    private var lastCommittedRaw = ""

    // Sound muting state to silence the mic start/stop "beep/ding" audio signal
    private var wasMuted = false

    private val timerRunnable = object : Runnable {
        override fun run() {
            if (isRecording && !isPaused) {
                durationSeconds++
            }
            if (isRecording) {
                mainHandler.postDelayed(this, 1000)
            }
        }
    }

    private val restartRunnable = Runnable {
        if (isRecording && !isPaused) {
            safeStartListening()
        }
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            isListening = true
            consecutiveErrors = 0
        }

        override fun onBeginningOfSpeech() {
            isListening = true
        }

        override fun onRmsChanged(rmsdB: Float) {
            currentRmsDb = rmsdB
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            isListening = false
        }

        override fun onError(error: Int) {
            isListening = false

            // CRITICAL FIX: Do NOT discard words spoken prior to pause or timeout!
            // If the recognizer timed out while user was speaking, commit the partial hypothesis.
            val pendingHypothesis = lastPartialRaw.trim()
            if (pendingHypothesis.isNotBlank() && pendingHypothesis != lastCommittedRaw) {
                val formatted = formatRecognizedChunk(pendingHypothesis)
                if (formatted.isNotBlank()) {
                    onTextAppendedCallback?.invoke(formatted)
                    lastCommittedRaw = pendingHypothesis
                }
            }
            lastPartialRaw = ""
            partialHypothesis = ""

            if (!isRecording || isPaused) return

            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                stopRecording()
                return
            }

            // Normal silence pauses (ERROR_NO_MATCH = 7, ERROR_SPEECH_TIMEOUT = 6)
            val isNormalSilence = error == SpeechRecognizer.ERROR_NO_MATCH ||
                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT

            if (isNormalSilence) {
                // Speaker simply paused or took a breath during the lecture.
                // Do NOT destroy or recreate the recognizer; immediately resume listening with minimal 30ms gap!
                cleanCancel()
                mainHandler.removeCallbacks(restartRunnable)
                mainHandler.postDelayed(restartRunnable, 30L)
            } else if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_CLIENT) {
                // Recover swiftly from transient recognizer busy state
                recreateRecognizerAndRestart(delayMs = 120L)
            } else {
                consecutiveErrors++
                val delay = if (consecutiveErrors > 3) 500L else 180L
                recreateRecognizerAndRestart(delayMs = delay)
            }
        }

        override fun onResults(results: Bundle?) {
            isListening = false
            partialHypothesis = ""
            consecutiveErrors = 0

            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val wordReplacements = preferencesManager.getWordReplacementsSync()
            val bestCandidate = SpeechPostProcessor.selectBestCandidate(matches, wordReplacements)?.trim()
            if (!bestCandidate.isNullOrBlank()) {
                val formatted = formatRecognizedChunk(bestCandidate)
                if (formatted.isNotBlank()) {
                    onTextAppendedCallback?.invoke(formatted)
                    lastCommittedRaw = bestCandidate
                }
            }
            lastPartialRaw = ""

            if (isRecording && !isPaused) {
                cleanCancel()
                mainHandler.removeCallbacks(restartRunnable)
                // Minimal 30ms gap so the next sentence uttered by the lecturer is never missed!
                mainHandler.postDelayed(restartRunnable, 30L)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partialMatches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val partial = partialMatches?.firstOrNull()?.trim() ?: ""
            if (partial.isNotBlank()) {
                lastPartialRaw = partial
                val enableSmartPunct = preferencesManager.isSmartPunctuationSync()
                val wordReplacements = preferencesManager.getWordReplacementsSync()
                partialHypothesis = SpeechPostProcessor.process(partial, enableSmartPunct, wordReplacements)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun startRecording(onTextAppended: (String) -> Unit): Boolean {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            return false
        }

        onTextAppendedCallback = onTextAppended
        durationSeconds = 0L
        partialHypothesis = ""
        isPaused = false
        isRecording = true
        consecutiveErrors = 0

        muteSystemBeeps(true)

        mainHandler.removeCallbacks(timerRunnable)
        mainHandler.postDelayed(timerRunnable, 1000)

        initAndListen()
        return true
    }

    fun togglePause() {
        if (!isRecording) return
        isPaused = !isPaused
        if (isPaused) {
            partialHypothesis = ""
            isListening = false
            cleanCancel()
            muteSystemBeeps(false)
        } else {
            muteSystemBeeps(true)
            safeStartListening()
        }
    }

    fun stopRecording() {
        isRecording = false
        isPaused = false
        isListening = false
        partialHypothesis = ""
        consecutiveErrors = 0

        mainHandler.removeCallbacks(timerRunnable)
        mainHandler.removeCallbacks(restartRunnable)

        muteSystemBeeps(false)

        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
    }

    private fun cleanCancel() {
        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) {}
    }

    private fun createRecognizer(): SpeechRecognizer {
        val accuracyMode = preferencesManager.getSpeechAccuracySync()
        if (accuracyMode == "prefer_offline" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                if (SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                    return SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                }
            } catch (_: Throwable) {}
        }
        return SpeechRecognizer.createSpeechRecognizer(context)
    }

    private fun initAndListen() {
        try {
            cleanCancel()
            speechRecognizer?.destroy()
            speechRecognizer = createRecognizer()
            speechRecognizer?.setRecognitionListener(recognitionListener)
            safeStartListening()
        } catch (_: Exception) {
            isRecording = false
            muteSystemBeeps(false)
        }
    }

    private fun recreateRecognizerAndRestart(delayMs: Long) {
        mainHandler.removeCallbacks(restartRunnable)
        mainHandler.postDelayed({
            if (isRecording && !isPaused) {
                try {
                    speechRecognizer?.cancel()
                    speechRecognizer?.destroy()
                } catch (_: Exception) {}
                speechRecognizer = null

                try {
                    speechRecognizer = createRecognizer()
                    speechRecognizer?.setRecognitionListener(recognitionListener)
                    safeStartListening()
                } catch (_: Exception) {
                    if (isRecording && !isPaused) {
                        mainHandler.postDelayed({ recreateRecognizerAndRestart(500) }, 1000)
                    }
                }
            }
        }, delayMs)
    }

    private fun safeStartListening() {
        if (!isRecording || isPaused) return
        try {
            val selectedLang = preferencesManager.getSpeechLanguageSync()
            val accuracyMode = preferencesManager.getSpeechAccuracySync()
            val langTag = if (selectedLang.isBlank() || selectedLang == "auto") {
                Locale.getDefault().toLanguageTag()
            } else {
                selectedLang
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langTag)
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)

                if (accuracyMode == "prefer_offline") {
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                }

                // Generous timeouts to capture continuous speech without interrupting the speaker:
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 900000L) // 15 minutes continuous session
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 7000L) // 7 sec complete silence before closing chunk
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L) // 5 sec pause

                // Enable modern speech formatting (numbers, punctuation, capitalization) and keep lecture vocabulary unmasked
                putExtra("android.speech.extra.ENABLE_FORMATTING", true)
                putExtra("android.speech.extra.MASK_OFFENSIVE_WORDS", false)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS)
                }
            }
            speechRecognizer?.startListening(intent)
        } catch (_: Exception) {
            recreateRecognizerAndRestart(delayMs = 300)
        }
    }

    private fun muteSystemBeeps(mute: Boolean) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            if (mute) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_MUTE, 0)
                    audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_MUTE, 0)
                } else {
                    @Suppress("DEPRECATION")
                    audioManager.setStreamMute(AudioManager.STREAM_SYSTEM, true)
                    @Suppress("DEPRECATION")
                    audioManager.setStreamMute(AudioManager.STREAM_NOTIFICATION, true)
                }
                wasMuted = true
            } else if (wasMuted) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_UNMUTE, 0)
                    audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0)
                } else {
                    @Suppress("DEPRECATION")
                    audioManager.setStreamMute(AudioManager.STREAM_SYSTEM, false)
                    @Suppress("DEPRECATION")
                    audioManager.setStreamMute(AudioManager.STREAM_NOTIFICATION, false)
                }
                wasMuted = false
            }
        } catch (_: Exception) {
            // Silently ignore if ROM restricts audio stream adjustment
        }
    }

    private fun formatRecognizedChunk(raw: String): String {
        if (raw.isBlank()) return ""
        val enableSmartPunct = preferencesManager.isSmartPunctuationSync()
        val wordReplacements = preferencesManager.getWordReplacementsSync()
        val processed = SpeechPostProcessor.process(raw, enableSmartPunct, wordReplacements)
        val capitalized = processed.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        val withPunctuation = if (!capitalized.endsWith(".") && !capitalized.endsWith("?") && !capitalized.endsWith("!") && !capitalized.endsWith("\n") && !capitalized.endsWith("»")) {
            "$capitalized."
        } else {
            capitalized
        }
        return withPunctuation
    }

    fun formattedDuration(): String {
        val minutes = durationSeconds / 60
        val seconds = durationSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    fun release() {
        stopRecording()
    }
}
