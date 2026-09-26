package com.example.util

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.data.preferences.UserPreferencesManager
import java.util.Locale

/**
 * Manager for continuous, stable speech-to-text transcription.
 * - Captures speech seamlessly into notes with real-time partial feedback.
 * - Formats text with smart punctuation and customizable word replacements.
 * - Handles Android audio session lifecycle safely without mic conflicts or infinite crash loops.
 */
class LectureTranscriptionManager(private val context: Context) {

    companion object {
        private const val TAG = "LectureTranscription"
    }

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
    private var onErrorCallback: ((String) -> Unit)? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var consecutiveErrors = 0
    private var lastPartialRaw = ""
    private var lastCommittedRaw = ""
    private var currentNoteTitle = "Новая лекция"

    private val timerRunnable = object : Runnable {
        override fun run() {
            if (isRecording && !isPaused) {
                durationSeconds++
                com.example.service.LectureRecordingService.updateStatus(
                    context,
                    isPaused,
                    formattedDuration(),
                    currentNoteTitle
                )
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
            Log.d(TAG, "onReadyForSpeech")
            isListening = true
            consecutiveErrors = 0
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "onBeginningOfSpeech")
            isListening = true
        }

        override fun onRmsChanged(rmsdB: Float) {
            currentRmsDb = rmsdB
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.d(TAG, "onEndOfSpeech")
            isListening = false
        }

        override fun onError(error: Int) {
            Log.w(TAG, "onError: code=$error")
            isListening = false

            // If the recognizer timed out while user was speaking, commit the partial hypothesis
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
                onErrorCallback?.invoke("Требуется разрешение на использование микрофона")
                return
            }

            // Normal silence pauses (ERROR_NO_MATCH = 7, ERROR_SPEECH_TIMEOUT = 6)
            val isNormalSilence = error == SpeechRecognizer.ERROR_NO_MATCH ||
                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT

            if (isNormalSilence) {
                // Speaker simply took a breath or paused.
                // Cleanly restart after 350ms to let audio HAL complete cleanup
                cleanCancel()
                mainHandler.removeCallbacks(restartRunnable)
                mainHandler.postDelayed(restartRunnable, 350L)
            } else if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_CLIENT) {
                consecutiveErrors++
                if (consecutiveErrors >= 3) {
                    stopRecording()
                    onErrorCallback?.invoke("Сервис распознавания речи занят. Попробуйте еще раз.")
                } else {
                    recreateRecognizerAndRestart(delayMs = 450L)
                }
            } else if (error == SpeechRecognizer.ERROR_AUDIO) {
                consecutiveErrors++
                if (consecutiveErrors >= 2) {
                    stopRecording()
                    onErrorCallback?.invoke("Микрофон временно недоступен или занят другим приложением")
                } else {
                    recreateRecognizerAndRestart(delayMs = 600L)
                }
            } else {
                consecutiveErrors++
                if (consecutiveErrors >= 4) {
                    stopRecording()
                    onErrorCallback?.invoke("Распознавание речи остановлено из-за сетевой или системной ошибки ($error)")
                } else {
                    recreateRecognizerAndRestart(delayMs = 600L)
                }
            }
        }

        override fun onResults(results: Bundle?) {
            Log.d(TAG, "onResults")
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
                // 350ms gap gives Android audio hardware time to finalize previous chunk
                mainHandler.postDelayed(restartRunnable, 350L)
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

    fun startRecording(
        noteTitle: String = "Новая лекция",
        onError: ((String) -> Unit)? = null,
        onTextAppended: (String) -> Unit
    ): Boolean {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            return false
        }

        currentNoteTitle = noteTitle
        onErrorCallback = onError
        onTextAppendedCallback = onTextAppended
        durationSeconds = 0L
        partialHypothesis = ""
        isPaused = false
        isRecording = true
        consecutiveErrors = 0

        // Connect Foreground Service notification callbacks
        com.example.service.LectureRecordingService.onServiceActionReceived = { action ->
            when (action) {
                com.example.service.LectureRecordingService.ACTION_TOGGLE_PAUSE -> togglePause()
                com.example.service.LectureRecordingService.ACTION_STOP -> stopRecording()
            }
        }
        com.example.service.LectureRecordingService.start(context, noteTitle)

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
        } else {
            safeStartListening()
        }
        com.example.service.LectureRecordingService.updateStatus(
            context,
            isPaused,
            formattedDuration(),
            currentNoteTitle
        )
    }

    fun stopRecording() {
        isRecording = false
        isPaused = false
        isListening = false
        partialHypothesis = ""
        consecutiveErrors = 0

        mainHandler.removeCallbacks(timerRunnable)
        mainHandler.removeCallbacks(restartRunnable)

        com.example.service.LectureRecordingService.stop(context)

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
        return SpeechRecognizer.createSpeechRecognizer(context)
    }

    private fun initAndListen() {
        mainHandler.post {
            try {
                cleanCancel()
                speechRecognizer?.destroy()
                speechRecognizer = createRecognizer().apply {
                    setRecognitionListener(recognitionListener)
                }
                safeStartListening()
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing recognizer", e)
                isRecording = false
                onErrorCallback?.invoke("Не удалось запустить распознаватель речи: ${e.message}")
            }
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
                    speechRecognizer = createRecognizer().apply {
                        setRecognitionListener(recognitionListener)
                    }
                    safeStartListening()
                } catch (e: Exception) {
                    Log.e(TAG, "Error recreating recognizer", e)
                    if (isRecording && !isPaused) {
                        mainHandler.postDelayed({ recreateRecognizerAndRestart(600) }, 800)
                    }
                }
            }
        }, delayMs)
    }

    private fun safeStartListening() {
        if (!isRecording || isPaused) return
        try {
            val selectedLang = preferencesManager.getSpeechLanguageSync()
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
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            }
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "startListening error", e)
            recreateRecognizerAndRestart(delayMs = 500)
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

        val includeTimestamps = preferencesManager.isTimestampsInLectureSync()
        return if (includeTimestamps) {
            val timestamp = formattedDuration()
            "[$timestamp] $withPunctuation"
        } else {
            withPunctuation
        }
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
