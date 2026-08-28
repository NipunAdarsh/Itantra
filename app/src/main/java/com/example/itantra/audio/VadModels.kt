package com.example.itantra.audio

/**
 * A finalized segment of speech, ready to hand off to STT.
 */
data class VoiceSegment(
    val samples: FloatArray,
    val sampleRate: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VoiceSegment) return false
        return sampleRate == other.sampleRate && samples.contentEquals(other.samples)
    }

    override fun hashCode(): Int {
        var result = samples.contentHashCode()
        result = 31 * result + sampleRate
        return result
    }
}

/**
 * Errors that can occur while setting up voice activity detection.
 */
sealed class VadException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {

    class ModelLoadFailed(modelPath: String, cause: Throwable) :
        VadException("Failed to load VAD model at $modelPath", cause)
}
