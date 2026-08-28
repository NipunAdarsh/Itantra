package com.example.itantra.audio

/**
 * A single chunk of raw PCM audio captured from an [AudioSource].
 *
 * Carries [sampleRate] alongside the data so downstream consumers (VAD, STT)
 * don't need to import capture constants separately or assume a fixed format.
 */
data class AudioFrame(
    val samples: ShortArray,
    val sampleRate: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioFrame) return false
        return sampleRate == other.sampleRate && samples.contentEquals(other.samples)
    }

    override fun hashCode(): Int {
        var result = samples.contentHashCode()
        result = 31 * result + sampleRate
        return result
    }
}

/**
 * Errors that can occur while capturing audio from an [AudioSource].
 *
 * Modeled as a sealed hierarchy (rather than throwing generic/platform
 * exceptions) so callers can handle each failure mode explicitly, e.g. show
 * a permission prompt for [PermissionDenied] vs. a generic error for
 * [DeviceInitFailed].
 */
sealed class AudioCaptureException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {

    class PermissionDenied :
        AudioCaptureException("RECORD_AUDIO permission is not granted")

    class DeviceInitFailed(reason: String) :
        AudioCaptureException("Audio device failed to initialize: $reason")

    class ReadFailed(val errorCode: Int) :
        AudioCaptureException("AudioRecord.read() failed with error code $errorCode")
}
