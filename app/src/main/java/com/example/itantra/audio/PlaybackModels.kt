package com.example.itantra.audio

/**
 * Synthesized PCM audio ready to be played out loud.
 *
 * A distinct type from [VoiceSegment] even though the shape is identical --
 * they represent different things (audio to transcribe vs. audio to play),
 * and keeping them separate types catches an accidental mix-up between the
 * two directions of the pipeline at compile time.
 */
data class SynthesizedAudio(
    val samples: FloatArray,
    val sampleRate: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SynthesizedAudio) return false
        return sampleRate == other.sampleRate && samples.contentEquals(other.samples)
    }

    override fun hashCode(): Int {
        var result = samples.contentHashCode()
        result = 31 * result + sampleRate
        return result
    }
}

/**
 * Errors that can occur while setting up audio playback.
 */
sealed class PlaybackException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {

    class DeviceInitFailed(reason: String) :
        PlaybackException("Playback device failed to initialize: $reason")
}
