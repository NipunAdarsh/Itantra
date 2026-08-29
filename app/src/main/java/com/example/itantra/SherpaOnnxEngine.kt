package com.example.itantra

import android.content.Context
import android.media.*
import android.util.Log
import com.example.itantra.audio.AudioTrackPlayer
import com.example.itantra.audio.MicAudioCapture
import com.example.itantra.audio.PlaybackConfig
import com.example.itantra.audio.SileroVoiceActivityDetector
import com.example.itantra.audio.SynthesizedAudio
import com.example.itantra.audio.VadTuning
import com.k2fsa.sherpa.onnx.*
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream

enum class AppLanguage(val code: String, val label: String) {
    ENGLISH("en", "English"),
    HINDI("hi", "Hindi")
}

class SherpaOnnxEngine(
    private val context: Context,
    private val onTextReady: (String) -> Unit
) {
    private val TAG = "SherpaOnnxEngine"
    private var recognizer: OfflineRecognizer? = null
    private var tts: OfflineTts? = null
    private var currentLanguage = AppLanguage.ENGLISH

    @Volatile private var isTtsSwitching = false
    @Volatile private var ttsReady = false

    private val micCapture = MicAudioCapture(context)
    private var vadSegmenter: SileroVoiceActivityDetector? = null
    private val mediaPlayer = AudioTrackPlayer()
    private val alertPlayer = AudioTrackPlayer(
        PlaybackConfig(usage = AudioAttributes.USAGE_ALARM, contentType = AudioAttributes.CONTENT_TYPE_SPEECH)
    )

    private var isListening = false
    private val scope = CoroutineScope(Dispatchers.IO)
    private var captureJob: Job? = null

    init {
        copyAssets()
        initModels()
    }

    private fun copyAssets() {
        val assets = arrayOf(
            "silero_vad.onnx"
        )

        assets.forEach { path ->
            copyAsset(path)
        }

        copyAssetDir("vits-piper-en_US-amy-low")
        copyAssetDir("vits-piper-hi_IN-pratham-medium")
        copyAssetDir("sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17")
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
        val assets = try {
            context.assets.list(path)
        } catch (e: Exception) {
            null
        } ?: return

        if (assets.isEmpty()) {
            // It's a file, not a directory
            copyAsset(path)
            return
        }

        for (asset in assets) {
            val fullPath = if (path.isEmpty()) asset else "$path/$asset"
            copyAssetDir(fullPath)
        }
    }

    private fun initModels() {
        try {
            // VAD initialization -- tuning matches the values already
            // validated on main (faster than the audio/ module's own
            // defaults), carried over rather than silently reverted.
            vadSegmenter = SileroVoiceActivityDetector(
                modelPath = File(context.filesDir, "silero_vad.onnx").absolutePath,
                tuning = VadTuning(
                    minSpeechDurationSeconds = 0.1f,
                    minSilenceDurationSeconds = 0.3f
                )
            )

            // STT initialization (SenseVoice)
            val sttConfig = OfflineRecognizerConfig(
                modelConfig = OfflineModelConfig(
                    senseVoice = OfflineSenseVoiceModelConfig(
                        model = File(context.filesDir,
                            "sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/model.int8.onnx"
                        ).absolutePath,
                        language = "en",
                        useInverseTextNormalization = true
                    ),
                    tokens = File(context.filesDir,
                        "sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/tokens.txt"
                    ).absolutePath,
                    numThreads = 2,
                    debug = false
                )
            )
            recognizer = OfflineRecognizer(config = sttConfig)

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
            tts = OfflineTts(config = ttsConfig)
            ttsReady = true
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
                    val result = recognizer?.getResult(stream!!)?.text
                    if (!result.isNullOrBlank()) {
                        val sanitized = sanitizeText(result)
                        if (sanitized.length >= 2) {
                            onTextReady(sanitized)
                        }
                    }
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

    fun switchLanguage(lang: AppLanguage) {
        if (currentLanguage == lang) return
        currentLanguage = lang
        isTtsSwitching = true
        ttsReady = false
        scope.launch(Dispatchers.IO) {
            try {
                tts?.release()
                tts = null
                val vitsConfig = when (lang) {
                    AppLanguage.ENGLISH -> OfflineTtsVitsModelConfig(
                        model = File(context.filesDir,
                            "vits-piper-en_US-amy-low/en_US-amy-low.onnx").absolutePath,
                        lexicon = "",
                        tokens = File(context.filesDir,
                            "vits-piper-en_US-amy-low/tokens.txt").absolutePath,
                        dataDir = File(context.filesDir,
                            "vits-piper-en_US-amy-low/espeak-ng-data").absolutePath
                    )
                    AppLanguage.HINDI -> OfflineTtsVitsModelConfig(
                        model = File(context.filesDir,
                            "vits-piper-hi_IN-pratham-medium/hi_IN-pratham-medium.onnx").absolutePath,
                        lexicon = "",
                        tokens = File(context.filesDir,
                            "vits-piper-hi_IN-pratham-medium/tokens.txt").absolutePath,
                        dataDir = File(context.filesDir,
                            "vits-piper-hi_IN-pratham-medium/espeak-ng-data").absolutePath
                    )
                }
                tts = OfflineTts(
                    config = OfflineTtsConfig(
                        model = OfflineTtsModelConfig(
                            vits = vitsConfig,
                            numThreads = 1,
                            debug = false
                        )
                    )
                )
                ttsReady = true
            } catch (e: Exception) {
                Log.e(TAG, "TTS switch failed", e)
            } finally {
                isTtsSwitching = false
            }
        }
    }

    private fun sanitizeText(rawText: String): String {
        // Remove SenseVoice emotion and event tags
        var cleaned = rawText.replace(Regex("<\\|.*?\\|>"), "")

        // Remove all Unicode characters outside basic English ASCII range
        cleaned = cleaned.replace(Regex("[^\\x20-\\x7E]"), "")

        // Remove any remaining brackets and special tokens
        cleaned = cleaned.replace(Regex("\\[.*?\\]"), "")

        // Keep only English letters, numbers, spaces and basic punctuation
        cleaned = cleaned.replace(Regex("[^a-zA-Z0-9\\s.,!?'\\-]"), "")

        // Collapse multiple spaces and trim
        cleaned = cleaned.replace(Regex("\\s+"), " ").trim()

        return cleaned
    }

    fun synthesizeAndPlay(text: String) {
        if (isTtsSwitching) return
        if (!ttsReady) return
        val currentTts = tts ?: return

        scope.launch {
            try {
                val isAlert = text.startsWith("[ALERT]")
                val playbackText = if (isAlert) text.removePrefix("[ALERT]") else text

                if (isAlert) {
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                    audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, AudioManager.FLAG_SHOW_UI)
                }

                val audio = currentTts.generate(playbackText, 0, 1.0f)
                if (audio != null) {
                    // Alert audio is routed through USAGE_ALARM so it
                    // actually plays on the channel just boosted above --
                    // USAGE_MEDIA (STREAM_MUSIC) would be silently unaffected
                    // by the STREAM_ALARM volume change.
                    val player = if (isAlert) alertPlayer else mediaPlayer
                    player.play(SynthesizedAudio(audio.samples, audio.sampleRate))
                }
            } catch (e: Exception) {
                Log.e(TAG, "TTS playback failed", e)
            }
        }
    }

    fun release() {
        stopListening()
        vadSegmenter?.close()
        mediaPlayer.close()
        alertPlayer.close()
    }
}
