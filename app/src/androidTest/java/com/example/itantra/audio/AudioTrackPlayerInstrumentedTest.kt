package com.example.itantra.audio

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.PI
import kotlin.math.sin

/**
 * On-device verification for [AudioTrackPlayer]. Runs as an instrumented
 * test since it needs a real [android.media.AudioTrack] / audio HAL, not
 * because it depends on any native (sherpa-onnx) library like Phases 1-2.
 *
 * Playback correctness is fully testable without a human in the loop --
 * these use synthetic tones of known duration rather than real TTS output
 * (audio *quality* is Member 1's concern, not this module's).
 */
@RunWith(AndroidJUnit4::class)
class AudioTrackPlayerInstrumentedTest {

    private fun sineWave(durationSeconds: Float, sampleRate: Int = 16_000, frequencyHz: Float = 440f): FloatArray {
        val sampleCount = (durationSeconds * sampleRate).toInt()
        return FloatArray(sampleCount) { i ->
            (0.3 * sin(2.0 * PI * frequencyHz * i / sampleRate)).toFloat()
        }
    }

    @Test
    fun playSuspendsUntilPlaybackCompletes() = runBlocking {
        val player = AudioTrackPlayer()
        val audio = SynthesizedAudio(sineWave(durationSeconds = 1.0f), sampleRate = 16_000)

        val startMs = System.currentTimeMillis()
        try {
            player.play(audio)
        } finally {
            player.close()
        }
        val elapsedMs = System.currentTimeMillis() - startMs

        assertTrue(
            "Expected play() to take roughly 1000ms (actual playback), took ${elapsedMs}ms",
            elapsedMs in 800..2500
        )
    }

    @Test
    fun stopInterruptsPlaybackEarly() = runBlocking {
        val player = AudioTrackPlayer()
        val audio = SynthesizedAudio(sineWave(durationSeconds = 5.0f), sampleRate = 16_000)

        val elapsedMs = try {
            coroutineScope {
                val startMs = System.currentTimeMillis()
                val playback = async { player.play(audio) }
                delay(500)
                player.stop()
                playback.await()
                System.currentTimeMillis() - startMs
            }
        } finally {
            player.close()
        }

        assertTrue(
            "Expected stop() to cut playback short of the full 5s, took ${elapsedMs}ms",
            elapsedMs < 2500
        )
    }

    /**
     * Not an automated pass/fail signal on its own -- confirms the engine
     * genuinely drives the speaker. **Listen for a ~1.5s tone** while this
     * runs.
     */
    @Test
    fun audibleSmokeTest() = runBlocking {
        val player = AudioTrackPlayer()
        val audio = SynthesizedAudio(sineWave(durationSeconds = 1.5f, frequencyHz = 880f), sampleRate = 16_000)
        try {
            player.play(audio)
        } finally {
            player.close()
        }
    }
}
