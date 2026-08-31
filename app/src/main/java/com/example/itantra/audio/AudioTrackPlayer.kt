package com.example.itantra.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Audio attributes used for playback.
 *
 * Defaults to normal voice-note playback (matching the prototype's existing
 * behavior). Left as a constructor parameter rather than hardcoded so a
 * future emergency-alert caller can configure e.g. [AudioAttributes.USAGE_ALARM]
 * without modifying [AudioTrackPlayer] itself -- this class only plays
 * audio, it does not implement alert volume/DND-override behavior.
 */
data class PlaybackConfig(
    val usage: Int = AudioAttributes.USAGE_MEDIA,
    val contentType: Int = AudioAttributes.CONTENT_TYPE_SPEECH
)

/**
 * [AudioPlayer] implementation backed by [AudioTrack].
 *
 * Needs no [android.content.Context] -- unlike microphone capture, audio
 * playback requires no runtime permission on Android.
 */
class AudioTrackPlayer(
    private val config: PlaybackConfig = PlaybackConfig()
) : AudioPlayer {

    private companion object {
        const val TAG = "AudioTrackPlayer"
        const val WRITE_CHUNK_SIZE = 4096
    }

    private val completionLock = Any()

    @Volatile
    private var activeTrack: AudioTrack? = null

    @Volatile
    private var activeContinuation: CancellableContinuation<Unit>? = null

    override suspend fun play(audio: SynthesizedAudio) {
        require(audio.samples.isNotEmpty()) { "Cannot play empty audio" }

        withContext(Dispatchers.IO) {
            val track = buildAudioTrack(audio.sampleRate)
            activeTrack = track
            try {
                suspendCancellableCoroutine { cont ->
                    activeContinuation = cont
                    track.setNotificationMarkerPosition(audio.samples.size)
                    track.setPlaybackPositionUpdateListener(object :
                        AudioTrack.OnPlaybackPositionUpdateListener {
                        override fun onMarkerReached(t: AudioTrack) = completeContinuation()
                        override fun onPeriodicNotification(t: AudioTrack) = Unit
                    })

                    cont.invokeOnCancellation { stopTrackInternal(track) }

                    track.play()
                    writeInChunks(track, audio.samples)
                }
            } finally {
                activeTrack = null
                track.release()
            }
        }
    }

    private fun writeInChunks(track: AudioTrack, samples: FloatArray) {
        var offset = 0
        while (offset < samples.size) {
            val length = minOf(WRITE_CHUNK_SIZE, samples.size - offset)
            track.write(samples, offset, length, AudioTrack.WRITE_BLOCKING)
            offset += length
        }
    }

    private fun buildAudioTrack(sampleRate: Int): AudioTrack {
        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT
        )
        if (minBufferSize <= 0) {
            throw PlaybackException.DeviceInitFailed("Unsupported sample rate: $sampleRate")
        }

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(config.usage)
                    .setContentType(config.contentType)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBufferSize * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        if (track.state != AudioTrack.STATE_INITIALIZED) {
            track.release()
            throw PlaybackException.DeviceInitFailed("AudioTrack.state=${track.state}")
        }
        return track
    }

    private fun completeContinuation() {
        val cont = synchronized(completionLock) {
            val current = activeContinuation
            activeContinuation = null
            current
        }
        if (cont != null && cont.isActive) cont.resume(Unit)
    }

    private fun stopTrackInternal(track: AudioTrack) {
        try {
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                track.pause()
                track.flush()
            }
            track.stop()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "AudioTrack.stop() failed, likely already stopped", e)
        }
    }

    override fun stop() {
        activeTrack?.let { stopTrackInternal(it) }
        completeContinuation()
    }

    override fun close() {
        stop()
    }
}
