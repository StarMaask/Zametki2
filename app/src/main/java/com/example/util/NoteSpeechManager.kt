package com.example.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

data class TtsVoiceOption(
    val id: String,
    val displayName: String,
    val genderLabel: String,
    val isHighQuality: Boolean,
    val isNetwork: Boolean
)

class NoteSpeechManager(context: Context) {

    var isSpeaking by mutableStateOf(false)
        private set

    var speechRate by mutableFloatStateOf(1.0f)
        private set

    var speechPitch by mutableFloatStateOf(1.0f)
        private set

    var selectedVoiceId by mutableStateOf<String?>(null)
        private set

    var availableVoices by mutableStateOf<List<TtsVoiceOption>>(emptyList())
        private set

    var currentSpeakingTitle by mutableStateOf("")
        private set

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var pendingText: String? = null

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                val langResult = tts?.setLanguage(Locale("ru"))
                if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.getDefault())
                }

                loadAvailableVoices()

                // Apply initial settings
                tts?.setSpeechRate(speechRate)
                tts?.setPitch(speechPitch)

                pendingText?.let { text ->
                    pendingText = null
                    speak(text, currentSpeakingTitle)
                }
            }
        }

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                isSpeaking = true
            }

            override fun onDone(utteranceId: String?) {
                isSpeaking = false
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                isSpeaking = false
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                isSpeaking = false
            }
        })
    }

    private fun loadAvailableVoices() {
        val ttsInstance = tts ?: return
        try {
            val voices = ttsInstance.voices
            if (!voices.isNullOrEmpty()) {
                val ruVoices = voices.filter { voice ->
                    val lang = voice.locale.language.lowercase()
                    lang == "ru" || lang == Locale.getDefault().language.lowercase()
                }

                val options = ruVoices.mapIndexed { idx, v ->
                    val nameLower = v.name.lowercase()
                    val features = v.features?.map { it.lowercase() }?.toSet() ?: emptySet()
                    val isFemale = features.contains("female") || nameLower.contains("female") || nameLower.contains("f0") || nameLower.contains("dfc")
                    val isMale = features.contains("male") || nameLower.contains("male") || nameLower.contains("m0") || nameLower.contains("ruf")
                    val gender = when {
                        isFemale -> "Женский"
                        isMale -> "Мужской"
                        else -> "Естественный"
                    }
                    val isHq = v.quality >= Voice.QUALITY_HIGH || nameLower.contains("network") || nameLower.contains("high")
                    val isNetwork = v.isNetworkConnectionRequired || nameLower.contains("network")

                    val friendlyName = when {
                        nameLower.contains("google") && isFemale -> "Google Естественный (Женский ${idx + 1})"
                        nameLower.contains("google") && isMale -> "Google Профессиональный (Мужской ${idx + 1})"
                        isFemale -> "Мягкий голос (Женский ${idx + 1})"
                        isMale -> "Чёткий голос (Мужской ${idx + 1})"
                        else -> "Системный голос (${idx + 1})"
                    }

                    TtsVoiceOption(
                        id = v.name,
                        displayName = friendlyName,
                        genderLabel = gender,
                        isHighQuality = isHq,
                        isNetwork = isNetwork
                    )
                }

                availableVoices = options
            }
        } catch (_: Exception) {}
    }

    fun setVoice(voiceId: String?) {
        selectedVoiceId = voiceId
        val ttsInstance = tts ?: return
        if (voiceId != null) {
            try {
                val match = ttsInstance.voices?.firstOrNull { it.name == voiceId }
                if (match != null) {
                    ttsInstance.voice = match
                }
            } catch (_: Exception) {}
        }
    }

    fun setPitch(pitch: Float) {
        val clamped = pitch.coerceIn(0.6f, 1.4f)
        speechPitch = clamped
        tts?.setPitch(clamped)
    }

    fun setRate(rate: Float) {
        val clamped = rate.coerceIn(0.5f, 2.2f)
        speechRate = clamped
        tts?.setSpeechRate(clamped)
    }

    fun speak(text: String, title: String = "") {
        val cleanText = cleanMarkdownForSpeech(text)
        if (cleanText.isBlank()) return

        currentSpeakingTitle = title
        if (!isInitialized) {
            pendingText = cleanText
            return
        }

        tts?.setSpeechRate(speechRate)
        tts?.setPitch(speechPitch)
        val fullSpeechText = if (title.isNotBlank()) "$title. $cleanText" else cleanText
        tts?.speak(fullSpeechText, TextToSpeech.QUEUE_FLUSH, null, "NOTE_READ_ALOUD_${System.currentTimeMillis()}")
        isSpeaking = true
    }

    fun previewVoice(voiceId: String?, pitch: Float = speechPitch, rate: Float = speechRate) {
        setVoice(voiceId)
        setPitch(pitch)
        setRate(rate)
        val sample = "Здравствуйте! Так будет звучать озвучивание ваших заметок."
        tts?.speak(sample, TextToSpeech.QUEUE_FLUSH, null, "VOICE_PREVIEW_${System.currentTimeMillis()}")
        isSpeaking = true
    }

    fun stop() {
        tts?.stop()
        isSpeaking = false
        currentSpeakingTitle = ""
    }

    fun cycleSpeechRate(): Float {
        val nextRate = when (speechRate) {
            1.0f -> 1.25f
            1.25f -> 1.5f
            1.5f -> 2.0f
            2.0f -> 0.75f
            else -> 1.0f
        }
        setRate(nextRate)
        return nextRate
    }

    fun release() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {}
        tts = null
        isSpeaking = false
    }

    private fun cleanMarkdownForSpeech(markdown: String): String {
        return markdown
            .replace(Regex("\\[color=#[0-9a-fA-F]{6}\\]"), "")
            .replace("[/color]", "")
            .replace(Regex("<[^>]*>"), "")
            .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
            .replace(Regex("\\*(.*?)\\*"), "$1")
            .replace(Regex("~~(.*?)~~"), "$1")
            .replace(Regex("==(.*?)=="), "$1")
            .replace(Regex("__(.*?)__"), "$1")
            .replace(Regex("^#+\\s*", RegexOption.MULTILINE), "")
            .replace(Regex("^[•\\-*+]\\s*", RegexOption.MULTILINE), "")
            .replace(Regex("^>\\s*", RegexOption.MULTILINE), "")
            .trim()
    }
}
