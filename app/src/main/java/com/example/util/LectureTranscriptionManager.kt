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
            partialHypothesis = ""

            if (!isRecording || isPaused) return

            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                stopRecording()
                return
            }

            consecutiveErrors++

            // Normal silence pauses (ERROR_NO_MATCH = 7, ERROR_SPEECH_TIMEOUT = 6)
            val isNormalSilence = error == SpeechRecognizer.ERROR_NO_MATCH ||
                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT

            if (isNormalSilence && consecutiveErrors <= 2) {
                // Speaker simply paused or took a breath. Clean cancel and smoothly resume listening
                cleanCancel()
                mainHandler.removeCallbacks(restartRunnable)
                mainHandler.postDelayed(restartRunnable, 250)
            } else {
                // Recognizer busy (8), client error (5), audio (3), network (1,2,4), or repeated timeouts:
                // Completely recreate the recognizer so it never gets locked in a busy loop!
                recreateRecognizerAndRestart(delayMs = 350)
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
                }
            }

            if (isRecording && !isPaused) {
                cleanCancel()
                mainHandler.removeCallbacks(restartRunnable)
                mainHandler.postDelayed(restartRunnable, 200)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partialMatches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val partial = partialMatches?.firstOrNull()?.trim() ?: ""
            if (partial.isNotBlank()) {
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
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 300000L) // 5 minutes continuous session
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L) // 4 sec complete silence before closing chunk
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3500L) // 3.5 sec pause

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
