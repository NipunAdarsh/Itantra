package com.example.itantra.audio

import kotlinx.coroutines.flow.Flow

/**
 * Abstraction over "a continuous stream of PCM audio frames".
 *
 * Downstream consumers (Silero VAD, STT) should depend on this interface
 * rather than a concrete capture implementation such as [MicAudioCapture].
 * This is the Dependency Inversion boundary for the audio module: it keeps
 * VAD/STT code testable and swappable (e.g. against a fake source) without
 * any changes when the capture implementation changes.
 */
interface AudioSource {

    /** True while a capture session is active. */
    val isCapturing: Boolean

    /**
     * Starts capturing and returns a cold [Flow] of [AudioFrame]s.
     *
     * Collecting the flow drives the capture. The flow completes normally
     * when [stop] is called, and completes exceptionally (with an
     * [AudioCaptureException]) if capture cannot start or fails mid-stream.
     * Cancelling the collecting coroutine also stops capture and releases
     * resources, just like calling [stop].
     */
    fun start(): Flow<AudioFrame>

    /** Stops capturing, if active. Safe to call even when not capturing. */
    fun stop()
}
