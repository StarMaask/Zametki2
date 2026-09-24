package com.example.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.AppThemePreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class FontSizeScale(val title: String, val scale: Float) {
    SMALL("Компактный", 0.85f),
    NORMAL("Обычный", 1.0f),
    LARGE("Крупный", 1.18f);

    val scaleMultiplier: Float get() = scale
}

private val Context.dataStore by preferencesDataStore(name = "user_settings")

class UserPreferencesManager(private val context: Context) {

    private val KEY_THEME = stringPreferencesKey("app_theme")
    private val KEY_FONT_SIZE = stringPreferencesKey("font_size")
    private val KEY_PIN_CODE = stringPreferencesKey("pin_code")
    private val KEY_PIN_ENABLED = booleanPreferencesKey("pin_enabled")
    private val KEY_BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
    private val KEY_IS_GRID_LAYOUT = booleanPreferencesKey("is_grid_layout")
    private val KEY_CUSTOM_FONT_PATH = stringPreferencesKey("custom_font_path")
    private val KEY_HANDWRITING_SLANT = androidx.datastore.preferences.core.floatPreferencesKey("handwriting_slant")
    private val KEY_HANDWRITING_THICKNESS = androidx.datastore.preferences.core.floatPreferencesKey("handwriting_thickness")
    private val KEY_HANDWRITING_SPACING = androidx.datastore.preferences.core.floatPreferencesKey("handwriting_spacing")
    private val KEY_HANDWRITING_SAMPLE_PATH = stringPreferencesKey("handwriting_sample_path")
    private val KEY_HANDWRITING_INK_COLOR = stringPreferencesKey("handwriting_ink_color")
    private val KEY_TTS_VOICE_NAME = stringPreferencesKey("tts_voice_name")
    private val KEY_TTS_PITCH = androidx.datastore.preferences.core.floatPreferencesKey("tts_pitch")
    private val KEY_TTS_RATE = androidx.datastore.preferences.core.floatPreferencesKey("tts_rate")

    private val KEY_SPEECH_LANGUAGE = stringPreferencesKey("speech_language")
    private val KEY_SPEECH_ACCURACY = stringPreferencesKey("speech_accuracy")
    private val KEY_AUDIO_SOURCE = stringPreferencesKey("audio_source_profile")
    private val KEY_MIC_SENSITIVITY = stringPreferencesKey("mic_sensitivity")
    private val KEY_SMART_PUNCTUATION = booleanPreferencesKey("speech_smart_punctuation")
    private val KEY_NOISE_SUPPRESSION = booleanPreferencesKey("speech_noise_suppression")
    private val KEY_WORD_REPLACEMENTS = stringPreferencesKey("speech_word_replacements")

    private val syncPrefs = context.getSharedPreferences("user_settings_sync", Context.MODE_PRIVATE)

    fun isGridLayoutSync(): Boolean {
        return syncPrefs.getBoolean("is_grid_layout", true)
    }

    fun getPinCodeSync(): String {
        return syncPrefs.getString("pin_code", null) ?: "0000"
    }

    fun setPinCodeSync(pin: String) {
        syncPrefs.edit().putString("pin_code", pin).apply()
    }

    fun hasCustomPinSetSync(): Boolean {
        return syncPrefs.contains("pin_code")
    }

    fun isPinEnabledSync(): Boolean {
        return syncPrefs.getBoolean("pin_enabled", false)
    }

    fun verifyPinSync(input: String): Boolean {
        val stored = getPinCodeSync()
        return input == stored
    }

    val fontSizeFlow: Flow<FontSizeScale> = context.dataStore.data.map { prefs ->
        val name = prefs[KEY_FONT_SIZE] ?: FontSizeScale.NORMAL.name
        try {
            FontSizeScale.valueOf(name)
        } catch (_: Exception) {
            FontSizeScale.NORMAL
        }
    }

