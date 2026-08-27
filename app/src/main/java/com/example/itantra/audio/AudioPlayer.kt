package com.example.itantra.audio

import java.io.Closeable

/**
 * Abstraction over "play synthesized audio out loud".
 *
 * Downstream integration code should depend on this interface rather than
 * [AudioTrackPlayer] directly -- the same Dependency Inversion boundary as
 * [AudioSource] and [VoiceSegmenter] in earlier phases.
 */
interface AudioPlayer : Closeable {

    /**
     * Plays [audio] and suspends until playback has genuinely finished --
     * not a guessed delay. Cancelling the calling coroutine stops playback
     * and releases the underlying resources, same as calling [stop].
     */
    suspend fun play(audio: SynthesizedAudio)

    /**
     * Stops any in-progress playback immediately, if active, causing a
     * pending [play] call to return. Safe to call even when nothing is
     * playing.
     */
    fun stop()
}
