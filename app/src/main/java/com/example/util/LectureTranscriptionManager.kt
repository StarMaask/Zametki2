package com.example.util

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaRecorder
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Manager for continuous, high-fidelity audio recording & speech transcription.
 *
 * Supports two distinct modes:
 * 1. [MODE_CONTINUOUS_AUDIO]: Truly uninterrupted audio recording using MediaRecorder.
 *    - The microphone remains ON continuously without periodic restart loops, beeps, or disconnects.
 *    - Saves full high-quality audio (.m4a) attached to the note.
 *    - Real-time amplitude and duration tracking.
 *    - Automatically transcribes speech using Gemini AI or offers manual one-tap transcription.
 *
 * 2. [MODE_STREAMING_SPEECH]: Real-time speech-to-text with system sound suppression.
 *    - Formats text on the fly with smart punctuation and vocabulary replacements.
 *    - Extended silence timeouts to prevent premature stops.
 */
class LectureTranscriptionManager(private val context: Context) {

    companion object {
        private const val TAG = "LectureTranscription"
        const val MODE_CONTINUOUS_AUDIO = "continuous_audio"
        const val MODE_STREAMING_SPEECH = "streaming_speech"
    }

    var activeMode by mutableStateOf(MODE_CONTINUOUS_AUDIO)
        private set

    var isRecording by mutableStateOf(false)
        private set

    var isPaused by mutableStateOf(false)
        private set

    var isListening by mutableStateOf(false)
        private set

    var isTranscribing by mutableStateOf(false)
        private set

    var transcriptionProgress by mutableStateOf("")
        private set

    var durationSeconds by mutableLongStateOf(0L)
        private set

    var partialHypothesis by mutableStateOf("")
        private set

    var currentAmplitude by mutableFloatStateOf(0.05f)
        private set

    var currentRmsDb by mutableFloatStateOf(0f)
        private set

    private val preferencesManager = UserPreferencesManager(context)
    private var speechRecognizer: SpeechRecognizer? = null
    private var mediaRecorder: MediaRecorder? = null
    private var currentRecordingFile: File? = null

