package com.example.itantra.audio

import android.util.Log
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Tunable Silero VAD parameters passed through to the native model.
 *
 * Defaults match the values already validated in the existing prototype
 * (`SherpaOnnxEngine.kt`), with [maxSpeechDurationSeconds] added as a
 * safety cap so continuous non-silent noise can't grow a single segment
 * unbounded.
 */
data class VadTuning(
    val sampleRate: Int = 16_000,
    val threshold: Float = 0.5f,
    val minSpeechDurationSeconds: Float = 0.25f,
    val minSilenceDurationSeconds: Float = 0.5f,
    val maxSpeechDurationSeconds: Float = 20f,
    val windowSize: Int = 512,
    val numThreads: Int = 1
)

/**
 * [VoiceSegmenter] backed by sherpa-onnx's Silero VAD model.
 *
 * The native model is loaded once, eagerly, in the constructor and reused
 * across every call to [segment] -- unlike [MicAudioCapture], which is
 * deliberately re-opened per capture session. That asymmetry is
 * intentional: loading an ONNX graph is comparatively expensive and should
 * happen once, while the microphone should never be held open longer than
 * a single capture session needs.
 *
 * Callers own this instance's lifecycle and must [close] it when done
 * (ideally via `.use { }`) to release the native handle.
 */
class SileroVoiceActivityDetector(
    modelPath: String,
    private val tuning: VadTuning = VadTuning()
) : VoiceSegmenter {

    private companion object {
        const val TAG = "SileroVAD"
    }

    private val vad: Vad = try {
        Vad(
            null,
            VadModelConfig(
                sileroVadModelConfig = SileroVadModelConfig(
                    model = modelPath,
                    threshold = tuning.threshold,
                    minSilenceDuration = tuning.minSilenceDurationSeconds,
                    minSpeechDuration = tuning.minSpeechDurationSeconds,
                    windowSize = tuning.windowSize,
                    maxSpeechDuration = tuning.maxSpeechDurationSeconds
                ),
                sampleRate = tuning.sampleRate,
                numThreads = tuning.numThreads
            )
        )
    } catch (e: Exception) {
        throw VadException.ModelLoadFailed(modelPath, e)
    }

    override fun segment(frames: Flow<AudioFrame>): Flow<VoiceSegment> = flow {
        var sampleRate = tuning.sampleRate

        frames.collect { frame ->
            sampleRate = frame.sampleRate
            vad.acceptWaveform(frame.toNormalizedFloatArray())
            while (!vad.empty()) {
                emit(VoiceSegment(vad.front().samples, sampleRate))
                vad.pop()
            }
        }

        // Upstream (mic capture) completed -- e.g. PTT released -- flush
        // any buffered-but-not-yet-finalized trailing speech instead of
        // silently dropping the tail end of the utterance.
        vad.flush()
        while (!vad.empty()) {
            emit(VoiceSegment(vad.front().samples, sampleRate))
            vad.pop()
        }
    }.flowOn(Dispatchers.Default)

    override fun close() {
        Log.d(TAG, "Releasing Vad native resources")
        vad.release()
    }
}

private fun AudioFrame.toNormalizedFloatArray(): FloatArray =
    FloatArray(samples.size) { i -> samples[i] / 32768f }
