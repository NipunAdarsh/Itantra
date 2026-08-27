package com.example.itantra.audio

import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device verification for [MicAudioCapture]. AudioRecord requires a real
 * (or emulated) device with a microphone, so this runs as an instrumented
 * test rather than a JVM unit test.
 *
 * Run via `./gradlew connectedDebugAndroidTest` with a device attached, or
 * from Android Studio: right-click this class -> Run.
 */
@RunWith(AndroidJUnit4::class)
class MicAudioCaptureInstrumentedTest {

    @Before
    fun grantMicPermission() {
        // Equivalent to GrantPermissionRule, without adding the
        // androidx.test:rules dependency: `pm grant` via UiAutomation shell
        // privileges, scoped to this test's own instrumentation.
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(
            instrumentation.targetContext.packageName,
            Manifest.permission.RECORD_AUDIO
        )
    }

    @Test
    fun capturedFramesMatchConfiguredFormat() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = AudioCaptureConfig()
        val capture = MicAudioCapture(context, config)

        val frames = withTimeout(5_000) {
            capture.start().take(10).toList()
        }

        assertTrue("Expected at least one captured frame", frames.isNotEmpty())
        frames.forEach { frame ->
            assertEquals(config.sampleRate, frame.sampleRate)
            assertTrue("Frame should contain samples", frame.samples.isNotEmpty())
            assertTrue(
                "Frame should not exceed configured frame size",
                frame.samples.size <= config.frameSizeInSamples
            )
        }
    }

    @Test
    fun stopEndsCaptureAndReleasesState() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val capture = MicAudioCapture(context)

        val collectJob = launch {
            capture.start().collect { }
        }

        // Give AudioRecord a moment to actually initialize and start reading.
        delay(300)
        assertTrue("Capture should be active after starting", capture.isCapturing)

        capture.stop()
        withTimeout(2_000) { collectJob.join() }

        assertFalse("Capture should be inactive after stop()", capture.isCapturing)
    }
}
