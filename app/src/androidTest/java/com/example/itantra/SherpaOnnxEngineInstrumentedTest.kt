package com.example.itantra

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * End-to-end smoke test for [SherpaOnnxEngine] -- the real class
 * [MainActivity] drives via the PTT button -- exercising both halves of the
 * pipeline through the actual integration point (not the individual
 * modules in isolation, which Phases 1-3 already cover).
 *
 * **Talk into the phone's mic right after this test starts.** The STT half
 * won't pass without real speech. Both the normal and [ALERT]-prefixed TTS
 * playback should be audible -- the alert one noticeably louder, since it
 * routes through STREAM_ALARM (boosted to max) instead of STREAM_MUSIC.
 */
@RunWith(AndroidJUnit4::class)
class SherpaOnnxEngineInstrumentedTest {

    @Test
    fun liveEndToEndPipeline() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val micGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        assumeTrue(
            "RECORD_AUDIO not granted -- launch the app once and accept the permission prompt, then re-run.",
            micGranted
        )

        val receivedText = AtomicReference<String?>(null)
        val latch = CountDownLatch(1)
        val engine = SherpaOnnxEngine(
            context = context,
            onTextReady = { text ->
                receivedText.set(text)
                latch.countDown()
            },
            onVadSpeechStateChanged = null
        )

        try {
            // --- STT half: mic -> MicAudioCapture -> SileroVoiceActivityDetector -> SenseVoice recognizer ---
            engine.startListening()
            val transcribed = latch.await(8, TimeUnit.SECONDS)
            engine.stopListening()

            Log.i("SherpaEngineTest", "Transcribed: ${receivedText.get()}")
            assertTrue(
                "Expected a transcribed callback within 8s -- did you talk into the mic?",
                transcribed && !receivedText.get().isNullOrBlank()
            )

            // --- TTS half: normal playback (mediaPlayer, USAGE_MEDIA) ---
            engine.synthesizeAndPlayPiper("This is a normal message.", AppLanguage.ENGLISH, false)
            runBlocking { delay(2_500) }

            // --- TTS half: alert playback (alertPlayer, USAGE_ALARM, boosted volume) ---
            engine.synthesizeAndPlayPiper("This is an emergency alert test.", AppLanguage.ENGLISH, true)
            runBlocking { delay(2_500) }
        } finally {
            engine.release()
        }
    }
}
