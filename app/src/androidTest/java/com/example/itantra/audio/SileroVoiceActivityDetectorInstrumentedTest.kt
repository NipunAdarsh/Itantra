package com.example.itantra.audio

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * On-device verification for [SileroVoiceActivityDetector]. Like
 * [MicAudioCaptureInstrumentedTest], this runs as an instrumented test
 * since it depends on the native sherpa-onnx ONNX runtime.
 */
@RunWith(AndroidJUnit4::class)
class SileroVoiceActivityDetectorInstrumentedTest {

    private companion object {
        const val TAG = "SileroVadSmokeTest"
    }

    private lateinit var modelPath: String

    @Before
    fun copyModelToFilesDir() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val destFile = File(context.filesDir, "silero_vad.onnx")
        if (!destFile.exists()) {
            context.assets.open("silero_vad.onnx").use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        modelPath = destFile.absolutePath
    }

    @Test
    fun loadsModelSuccessfullyAndClosesCleanly() {
        val detector = SileroVoiceActivityDetector(modelPath)
        detector.close()
    }

    @Test
    fun silenceProducesNoSegments() = runBlocking {
        val detector = SileroVoiceActivityDetector(modelPath)
        try {
            val silentFrames = flow {
                repeat(60) { // 60 * 512 samples @ 16kHz =~ 1.9s of digital silence
                    emit(AudioFrame(ShortArray(512), 16_000))
                }
            }

            val segments = detector.segment(silentFrames).toList()

            assertTrue(
                "Expected no segments from pure silence, got ${segments.size}",
                segments.isEmpty()
            )
        } finally {
            detector.close()
        }
    }

    /**
     * Not a deterministic assertion of "speech was detected" -- there's no
     * guarantee a human is talking during an automated run. This pipes
     * real mic input (Phase 1) through the real VAD model for ~3 seconds,
     * asserts structural validity of whatever comes out, and logs the
     * result. **Talk into the phone's mic while this runs** and check
     * Logcat (tag "SileroVadSmokeTest") to visually confirm segments land
     * when you speak.
     */
    @Test
    fun liveMicSmokeTest() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val micGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        assumeTrue(
            "RECORD_AUDIO not granted -- launch the app once and accept the permission " +
                "prompt (see Phase 1), then re-run.",
            micGranted
        )

        val capture = MicAudioCapture(context)
        val detector = SileroVoiceActivityDetector(modelPath)
        try {
            val segments = coroutineScope {
                val collected = async { detector.segment(capture.start()).toList() }
                delay(3_000)
                capture.stop()
                collected.await()
            }

            Log.i(TAG, "Captured ${segments.size} segment(s) in ~3s window")
            segments.forEach { segment ->
                assertEquals(16_000, segment.sampleRate)
                assertTrue("Segment should contain samples", segment.samples.isNotEmpty())
                val durationMs = segment.samples.size * 1000L / segment.sampleRate
                Log.i(TAG, "  segment: ${segment.samples.size} samples (~${durationMs}ms)")
            }
        } finally {
            capture.stop()
            detector.close()
        }
    }
}
