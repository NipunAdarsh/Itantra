package com.example.itantra

import android.content.Context
import android.media.*
import android.util.Log
import com.example.itantra.audio.AudioTrackPlayer
import com.example.itantra.audio.MicAudioCapture
import com.example.itantra.audio.SileroVoiceActivityDetector
import com.example.itantra.audio.SynthesizedAudio
import com.k2fsa.sherpa.onnx.*
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream

class SherpaOnnxEngine(
    private val context: Context,
    private val onTextReady: (String) -> Unit
) {
    private val TAG = "SherpaOnnxEngine"
    private var recognizer: OfflineRecognizer? = null
    private var tts: OfflineTts? = null

    private val micCapture = MicAudioCapture(context)
    private var vadSegmenter: SileroVoiceActivityDetector? = null
    private val player = AudioTrackPlayer()

    private var isListening = false
    private val scope = CoroutineScope(Dispatchers.IO)
    private var captureJob: Job? = null

    init {
        copyAssets()
        initModels()
    }

    private fun copyAssets() {
        val assets = arrayOf(
            "silero_vad.onnx",
            "sherpa-onnx-whisper-tiny/tiny-encoder.onnx",
            "sherpa-onnx-whisper-tiny/tiny-decoder.onnx",
            "sherpa-onnx-whisper-tiny/tiny-tokens.txt",
            "vits-piper-en_US-amy-low/en_US-amy-low.onnx",
            "vits-piper-en_US-amy-low/tokens.txt",
            "vits-piper-en_US-amy-low/en_US-amy-low.onnx.json"
        )

        assets.forEach { path ->
            copyAsset(path)
        }

        copyAssetDir("vits-piper-en_US-amy-low/espeak-ng-data")
    }

    private fun copyAsset(path: String) {
        val destFile = File(context.filesDir, path)
        if (destFile.exists()) return

        destFile.parentFile?.mkdirs()
        try {
            context.assets.open(path).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error copying asset $path", e)
        }
    }

    private fun copyAssetDir(path: String) {
        val assets = context.assets.list(path) ?: return
        for (asset in assets) {
            val fullPath = if (path.isEmpty()) asset else "$path/$asset"
            val children = context.assets.list(fullPath)
            if (children != null && children.isNotEmpty()) {
                copyAssetDir(fullPath)
            } else {
                copyAsset(fullPath)
            }
        }
    }

    private fun initModels() {
        try {
            // VAD initialization
            vadSegmenter = SileroVoiceActivityDetector(
                modelPath = File(context.filesDir, "silero_vad.onnx").absolutePath
            )

            // STT initialization (Whisper)
            val sttConfig = OfflineRecognizerConfig(
                modelConfig = OfflineModelConfig(
                    whisper = OfflineWhisperModelConfig(
                        encoder = File(context.filesDir, "sherpa-onnx-whisper-tiny/tiny-encoder.onnx").absolutePath,
                        decoder = File(context.filesDir, "sherpa-onnx-whisper-tiny/tiny-decoder.onnx").absolutePath
                    ),
                    tokens = File(context.filesDir, "sherpa-onnx-whisper-tiny/tiny-tokens.txt").absolutePath,
                    numThreads = 1,
                    debug = true
                )
            )
            recognizer = OfflineRecognizer(null, sttConfig)

            // TTS initialization (Piper)
            val ttsConfig = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = File(context.filesDir, "vits-piper-en_US-amy-low/en_US-amy-low.onnx").absolutePath,
                        lexicon = "",
                        tokens = File(context.filesDir, "vits-piper-en_US-amy-low/tokens.txt").absolutePath,
                        dataDir = File(context.filesDir, "vits-piper-en_US-amy-low/espeak-ng-data").absolutePath
                    ),
                    numThreads = 1,
                    debug = true
                )
            )
            tts = OfflineTts(null, ttsConfig)
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing models", e)
        }
    }

    fun startListening() {
        if (isListening) return
        val segmenter = vadSegmenter
        if (segmenter == null) {
            Log.e(TAG, "VAD failed to initialize; cannot start listening")
            return
        }
        isListening = true

        captureJob = scope.launch {
            try {
                segmenter.segment(micCapture.start()).collect { segment ->
                    val stream = recognizer?.createStream()
                    stream?.acceptWaveform(segment.samples, segment.sampleRate)
                    recognizer?.decode(stream!!)
                    recognizer?.getResult(stream!!)?.text
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?.let(onTextReady)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during listen/transcribe pipeline", e)
            } finally {
                isListening = false
            }
        }
    }

    fun stopListening() {
        isListening = false
        micCapture.stop()
        captureJob?.cancel()
    }

    fun synthesizeAndPlay(text: String) {
        scope.launch {
            val isAlert = text.startsWith("[ALERT]")
            val playbackText = if (isAlert) text.removePrefix("[ALERT]") else text

            if (isAlert) {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0)
            }

            val audio = tts?.generate(playbackText, 0, 1.0f)
            if (audio != null) {
                try {
                    player.play(SynthesizedAudio(audio.samples, audio.sampleRate))
                } catch (e: Exception) {
                    Log.e(TAG, "Error playing synthesized audio", e)
                }
            }
        }
    }

    fun release() {
        stopListening()
        vadSegmenter?.close()
        player.close()
    }
}
