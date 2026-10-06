package com.example.util

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Utility to safely split large audio files (e.g. 10 to 60+ minutes) into smaller,
 * manageable chunks (3-4 minutes each) so that speech-to-text processing never exceeds
 * memory limits or API payload constraints.
 */
object AudioChunkerUtil {
    private const val TAG = "AudioChunkerUtil"
    const val DEFAULT_CHUNK_DURATION_MS = 10 * 60 * 1000L // 10 minutes
    const val MAX_SINGLE_FILE_BYTES = 10 * 1024 * 1024L // 10 MB

    /**
     * Checks if audio is considered long (>5 minutes or >8 MB).
     */
    fun isLongAudio(file: File): Boolean {
        if (!file.exists() || file.length() == 0L) return false
        if (file.length() > 8 * 1024 * 1024L) return true
        val durationMs = getAudioDurationMs(file)
        return durationMs > 5 * 60 * 1000L
    }

    /**
     * Splits an audio file into smaller chunks if it exceeds target chunk duration or size.
     * If the audio is short and small, returns the original file in a single-element list.
     */
    fun splitAudioIfNeeded(
        context: Context,
        sourceFile: File,
        targetChunkDurationMs: Long = DEFAULT_CHUNK_DURATION_MS
    ): List<File> {
        if (!sourceFile.exists() || sourceFile.length() == 0L) {
            return emptyList()
        }

        val fileLength = sourceFile.length()
        val durationMs = getAudioDurationMs(sourceFile)

        Log.d(TAG, "Audio file: ${sourceFile.name}, size: ${fileLength / 1024 / 1024}MB, duration: ${durationMs / 1000}s")

        // If file is short AND smaller than MAX_SINGLE_FILE_BYTES, no chunking needed
        if (durationMs in 1..targetChunkDurationMs && fileLength <= MAX_SINGLE_FILE_BYTES) {
            return listOf(sourceFile)
        }

        // Try MediaExtractor + MediaMuxer (standard for .m4a, .mp4, .aac, .mp3)
        try {
            val m4aChunks = splitWithMediaMuxer(context, sourceFile, durationMs, targetChunkDurationMs)
            if (m4aChunks.isNotEmpty()) {
                Log.d(TAG, "Successfully split into ${m4aChunks.size} chunks using MediaMuxer")
                return m4aChunks
            }
        } catch (t: Throwable) {
            Log.w(TAG, "MediaMuxer split failed, attempting fallback", t)
        }

        // Try WAV splitting if it's a WAV file
        if (sourceFile.name.endsWith(".wav", ignoreCase = true)) {
            try {
                val wavChunks = splitWavFile(context, sourceFile, targetChunkDurationMs)
                if (wavChunks.isNotEmpty()) {
                    Log.d(TAG, "Successfully split into ${wavChunks.size} WAV chunks")
                    return wavChunks
                }
            } catch (t: Throwable) {
                Log.w(TAG, "WAV split failed", t)
            }
        }

        // Fallback: return original file
        Log.w(TAG, "Chunking could not be performed, returning original file")
        return listOf(sourceFile)
    }

