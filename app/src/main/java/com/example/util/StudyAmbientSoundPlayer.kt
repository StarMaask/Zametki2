package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Random
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

enum class AmbientSound(val title: String, val description: String) {
    NONE("Тишина", "Без фонового звука"),
    RAIN("Мягкий дождь", "Успокаивающий шелест капель за окном"),
    WHITE_NOISE("Розовый шум", "Мягкое широкополосное маскирование посторонних звуков"),
    ALPHA_WAVES("Альфа-волны (Дрон)", "Гармоничный фон для глубокой фокусировки (10 Гц)"),
    CLOCK_TICK("Тиканье часов", "Ритмичный мягкий пульс концентрации")
}

class StudyAmbientSoundPlayer(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Default)
    private var soundJob: Job? = null
    private var currentSound: AmbientSound = AmbientSound.NONE
    private var volume: Float = 0.5f

    fun play(sound: AmbientSound) {
        currentSound = sound
        soundJob?.cancel()
        if (sound == AmbientSound.NONE) return

        soundJob = scope.launch {
            val sampleRate = 22050
            val minBufSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(sampleRate / 4)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBufSize * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            try {
                track.setVolume(volume)
                track.play()

                val buffer = ShortArray(1024)
                val random = Random()
                var phase1 = 0.0
                var phase2 = 0.0
                var phase3 = 0.0
                var lpf = 0.0
                var tickSampleCount = 0

                while (isActive) {
                    when (sound) {
                        AmbientSound.WHITE_NOISE -> {
                            // Soft pink noise (low-pass filtered white noise)
                            for (i in buffer.indices) {
                                val white = (random.nextFloat() * 2f - 1f) * 16000f
                                lpf += (white - lpf) * 0.12 // Simple low pass
                                buffer[i] = (lpf * 0.5f).toInt().coerceIn(-32768, 32767).toShort()
                            }
                        }
                        AmbientSound.RAIN -> {
                            // Rain simulation: pink noise with random drop transients and wind modulation
                            for (i in buffer.indices) {
                                val white = (random.nextFloat() * 2f - 1f) * 14000f
                                lpf += (white - lpf) * 0.08
                                val drop = if (random.nextFloat() < 0.002f) {
                                    (random.nextFloat() * 8000f)
                                } else 0f
                                val sample = (lpf * 0.45f + drop)
                                buffer[i] = sample.toInt().coerceIn(-32768, 32767).toShort()
                            }
                        }
                        AmbientSound.ALPHA_WAVES -> {
                            // Deep ambient meditative chord with 10Hz beating (alpha binaural illusion)
                            val f1 = 140.0
                            val f2 = 150.0
                            val f3 = 210.0
                            val step1 = 2.0 * PI * f1 / sampleRate
                            val step2 = 2.0 * PI * f2 / sampleRate
                            val step3 = 2.0 * PI * f3 / sampleRate
                            for (i in buffer.indices) {
                                val s1 = sin(phase1) * 0.4
                                val s2 = sin(phase2) * 0.4
                                val s3 = sin(phase3) * 0.2
                                phase1 += step1; if (phase1 > 2 * PI) phase1 -= 2 * PI
                                phase2 += step2; if (phase2 > 2 * PI) phase2 -= 2 * PI
                                phase3 += step3; if (phase3 > 2 * PI) phase3 -= 2 * PI
                                val out = (s1 + s2 + s3) * 12000.0
                                buffer[i] = out.toInt().coerceIn(-32768, 32767).toShort()
                            }
                        }
                        AmbientSound.CLOCK_TICK -> {
                            // Gentle tick every 1 sec
                            for (i in buffer.indices) {
                                val inTickWindow = tickSampleCount < 400
                                if (inTickWindow) {
                                    val t = tickSampleCount.toDouble() / sampleRate
                                    val decay = exp(-t * 220.0)
                                    val wave = sin(2.0 * PI * 950.0 * t) * decay * 14000.0
                                    buffer[i] = wave.toInt().coerceIn(-32768, 32767).toShort()
                                } else {
                                    buffer[i] = 0
                                }
                                tickSampleCount++
                                if (tickSampleCount >= sampleRate) {
                                    tickSampleCount = 0
                                }
                            }
                        }
                        AmbientSound.NONE -> break
                    }
                    track.write(buffer, 0, buffer.size)
                }
            } catch (_: Exception) {
            } finally {
                try {
                    track.stop()
                    track.release()
                } catch (_: Exception) {}
            }
        }
    }

    fun setVolume(vol: Float) {
        volume = vol.coerceIn(0f, 1f)
    }

    fun stop() {
        currentSound = AmbientSound.NONE
        soundJob?.cancel()
        soundJob = null
    }

    fun isPlaying(): Boolean = soundJob?.isActive == true && currentSound != AmbientSound.NONE

    fun getCurrentSound(): AmbientSound = currentSound

    /**
     * Plays a harmonic chime bell indicating session complete, and triggers haptic feedback.
     */
    fun playCompletionChime() {
        // Haptic feedback
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 180, 100, 240), -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 180, 100, 240), -1)
            }
        } catch (_: Exception) {}

        // Audio chime: pleasant 2-tone melodic harmonic (C5 -> E5 -> G5)
        scope.launch {
            val sampleRate = 22050
            val chordTones = listOf(523.25, 659.25, 783.99)
            val durationSec = 1.2
            val totalSamples = (sampleRate * durationSec).toInt()
            val trackBuffer = ShortArray(totalSamples)

            for (i in 0 until totalSamples) {
                val t = i.toDouble() / sampleRate
                val envelope = exp(-t * 2.8)
                var sampleSum = 0.0
                chordTones.forEach { freq ->
                    sampleSum += sin(2.0 * PI * freq * t)
                }
                val sampleValue = (sampleSum / chordTones.size * envelope * 22000.0)
                trackBuffer[i] = sampleValue.toInt().coerceIn(-32768, 32767).toShort()
            }

            try {
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(totalSamples * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(trackBuffer, 0, trackBuffer.size)
                track.play()
                // Wait for playback then release
                kotlinx.coroutines.delay((durationSec * 1000).toLong() + 200)
                track.stop()
                track.release()
            } catch (_: Exception) {}
        }
    }
}
