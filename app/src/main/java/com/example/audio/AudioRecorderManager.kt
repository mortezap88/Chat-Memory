package com.example.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread
import kotlin.math.sqrt

class AudioRecorderManager(
    private val context: Context,
    var onChunkAvailable: ((base64Wav: String) -> Unit)? = null
) {
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingThread: Thread? = null

    private var recordStartTime: Long = 0
    private val totalPcmBytes = ByteArrayOutputStream()
    private val currentChunkBytes = ByteArrayOutputStream()

    private var silenceDurationMs = 0L
    private var lastCheckTime = 0L

    fun triggerSubtleVibration() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val vibrator = vibratorManager?.defaultVibrator
                vibrator?.vibrate(VibrationEffect.createOneShot(45, 120))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(45, 120))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(45)
                }
            }
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Vibration error: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun startRecording(silencePauseSeconds: Float = 2.0f, sensitivity: String = "Low"): Boolean {
        if (isRecording) return false
        try {
            triggerSubtleVibration()

            val minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val bufferSize = minBufSize.coerceAtLeast(sampleRate * 2) // at least 1 sec buffer

            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.e("AudioRecorder", "AudioRecord not initialized")
                return false
            }

            record.startRecording()
            audioRecord = record
            isRecording = true
            recordStartTime = System.currentTimeMillis()
            lastCheckTime = recordStartTime
            silenceDurationMs = 0L

            totalPcmBytes.reset()
            currentChunkBytes.reset()

            // Exact pause threshold from user setting (in ms)
            val pauseThresholdMs = (silencePauseSeconds * 1000).toLong().coerceAtLeast(600L)

            // Sensitivity controls silence RMS threshold:
            // "High" sensitivity means even soft voice counts as speech (lower silence threshold)
            // "Low" sensitivity needs clearer/louder voice to count as speech (higher silence threshold)
            val silenceRmsThreshold = when (sensitivity.lowercase().trim()) {
                "high" -> 400.0   // detects very soft speech, triggers silence only if RMS < 400
                "normal" -> 650.0 // standard ambient threshold
                "low" -> 900.0    // requires louder speech, ignores ambient noise easily
                else -> 700.0
            }

            recordingThread = thread(start = true, name = "AudioRecordThread") {
                val readBuffer = ShortArray(1024)
                val byteBuffer = ByteBuffer.allocate(readBuffer.size * 2).order(ByteOrder.LITTLE_ENDIAN)

                while (isRecording) {
                    val readCount = record.read(readBuffer, 0, readBuffer.size)
                    if (readCount > 0) {
                        byteBuffer.clear()
                        var sumSq = 0.0
                        for (i in 0 until readCount) {
                            val sample = readBuffer[i]
                            byteBuffer.putShort(sample)
                            sumSq += sample * sample
                        }
                        val rms = sqrt(sumSq / readCount)
                        val bytesRead = byteBuffer.array().copyOf(readCount * 2)

                        synchronized(totalPcmBytes) {
                            totalPcmBytes.write(bytesRead)
                            currentChunkBytes.write(bytesRead)
                        }

                        val now = System.currentTimeMillis()
                        val elapsedSinceLast = now - lastCheckTime
                        lastCheckTime = now

                        // Check silence against sensitivity-adjusted RMS threshold
                        if (rms < silenceRmsThreshold) {
                            silenceDurationMs += elapsedSinceLast
                        } else {
                            silenceDurationMs = 0
                        }

                        // When user pauses for the exact silencePauseDuration and we have >= 0.7s of audio, slice and pre-transcribe!
                        if (silenceDurationMs >= pauseThresholdMs && currentChunkBytes.size() > (sampleRate * 2 * 0.7)) {
                            silenceDurationMs = 0
                            val chunkWavBytes = synchronized(totalPcmBytes) {
                                val pcmData = currentChunkBytes.toByteArray()
                                currentChunkBytes.reset()
                                pcmToWav(pcmData, sampleRate, 1, 16)
                            }
                            val base64 = Base64.encodeToString(chunkWavBytes, Base64.NO_WRAP)
                            try {
                                onChunkAvailable?.invoke(base64)
                            } catch (e: Exception) {
                                Log.w("AudioRecorder", "Chunk callback error: ${e.message}")
                            }
                        }
                    }
                }
            }
            return true
        } catch (e: Exception) {
            Log.e("AudioRecorder", "startRecording error: ${e.message}")
            stopRecording()
            return false
        }
    }

    fun stopRecording(): AudioRecordingResult? {
        if (!isRecording) return null
        triggerSubtleVibration()
        isRecording = false

        try {
            audioRecord?.apply {
                try {
                    stop()
                } catch (_: Exception) {}
                release()
            }
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Release error: ${e.message}")
        } finally {
            audioRecord = null
            recordingThread?.interrupt()
            recordingThread = null
        }

        val durationMs = System.currentTimeMillis() - recordStartTime
        if (durationMs < 350) {
            totalPcmBytes.reset()
            currentChunkBytes.reset()
            return null
        }

        val (fullBytes, finalChunkBytes) = synchronized(totalPcmBytes) {
            val full = totalPcmBytes.toByteArray()
            val finalChunk = currentChunkBytes.toByteArray()
            totalPcmBytes.reset()
            currentChunkBytes.reset()
            Pair(full, finalChunk)
        }

        val fullWav = pcmToWav(fullBytes, sampleRate, 1, 16)
        val finalChunkWav = if (finalChunkBytes.isNotEmpty()) {
            pcmToWav(finalChunkBytes, sampleRate, 1, 16)
        } else null

        return AudioRecordingResult(
            fullAudioBase64 = Base64.encodeToString(fullWav, Base64.NO_WRAP),
            finalChunkBase64 = finalChunkWav?.let { Base64.encodeToString(it, Base64.NO_WRAP) },
            mimeType = "audio/wav",
            durationMs = durationMs
        )
    }

    private fun pcmToWav(pcmData: ByteArray, sampleRate: Int, channels: Int, bitDepth: Int): ByteArray {
        val header = ByteArray(44)
        val totalDataLen = pcmData.size + 36
        val byteRate = sampleRate * channels * bitDepth / 8

        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // PCM
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = ((channels * bitDepth / 8)).toByte()
        header[33] = 0
        header[34] = bitDepth.toByte()
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (pcmData.size and 0xff).toByte()
        header[41] = ((pcmData.size shr 8) and 0xff).toByte()
        header[42] = ((pcmData.size shr 16) and 0xff).toByte()
        header[43] = ((pcmData.size shr 24) and 0xff).toByte()

        val wavStream = ByteArrayOutputStream(44 + pcmData.size)
        wavStream.write(header)
        wavStream.write(pcmData)
        return wavStream.toByteArray()
    }
}

data class AudioRecordingResult(
    val fullAudioBase64: String,
    val finalChunkBase64: String?,
    val mimeType: String,
    val durationMs: Long
)