    /**
     * Gets audio duration in milliseconds using MediaMetadataRetriever.
     */
    fun getAudioDurationMs(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durStr?.toLongOrNull() ?: 0L
        } catch (_: Throwable) {
            0L
        } finally {
            try {
                retriever.release()
            } catch (_: Throwable) {}
        }
    }

    /**
     * Splits MPEG-4 / AAC / MP3 audio using Android MediaExtractor and MediaMuxer.
     * This avoids decoding/re-encoding, running extremely fast with near-zero RAM usage.
     */
    private fun splitWithMediaMuxer(
        context: Context,
        sourceFile: File,
        totalDurationMs: Long,
        chunkDurationMs: Long
    ): List<File> {
        val chunksDir = File(context.cacheDir, "audio_chunks").apply { mkdirs() }
        val extractor = MediaExtractor()
        extractor.setDataSource(sourceFile.absolutePath)

        var audioTrackIndex = -1
        var audioFormat: MediaFormat? = null

        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                audioTrackIndex = i
                audioFormat = format
                break
            }
        }

        if (audioTrackIndex == -1 || audioFormat == null) {
            extractor.release()
            return emptyList()
        }

        val totalDurationUs = if (audioFormat.containsKey(MediaFormat.KEY_DURATION)) {
            audioFormat.getLong(MediaFormat.KEY_DURATION)
        } else {
            totalDurationMs * 1000L
        }

        if (totalDurationUs <= 0L) {
            extractor.release()
            return emptyList()
        }

        val chunkDurationUs = chunkDurationMs * 1000L
        val numChunks = ((totalDurationUs + chunkDurationUs - 1) / chunkDurationUs).toInt().coerceAtLeast(1)

        val resultChunks = mutableListOf<File>()
        val buffer = ByteBuffer.allocate(256 * 1024)
        val bufferInfo = MediaCodec.BufferInfo()
        val timestampPrefix = System.currentTimeMillis()

        for (chunkIdx in 0 until numChunks) {
            val startUs = chunkIdx * chunkDurationUs
            val endUs = minOf((chunkIdx + 1) * chunkDurationUs, totalDurationUs)

            if (startUs >= totalDurationUs) break

            val chunkFile = File(chunksDir, "chunk_${timestampPrefix}_${chunkIdx}.m4a")
            var muxer: MediaMuxer? = null
            var muxerStarted = false

            try {
                muxer = MediaMuxer(chunkFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                val muxerTrack = muxer.addTrack(audioFormat)
                muxer.start()
                muxerStarted = true

                extractor.selectTrack(audioTrackIndex)
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

                var firstSampleTimeUs = -1L
                var samplesWritten = 0

                while (true) {
                    val sampleSize = extractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) break

                    val sampleTime = extractor.sampleTime
                    if (sampleTime >= endUs) break

                    if (sampleTime < startUs) {
                        extractor.advance()
                        continue
                    }

                    if (firstSampleTimeUs == -1L) {
                        firstSampleTimeUs = sampleTime
                    }

                    bufferInfo.offset = 0
                    bufferInfo.size = sampleSize
                    bufferInfo.presentationTimeUs = (sampleTime - firstSampleTimeUs).coerceAtLeast(0L)
                    bufferInfo.flags = extractor.sampleFlags

                    muxer.writeSampleData(muxerTrack, buffer, bufferInfo)
                    samplesWritten++
                    extractor.advance()
                }

                if (muxerStarted) {
                    try {
                        muxer.stop()
                    } catch (_: Throwable) {}
                }

                if (samplesWritten > 0 && chunkFile.exists() && chunkFile.length() > 1024L) {
                    resultChunks.add(chunkFile)
                } else {
                    chunkFile.delete()
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Error generating chunk $chunkIdx", t)
                chunkFile.delete()
            } finally {
                try {
                    muxer?.release()
                } catch (_: Throwable) {}
            }
        }

        extractor.release()
        return resultChunks
    }

    /**
     * Splits raw WAV files into smaller chunks with valid 44-byte WAV headers.
     */
    private fun splitWavFile(
        context: Context,
        wavFile: File,
        targetChunkDurationMs: Long
    ): List<File> {
        val chunksDir = File(context.cacheDir, "audio_chunks").apply { mkdirs() }
        val fis = FileInputStream(wavFile)
        val header = ByteArray(44)
        if (fis.read(header) != 44) {
            fis.close()
            return emptyList()
        }

        val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val channels = bb.getShort(22).toInt().coerceAtLeast(1)
        val sampleRate = bb.getInt(24).coerceAtLeast(8000)
        val bitsPerSample = bb.getShort(34).toInt().coerceAtLeast(16)
        val bytesPerSecond = sampleRate * channels * (bitsPerSample / 8)

        val chunkBytes = (bytesPerSecond * (targetChunkDurationMs / 1000.0)).toLong().coerceAtLeast(512 * 1024L)
        val totalPcmBytes = wavFile.length() - 44
        val numChunks = ((totalPcmBytes + chunkBytes - 1) / chunkBytes).toInt().coerceAtLeast(1)

        val result = mutableListOf<File>()
        val buffer = ByteArray(64 * 1024)

        for (i in 0 until numChunks) {
            val chunkFile = File(chunksDir, "chunk_wav_${System.currentTimeMillis()}_$i.wav")
            val fos = FileOutputStream(chunkFile)

            // Write placeholder header
            fos.write(header)

            var written = 0L
            while (written < chunkBytes) {
                val toRead = minOf(buffer.size.toLong(), chunkBytes - written).toInt()
                val read = fis.read(buffer, 0, toRead)
                if (read == -1) break
                fos.write(buffer, 0, read)
                written += read
            }
            fos.flush()

            // Update WAV header with actual chunk size
            if (written > 0) {
                try {
                    val chunkTotal = (written + 36).toInt()
                    val dataSize = written.toInt()
                    val totalBb = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(chunkTotal).array()
                    val dataBb = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(dataSize).array()
                    java.io.RandomAccessFile(chunkFile, "rw").use { raf ->
                        raf.seek(4)
                        raf.write(totalBb)
                        raf.seek(40)
                        raf.write(dataBb)
                    }
                } catch (_: Throwable) {}
                result.add(chunkFile)
            } else {
                chunkFile.delete()
            }
        }
        fis.close()
        return result
    }

    /**
     * Cleans up temporary chunk files generated during splitting.
     */
    fun cleanUpChunks(chunks: List<File>, originalFile: File) {
        for (chunk in chunks) {
            if (chunk != originalFile && chunk.exists() && chunk.absolutePath.contains("audio_chunks")) {
                try {
                    chunk.delete()
                } catch (_: Throwable) {}
            }
        }
    }
}
