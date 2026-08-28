package com.example.itantra.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive

/**
 * Tunable capture parameters.
 *
 * Defaults match what Silero VAD and the sherpa-onnx STT model expect:
 * 16 kHz, mono, 16-bit PCM, 512-sample (32 ms) frames. Exposed as a
 * constructor default rather than hardcoded so callers can override without
 * modifying [MicAudioCapture] itself (Open/Closed).
 */
data class AudioCaptureConfig(
    val sampleRate: Int = 16_000,
    val channelConfig: Int = AudioFormat.CHANNEL_IN_MONO,
    val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT,
    val frameSizeInSamples: Int = 512
)

/**
 * [AudioSource] implementation backed by [AudioRecord].
 *
 * Not designed for concurrent overlapping [start] calls — intended to be
 * driven by a single owner that starts/stops it sequentially (e.g. a PTT
 * button or a continuous-listening engine in a later phase).
 */
class MicAudioCapture(
    private val context: Context,
    private val config: AudioCaptureConfig = AudioCaptureConfig()
) : AudioSource {

    private companion object {
        const val TAG = "MicAudioCapture"
        const val LOG_EVERY_N_FRAMES = 20
        const val INTERNAL_BUFFER_MULTIPLIER = 4
    }

    @Volatile
    private var capturing = false

    override val isCapturing: Boolean
        get() = capturing

    override fun start(): Flow<AudioFrame> = callbackFlow {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            close(AudioCaptureException.PermissionDenied())
            awaitClose { }
            return@callbackFlow
        }

        val minBufferSize = AudioRecord.getMinBufferSize(
            config.sampleRate, config.channelConfig, config.audioFormat
        )
        if (minBufferSize <= 0) {
            close(AudioCaptureException.DeviceInitFailed("Unsupported sample rate/format combination"))
            awaitClose { }
            return@callbackFlow
        }

        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                config.sampleRate,
                config.channelConfig,
                config.audioFormat,
                minBufferSize * INTERNAL_BUFFER_MULTIPLIER
            )
        } catch (e: SecurityException) {
            close(AudioCaptureException.PermissionDenied())
            awaitClose { }
            return@callbackFlow
        }

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            close(AudioCaptureException.DeviceInitFailed("AudioRecord.state=${record.state}"))
            awaitClose { }
            return@callbackFlow
        }

        capturing = true
        record.startRecording()
        Log.d(
            TAG,
            "Capture started (sampleRate=${config.sampleRate}, frame=${config.frameSizeInSamples} samples)"
        )

        var frameCount = 0L
        var readError: Int? = null
        val buffer = ShortArray(config.frameSizeInSamples)

        while (isActive && capturing) {
            val read = record.read(buffer, 0, buffer.size)
            if (read > 0) {
                frameCount++
                val frameSamples = buffer.copyOf(read)
                if (frameCount % LOG_EVERY_N_FRAMES == 0L) {
                    Log.d(
                        TAG,
                        "frame #$frameCount amplitude range=[${frameSamples.min()}, ${frameSamples.max()}]"
                    )
                }
                trySend(AudioFrame(frameSamples, config.sampleRate))
            } else {
                Log.e(TAG, "AudioRecord.read() failed with code $read")
                readError = read
                break
            }
        }

        if (readError != null) {
            close(AudioCaptureException.ReadFailed(readError))
        } else {
            close()
        }

        awaitClose {
            Log.d(TAG, "Capture stopping, releasing AudioRecord")
            capturing = false
            try {
                record.stop()
            } catch (e: IllegalStateException) {
                Log.w(TAG, "AudioRecord.stop() failed, likely already stopped", e)
            }
            record.release()
        }
    }.flowOn(Dispatchers.IO)

    override fun stop() {
        capturing = false
    }
}