    val pinCodeFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_PIN_CODE] ?: "0000"
    }

    val isPinEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_PIN_ENABLED] ?: false
    }

    val isBiometricEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_BIOMETRIC_ENABLED] ?: false
    }

    val themeFlow: Flow<AppThemePreset> = context.dataStore.data.map { prefs ->
        val name = prefs[KEY_THEME] ?: AppThemePreset.PURITY.name
        try {
            AppThemePreset.valueOf(name)
        } catch (_: Exception) {
            AppThemePreset.PURITY
        }
    }

    val isGridLayoutFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_IS_GRID_LAYOUT] ?: syncPrefs.getBoolean("is_grid_layout", true)
    }

    val customFontPathFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_CUSTOM_FONT_PATH]
    }

    suspend fun setTheme(theme: AppThemePreset) {
        context.dataStore.edit { prefs ->
            prefs[KEY_THEME] = theme.name
        }
    }

    suspend fun setFontSize(scale: FontSizeScale) {
        context.dataStore.edit { prefs ->
            prefs[KEY_FONT_SIZE] = scale.name
        }
    }

    suspend fun setPinCode(pin: String) {
        syncPrefs.edit().putString("pin_code", pin).apply()
        context.dataStore.edit { prefs ->
            prefs[KEY_PIN_CODE] = pin
        }
    }

    suspend fun setPinEnabled(enabled: Boolean) {
        syncPrefs.edit().putBoolean("pin_enabled", enabled).apply()
        context.dataStore.edit { prefs ->
            prefs[KEY_PIN_ENABLED] = enabled
        }
    }

    val ttsVoiceNameFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_TTS_VOICE_NAME]
    }

    val ttsPitchFlow: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_TTS_PITCH] ?: 1.0f
    }

    val ttsRateFlow: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_TTS_RATE] ?: 1.0f
    }

    suspend fun setTtsSettings(voiceName: String?, pitch: Float, rate: Float) {
        context.dataStore.edit { prefs ->
            if (voiceName != null) {
                prefs[KEY_TTS_VOICE_NAME] = voiceName
            } else {
                prefs.remove(KEY_TTS_VOICE_NAME)
            }
            prefs[KEY_TTS_PITCH] = pitch
            prefs[KEY_TTS_RATE] = rate
        }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BIOMETRIC_ENABLED] = enabled
        }
    }

    fun setGridLayoutSync(isGrid: Boolean) {
        syncPrefs.edit().putBoolean("is_grid_layout", isGrid).commit()
    }

    suspend fun setGridLayout(isGrid: Boolean) {
        syncPrefs.edit().putBoolean("is_grid_layout", isGrid).commit()
        context.dataStore.edit { prefs ->
            prefs[KEY_IS_GRID_LAYOUT] = isGrid
        }
    }

    suspend fun setCustomFontPath(path: String?) {
        syncPrefs.edit().putString("custom_font_path", path).apply()
        context.dataStore.edit { prefs ->
            if (path == null) {
                prefs.remove(KEY_CUSTOM_FONT_PATH)
            } else {
                prefs[KEY_CUSTOM_FONT_PATH] = path
            }
        }
    }

    suspend fun saveHandwritingSettings(
        slant: Float,
        thickness: Float,
        spacing: Float,
        samplePath: String?,
        inkColorHex: String?
    ) {
        syncPrefs.edit()
            .putFloat("handwriting_slant", slant)
            .putFloat("handwriting_thickness", thickness)
            .putFloat("handwriting_spacing", spacing)
            .putString("handwriting_sample_path", samplePath)
            .putString("handwriting_ink_color", inkColorHex)
            .apply()

        context.dataStore.edit { prefs ->
            prefs[KEY_HANDWRITING_SLANT] = slant
            prefs[KEY_HANDWRITING_THICKNESS] = thickness
            prefs[KEY_HANDWRITING_SPACING] = spacing
            if (samplePath != null) prefs[KEY_HANDWRITING_SAMPLE_PATH] = samplePath
            if (inkColorHex != null) prefs[KEY_HANDWRITING_INK_COLOR] = inkColorHex
        }
    }

    fun getHandwritingSlantSync(): Float = syncPrefs.getFloat("handwriting_slant", 10f)
    fun getHandwritingThicknessSync(): Float = syncPrefs.getFloat("handwriting_thickness", 4f)
    fun getHandwritingSpacingSync(): Float = syncPrefs.getFloat("handwriting_spacing", 1.2f)
    fun getHandwritingSamplePathSync(): String? = syncPrefs.getString("handwriting_sample_path", null)
    fun getHandwritingInkColorSync(): String? = syncPrefs.getString("handwriting_ink_color", null)

    fun getDefaultNoteFontSync(): String = syncPrefs.getString("default_note_font", "DEFAULT") ?: "DEFAULT"
    fun setDefaultNoteFontSync(fontId: String) {
        syncPrefs.edit().putString("default_note_font", fontId).apply()
    }

    fun setCustomFontPathSync(path: String?) {
        syncPrefs.edit().putString("custom_font_path", path).apply()
    }

    fun saveHandwritingSettingsSync(
        slant: Float,
        thickness: Float,
        spacing: Float,
        samplePath: String?,
        inkColorHex: String?
    ) {
        syncPrefs.edit()
            .putFloat("handwriting_slant", slant)
            .putFloat("handwriting_thickness", thickness)
            .putFloat("handwriting_spacing", spacing)
            .putString("handwriting_sample_path", samplePath)
            .putString("handwriting_ink_color", inkColorHex)
            .apply()
    }

    val speechLanguageFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_SPEECH_LANGUAGE] ?: syncPrefs.getString("speech_language", "auto") ?: "auto"
    }

    val speechAccuracyFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_SPEECH_ACCURACY] ?: syncPrefs.getString("speech_accuracy", "online_high_accuracy") ?: "online_high_accuracy"
    }

    val audioSourceProfileFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_AUDIO_SOURCE] ?: syncPrefs.getString("audio_source_profile", "VOICE_RECOGNITION") ?: "VOICE_RECOGNITION"
    }

    val micSensitivityFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_MIC_SENSITIVITY] ?: syncPrefs.getString("mic_sensitivity", "high") ?: "high"
    }

    val smartPunctuationFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SMART_PUNCTUATION] ?: syncPrefs.getBoolean("speech_smart_punctuation", true)
    }

    val noiseSuppressionFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_NOISE_SUPPRESSION] ?: syncPrefs.getBoolean("speech_noise_suppression", true)
    }

    val wordReplacementsFlow: Flow<Map<String, String>> = context.dataStore.data.map { prefs ->
        val json = prefs[KEY_WORD_REPLACEMENTS] ?: syncPrefs.getString("speech_word_replacements", "{}") ?: "{}"
        parseWordReplacementsJson(json)
    }

    fun getSpeechLanguageSync(): String = syncPrefs.getString("speech_language", "auto") ?: "auto"
    fun getSpeechAccuracySync(): String = syncPrefs.getString("speech_accuracy", "online_high_accuracy") ?: "online_high_accuracy"
    fun getAudioSourceProfileSync(): String = syncPrefs.getString("audio_source_profile", "VOICE_RECOGNITION") ?: "VOICE_RECOGNITION"
    fun getMicSensitivitySync(): String = syncPrefs.getString("mic_sensitivity", "high") ?: "high"
    fun isSmartPunctuationSync(): Boolean = syncPrefs.getBoolean("speech_smart_punctuation", true)
    fun isNoiseSuppressionSync(): Boolean = syncPrefs.getBoolean("speech_noise_suppression", true)

    fun getWordReplacementsSync(): Map<String, String> {
        val json = syncPrefs.getString("speech_word_replacements", "{}") ?: "{}"
        return parseWordReplacementsJson(json)
    }

    private fun parseWordReplacementsJson(raw: String): Map<String, String> {
        return try {
            val result = LinkedHashMap<String, String>()
            if (raw.isBlank() || raw == "{}") return result
            val jsonObject = org.json.JSONObject(raw)
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                result[key] = jsonObject.optString(key, "")
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun serializeWordReplacements(map: Map<String, String>): String {
        return try {
            val jsonObject = org.json.JSONObject()
            for ((k, v) in map) {
                if (k.isNotBlank() && v.isNotBlank()) {
                    jsonObject.put(k.trim(), v.trim())
                }
            }
            jsonObject.toString()
        } catch (_: Exception) {
            "{}"
        }
    }

    suspend fun setSpeechLanguage(lang: String) {
        syncPrefs.edit().putString("speech_language", lang).apply()
        context.dataStore.edit { it[KEY_SPEECH_LANGUAGE] = lang }
    }

    suspend fun setSpeechAccuracy(mode: String) {
        syncPrefs.edit().putString("speech_accuracy", mode).apply()
        context.dataStore.edit { it[KEY_SPEECH_ACCURACY] = mode }
    }

    suspend fun setAudioSourceProfile(profile: String) {
        syncPrefs.edit().putString("audio_source_profile", profile).apply()
        context.dataStore.edit { it[KEY_AUDIO_SOURCE] = profile }
    }

    suspend fun setMicSensitivity(sensitivity: String) {
        syncPrefs.edit().putString("mic_sensitivity", sensitivity).apply()
        context.dataStore.edit { it[KEY_MIC_SENSITIVITY] = sensitivity }
    }

    suspend fun setSmartPunctuation(enabled: Boolean) {
        syncPrefs.edit().putBoolean("speech_smart_punctuation", enabled).apply()
        context.dataStore.edit { it[KEY_SMART_PUNCTUATION] = enabled }
    }

    suspend fun setNoiseSuppression(enabled: Boolean) {
        syncPrefs.edit().putBoolean("speech_noise_suppression", enabled).apply()
        context.dataStore.edit { it[KEY_NOISE_SUPPRESSION] = enabled }
    }

    suspend fun setWordReplacements(map: Map<String, String>) {
        val json = serializeWordReplacements(map)
        syncPrefs.edit().putString("speech_word_replacements", json).apply()
        context.dataStore.edit { it[KEY_WORD_REPLACEMENTS] = json }
    }

    suspend fun addWordReplacement(wrongWord: String, correctWord: String) {
        val current = LinkedHashMap(getWordReplacementsSync())
        current[wrongWord.trim()] = correctWord.trim()
        setWordReplacements(current)
    }

    suspend fun removeWordReplacement(wrongWord: String) {
        val current = LinkedHashMap(getWordReplacementsSync())
        current.remove(wrongWord.trim())
        setWordReplacements(current)
    }
}