    private var onTextAppendedCallback: ((String) -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null
    private var onAudioSavedCallback: ((String) -> Unit)? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var consecutiveErrors = 0
    private var lastPartialRaw = ""
    private var lastCommittedRaw = ""
    private var currentNoteTitle = "Новая заметка"
    private var wasMuted = false

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

    private val amplitudeRunnable = object : Runnable {
        override fun run() {
            if (isRecording && !isPaused && activeMode == MODE_CONTINUOUS_AUDIO) {
                try {
                    val maxAmp = mediaRecorder?.maxAmplitude ?: 0
                    val normalized = (maxAmp / 28000f).coerceIn(0.04f, 1f)
                    currentAmplitude = normalized
                    currentRmsDb = normalized * 10f
                } catch (_: Exception) {}
            }
            if (isRecording && activeMode == MODE_CONTINUOUS_AUDIO) {
                mainHandler.postDelayed(this, 120)
            }
        }
    }

    private val restartRunnable = Runnable {
        if (isRecording && !isPaused && activeMode == MODE_STREAMING_SPEECH) {
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
            currentAmplitude = (rmsdB / 10f).coerceIn(0.04f, 1f)
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.d(TAG, "onEndOfSpeech")
            isListening = false
        }

        override fun onError(error: Int) {
            Log.w(TAG, "onError: code=$error")
            isListening = false

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

            if (!isRecording || isPaused || activeMode != MODE_STREAMING_SPEECH) return

            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                stopRecording()
                onErrorCallback?.invoke("Требуется разрешение на использование микрофона")
                return
            }

            val isNormalSilence = error == SpeechRecognizer.ERROR_NO_MATCH ||
                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT

            if (isNormalSilence) {
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
                    onErrorCallback?.invoke("Распознавание речи остановлено из-за системной ошибки ($error)")
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

            if (isRecording && !isPaused && activeMode == MODE_STREAMING_SPEECH) {
                cleanCancel()
                mainHandler.removeCallbacks(restartRunnable)
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

    /**
     * Starts audio recording or streaming speech recognition.
     */
    fun startRecording(
        noteTitle: String = "Новая заметка",
        mode: String? = null,
        onError: ((String) -> Unit)? = null,
        onAudioSaved: ((String) -> Unit)? = null,
        onTextAppended: (String) -> Unit
    ): Boolean {
        val selectedMode = mode ?: preferencesManager.getLectureRecordingModeSync()
        activeMode = selectedMode
        currentNoteTitle = noteTitle
        onErrorCallback = onError
        onAudioSavedCallback = onAudioSaved
        onTextAppendedCallback = onTextAppended
        durationSeconds = 0L
        partialHypothesis = ""
        isPaused = false
        consecutiveErrors = 0
        currentAmplitude = 0.05f

        com.example.service.LectureRecordingService.onServiceActionReceived = { action ->
            when (action) {
                com.example.service.LectureRecordingService.ACTION_TOGGLE_PAUSE -> togglePause()
                com.example.service.LectureRecordingService.ACTION_STOP -> stopRecording()
            }
        }
        com.example.service.LectureRecordingService.start(context, noteTitle)

        mainHandler.removeCallbacks(timerRunnable)
        mainHandler.postDelayed(timerRunnable, 1000)

        return if (selectedMode == MODE_CONTINUOUS_AUDIO) {
            startContinuousAudioRecording()
        } else {
            startStreamingSpeechRecognition()
        }
    }

    private fun startContinuousAudioRecording(): Boolean {
        return try {
            val audioDir = File(context.filesDir, "audio_records").apply { mkdirs() }
            val recordFile = File(audioDir, "rec_${System.currentTimeMillis()}.m4a")
            currentRecordingFile = recordFile

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(recordFile.absolutePath)
                prepare()
                start()
            }

            mediaRecorder = recorder
            isRecording = true
            isListening = true

            mainHandler.removeCallbacks(amplitudeRunnable)
            mainHandler.postDelayed(amplitudeRunnable, 120)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MediaRecorder", e)
            isRecording = false
            isListening = false
            mediaRecorder?.release()
            mediaRecorder = null
            onErrorCallback?.invoke("Ошибка запуска непрерывной записи: ${e.localizedMessage}")
            false
        }
    }

    private fun startStreamingSpeechRecognition(): Boolean {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onErrorCallback?.invoke("Распознавание речи недоступно на данном устройстве")
            return false
        }
        isRecording = true
        muteSystemSounds(true)
        initAndListen()
        return true
    }

    fun togglePause() {
        if (!isRecording) return
        isPaused = !isPaused

        if (activeMode == MODE_CONTINUOUS_AUDIO) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    if (isPaused) {
                        mediaRecorder?.pause()
                    } else {
                        mediaRecorder?.resume()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Pause/Resume error on MediaRecorder", e)
            }
        } else {
            if (isPaused) {
                partialHypothesis = ""
                isListening = false
                cleanCancel()
                muteSystemSounds(false)
            } else {
                muteSystemSounds(true)
                safeStartListening()
            }
        }

        com.example.service.LectureRecordingService.updateStatus(
            context,
            isPaused,
            formattedDuration(),
            currentNoteTitle
        )
    }

    fun stopRecording() {
        if (!isRecording && !isTranscribing) return

        val wasContinuous = activeMode == MODE_CONTINUOUS_AUDIO
        val savedFile = currentRecordingFile

        isRecording = false
        isPaused = false
        isListening = false
        partialHypothesis = ""
        consecutiveErrors = 0
        currentAmplitude = 0.05f

        muteSystemSounds(false)

        mainHandler.removeCallbacks(timerRunnable)
        mainHandler.removeCallbacks(amplitudeRunnable)
        mainHandler.removeCallbacks(restartRunnable)

        com.example.service.LectureRecordingService.stop(context)

        if (wasContinuous) {
            try {
                mediaRecorder?.stop()
            } catch (e: Exception) {
                Log.w(TAG, "MediaRecorder stop failed", e)
            }
            try {
                mediaRecorder?.release()
            } catch (_: Exception) {}
            mediaRecorder = null

            if (savedFile != null && savedFile.exists() && savedFile.length() > 0L) {
                val filePath = savedFile.absolutePath
                onAudioSavedCallback?.invoke(filePath)

                // Automatic speech-to-text transcription via Gemini AI
                isTranscribing = true
                transcriptionProgress = "Оцифровка записи в текст через Gemini ИИ..."
                scope.launch {
                    try {
                        val result = GeminiOcrService.transcribeAudioWithGemini(context, savedFile)
                        withContext(Dispatchers.Main) {
                            if (result.isSuccess) {
                                val text = result.getOrNull().orEmpty()
                                if (text.isNotBlank()) {
                                    val formatted = formatRecognizedChunk(text)
                                    onTextAppendedCallback?.invoke(formatted)
                                }
                            } else {
                                val err = result.exceptionOrNull()?.localizedMessage
                                if (!err.isNullOrBlank() && !err.contains("API-ключ")) {
                                    onErrorCallback?.invoke(err)
                                }
                            }
                            isTranscribing = false
                            transcriptionProgress = ""
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Transcription failed", e)
                        withContext(Dispatchers.Main) {
                            isTranscribing = false
                            transcriptionProgress = ""
                        }
                    }
                }
            }
        } else {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            } catch (_: Exception) {}
            speechRecognizer = null
        }
    }

    /**
     * Suppresses system beep/chimes during speech recognition sessions.
     */
    private fun muteSystemSounds(mute: Boolean) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            if (mute && !wasMuted) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_MUTE, 0)
                    audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_MUTE, 0)
                } else {
                    @Suppress("DEPRECATION")
                    audioManager.setStreamMute(AudioManager.STREAM_SYSTEM, true)
                }
                wasMuted = true
            } else if (!mute && wasMuted) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_UNMUTE, 0)
                    audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0)
                } else {
                    @Suppress("DEPRECATION")
                    audioManager.setStreamMute(AudioManager.STREAM_SYSTEM, false)
                }
                wasMuted = false
            }
        } catch (_: Exception) {}
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
            if (isRecording && !isPaused && activeMode == MODE_STREAMING_SPEECH) {
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
        if (!isRecording || isPaused || activeMode != MODE_STREAMING_SPEECH) return
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

                // Avoid frequent silence cut-offs
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 15000)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 10000)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 10000)
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
