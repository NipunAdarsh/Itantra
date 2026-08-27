package com.example.itantra.audio

import kotlinx.coroutines.flow.Flow
import java.io.Closeable

/**
 * Abstraction over "turn a stream of raw audio frames into finalized speech
 * segments".
 *
 * Downstream consumers (STT, in a later phase) should depend on this
 * interface rather than a concrete VAD implementation such as
 * [SileroVoiceActivityDetector] -- the same Dependency Inversion boundary
 * as [AudioSource] in Phase 1.
 */
interface VoiceSegmenter : Closeable {

    /**
     * Consumes [frames] and emits a [VoiceSegment] each time the VAD model
     * finalizes a chunk of speech. When [frames] completes (e.g. the mic
     * capture was stopped), any buffered trailing speech is flushed and
     * emitted before this flow completes.
     */
    fun segment(frames: Flow<AudioFrame>): Flow<VoiceSegment>
}
