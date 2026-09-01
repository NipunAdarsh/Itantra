package com.example.itantra

import android.content.Context
import android.media.*
import android.util.Log
import com.k2fsa.sherpa.onnx.*
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream

enum class AppLanguage(
    val code: String,
    val label: String,
    val flag: String
) {
    ENGLISH("en", "English", "🇬🇧"),
    HINDI("hi", "Hindi", "🇮🇳")
}

class SherpaOnnxEngine(
    private val context: Context,
    private val onTextReady: (String) -> Unit
) {
    private val TAG = "SherpaOnnxEngine"
    private val SENSEVOICE_DIR = "sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17"
    private val WHISPER_BASE_DIR = "sherpa-onnx-whisper-base"

    // Thread synchronization lock for all native JNI pointer operations
    private val engineLock = Any()
    private val audioBufferLock = Any()

    private var recognizer: OfflineRecognizer? = null
    private var vad: Vad? = null
    private var tts: OfflineTts? = null
    private var currentLanguage = AppLanguage.ENGLISH
    
    @Volatile private var isTtsSwitching = false
    @Volatile private var ttsReady = false
    @Volatile private var isSttSwitching = false
    @Volatile private var sttReady = false
    
    private var audioRecord: AudioRecord? = null
    @Volatile private var isListening = false
    private val scope = CoroutineScope(Dispatchers.IO)
    private var recordingJob: Job? = null
    private val audioAccumulator = mutableListOf<Float>()

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    init {
        initModels()
    }

    private fun copyAssets() {
        copyAsset("silero_vad.onnx")
        copyAssetDir(SENSEVOICE_DIR)
        copyAssetDir(WHISPER_BASE_DIR)
        copyAssetDir("vits-piper-en_US-amy-low")
        copyAssetDir("vits-piper-hi_IN-pratham-medium")
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
            // It's a file or empty directory
            copyAsset(path)
            return
        }

        for (asset in assets) {
            val fullPath = if (path.isEmpty()) asset else "$path/$asset"
            copyAssetDir(fullPath)
        }
    }

    /**
     * Build an OfflineRecognizer for the given language.
     * - ENGLISH → SenseVoice (language hardcoded to "en", inverse text normalization enabled)
     * - HINDI   → Whisper-base (language hardcoded to "hi", task = "transcribe")
     *
     * Must be called inside synchronized(engineLock).
     */
    private fun buildSttRecognizer(lang: AppLanguage): OfflineRecognizer? {
        val cores = Runtime.getRuntime().availableProcessors()
        val chosenThreads = cores.coerceIn(2, 4)
        Log.i(TAG, "Detected CPU cores: $cores, STT numThreads set to: $chosenThreads")

        return when (lang) {
            AppLanguage.ENGLISH -> {
                val modelFile = File(context.filesDir, "$SENSEVOICE_DIR/model.int8.onnx")
                val tokensFile = File(context.filesDir, "$SENSEVOICE_DIR/tokens.txt")
                if (!modelFile.exists() || !tokensFile.exists()) {
                    Log.e(TAG, "SenseVoice model files missing: model=${modelFile.exists()}, tokens=${tokensFile.exists()}")
                    return null
                }
                val config = OfflineRecognizerConfig(
                    modelConfig = OfflineModelConfig(
                        senseVoice = OfflineSenseVoiceModelConfig(
                            model = modelFile.absolutePath,
                            language = "en",
                            useInverseTextNormalization = true
                        ),
                        tokens = tokensFile.absolutePath,
                        numThreads = chosenThreads,
                        debug = false
                    ),
                    decodingMethod = "greedy_search"
                )
                val rec = OfflineRecognizer(config = config)
                Log.i(TAG, "SenseVoice STT initialized (English, int8, $chosenThreads threads)")
                rec
            }
            AppLanguage.HINDI -> {
                val encoderFile = File(context.filesDir, "$WHISPER_BASE_DIR/base-encoder.int8.onnx")
                val decoderFile = File(context.filesDir, "$WHISPER_BASE_DIR/base-decoder.int8.onnx")
                val tokensFile = File(context.filesDir, "$WHISPER_BASE_DIR/base-tokens.txt")
                if (!encoderFile.exists() || !decoderFile.exists() || !tokensFile.exists()) {
                    Log.e(TAG, "Whisper-base model files missing: encoder=${encoderFile.exists()}, decoder=${decoderFile.exists()}, tokens=${tokensFile.exists()}")
                    return null
                }
                val config = OfflineRecognizerConfig(
                    modelConfig = OfflineModelConfig(
                        whisper = OfflineWhisperModelConfig(
                            encoder = encoderFile.absolutePath,
                            decoder = decoderFile.absolutePath,
                            language = "hi",
                            task = "transcribe"
                        ),
                        tokens = tokensFile.absolutePath,
                        numThreads = chosenThreads,
                        debug = false
                    ),
                    decodingMethod = "greedy_search"
                )
                val rec = OfflineRecognizer(config = config)
                Log.i(TAG, "Whisper-base STT initialized (Hindi, int8, $chosenThreads threads)")
                rec
            }
        }
    }

    /**
     * Rebuild TTS engine for the given language. Must be called inside synchronized(engineLock).
     */
    private fun rebuildTTS(lang: AppLanguage) {
        try {
            ttsReady = false
            tts?.release()
            tts = null

            // Use the Hindi model's espeak-ng-data as the shared dataDir for ALL TTS engines.
            // espeak-ng is a C-level singleton; the Hindi espeak-ng-data is a superset that
            // contains dictionaries for ALL languages (en_dict, hi_dict, etc.), so both
            // English and Hindi phonemization work correctly without voice-switching conflicts.
            val sharedEspeakDataDir = File(context.filesDir,
                "vits-piper-hi_IN-pratham-medium/espeak-ng-data").absolutePath

            val vitsConfig = when (lang) {
                AppLanguage.ENGLISH -> OfflineTtsVitsModelConfig(
                    model = File(context.filesDir,
                        "vits-piper-en_US-amy-low/en_US-amy-low.onnx").absolutePath,
                    lexicon = "",
                    tokens = File(context.filesDir,
                        "vits-piper-en_US-amy-low/tokens.txt").absolutePath,
                    dataDir = sharedEspeakDataDir
                )
                AppLanguage.HINDI -> OfflineTtsVitsModelConfig(
                    model = File(context.filesDir,
                        "vits-piper-hi_IN-pratham-medium/hi_IN-pratham-medium.onnx").absolutePath,
                    lexicon = "",
                    tokens = File(context.filesDir,
                        "vits-piper-hi_IN-pratham-medium/tokens.txt").absolutePath,
                    dataDir = sharedEspeakDataDir
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
            Log.i(TAG, "TTS initialized successfully for language: ${lang.name}")
        } catch (e: Exception) {
            Log.e(TAG, "TTS rebuild failed for ${lang.name}: ${e.message}", e)
            tts = null
            ttsReady = false
        }
    }

    private fun initModels() {
        try {
            // A) Copy assets
            copyAssets()

            // B) Initialize VAD
            val vadConfig = VadModelConfig(
                sileroVadModelConfig = SileroVadModelConfig(
                    model = File(context.filesDir, "silero_vad.onnx").absolutePath,
                    threshold = 0.5f,
                    minSilenceDuration = 0.3f,
                    minSpeechDuration = 0.1f
                ),
                sampleRate = 16000
            )
            vad = Vad(config = vadConfig)

            // C) Initialize SenseVoice STT as default (English)
            synchronized(engineLock) {
                recognizer = buildSttRecognizer(AppLanguage.ENGLISH)
                sttReady = recognizer != null
                if (sttReady) {
                    Log.i(TAG, "Default STT ready: SenseVoice (English)")
                } else {
                    Log.e(TAG, "Default STT failed to initialize")
                }
            }

            // D) Initialize default TTS (English Piper)
            synchronized(engineLock) {
                rebuildTTS(AppLanguage.ENGLISH)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing models", e)
        }
    }

    fun startListening() {
        // Guard: do not start if STT is switching or not loaded
        if (isSttSwitching || !sttReady) {
            Log.w(TAG, "STT not ready (switching=$isSttSwitching, ready=$sttReady), ignoring PTT press")
            return
        }

        if (isListening) return
        isListening = true
        
        synchronized(audioBufferLock) {
            audioAccumulator.clear()
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord not initialized")
                isListening = false
                return
            }
            
            audioRecord?.startRecording()
            
            recordingJob = scope.launch {
                val buffer = ShortArray(512)
                while (isListening) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        synchronized(audioBufferLock) {
                            for (i in 0 until read) {
                                audioAccumulator.add(buffer[i].toFloat() / 32768.0f)
                            }
                        }
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for AudioRecord", e)
            isListening = false
        }
    }

    fun stopListening() {
        if (!isListening) return
        isListening = false
        
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord", e)
        }
        audioRecord = null
        recordingJob = null

        val finalAudio: FloatArray
        synchronized(audioBufferLock) {
            finalAudio = audioAccumulator.toFloatArray()
            audioAccumulator.clear()
        }

        if (finalAudio.isNotEmpty()) {
            val durationSec = finalAudio.size / 16000.0f
            Log.i(TAG, "Decoding full PTT utterance: ${finalAudio.size} samples (${String.format("%.2f", durationSec)}s)")
            
            scope.launch {
                val transcribed = synchronized(engineLock) {
                    if (!sttReady) {
                        Log.w(TAG, "STT not ready, skipping decode")
                        null
                    } else {
                        decodeAudio(finalAudio)
                    }
                }
                if (!transcribed.isNullOrBlank()) {
                    val sanitized = sanitizeText(transcribed)
                    if (sanitized.length >= 2) {
                        onTextReady(sanitized)
                    }
                }
            }
        }
    }

    /**
     * Create a fresh stream, feed audio, decode, read result, release stream.
     * Must be called inside synchronized(engineLock).
     */
    private fun decodeAudio(audio: FloatArray): String? {
        val rec = recognizer ?: return null
        var stream: OfflineStream? = null
        return try {
            stream = rec.createStream()
            stream.acceptWaveform(audio, sampleRate)
            rec.decode(stream)
            val raw = rec.getResult(stream).text
            Log.d("STT_RAW", "Raw STT output: \"$raw\"")
            raw
        } catch (e: Exception) {
            Log.e(TAG, "STT decode error: ${e.message}", e)
            null
        } finally {
            try {
                stream?.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing STT stream: ${e.message}")
            }
        }
    }

    fun switchLanguage(lang: AppLanguage) {
        if (currentLanguage == lang && ttsReady && tts != null && sttReady && recognizer != null) return
        currentLanguage = lang

        // Mark both engines as switching
        isTtsSwitching = true
        ttsReady = false
        isSttSwitching = true
        sttReady = false

        scope.launch(Dispatchers.IO) {
            synchronized(engineLock) {
                // --- Switch STT ---
                try {
                    Log.i(TAG, "Switching STT to ${lang.name}...")
                    recognizer?.release()
                    recognizer = null
                    recognizer = buildSttRecognizer(lang)
                    Log.i(TAG, "Successfully switched STT to: ${lang.name}")
                } catch (e: Exception) {
                    Log.e(TAG, "STT switch failed for ${lang.name}: ${e.message}", e)
                    recognizer = null
                } finally {
                    sttReady = recognizer != null
                    isSttSwitching = false
                    Log.i(TAG, "STT switch complete: sttReady=$sttReady")
                }

                // --- Switch TTS ---
                try {
                    rebuildTTS(lang)
                    Log.i(TAG, "Successfully switched TTS voice to: ${lang.name}")
                } catch (e: Exception) {
                    Log.e(TAG, "TTS switch failed for ${lang.name}", e)
                } finally {
                    isTtsSwitching = false
                }
            }
        }
    }

    companion object {
        fun detectScript(text: String): AppLanguage {
            for (ch in text) {
                val code = ch.code
                if (code in 0x0900..0x097F) return AppLanguage.HINDI  // Devanagari
            }
            return AppLanguage.ENGLISH
        }
    }

    private fun sanitizeText(rawText: String): String {
        return rawText
            .replace(Regex("<\\|.*?\\|>"), "")
            .replace(Regex("\\[.*?\\]"), "")
            .replace(Regex("[\\x00-\\x1F\\x7F]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun synthesizeAndPlay(text: String) {
        val isAlert = text.startsWith("[ALERT]")
        val playbackText = if (isAlert) text.removePrefix("[ALERT]").trim() else text.trim()
        if (playbackText.isBlank()) return

        val detectedLang = detectScript(playbackText)

        scope.launch(Dispatchers.IO) {
            try {
                // Auto-switch voice to match script if needed
                if (currentLanguage != detectedLang || !ttsReady || tts == null) {
                    Log.d("TTS", "Auto-switched voice to $detectedLang for incoming text: \"$playbackText\"")
                    synchronized(engineLock) {
                        currentLanguage = detectedLang
                        rebuildTTS(detectedLang)
                    }
                }

                val currentTts = synchronized(engineLock) { tts } ?: return@launch
                
                if (isAlert) {
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                    audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, AudioManager.FLAG_SHOW_UI)
                }

                val audio = synchronized(engineLock) {
                    currentTts.generate(
                        text = playbackText,
                        sid = 0,
                        speed = 1.0f
                    )
                }

                if (audio != null && audio.samples.isNotEmpty()) {
                    val samples = audio.samples
                    Log.d("TTS", "TTS generated ${samples.size} samples at ${audio.sampleRate} Hz for \"$playbackText\"")
                    
                    val minBufSize = AudioTrack.getMinBufferSize(
                        audio.sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_FLOAT
                    )
                    val bufferSizeInBytes = maxOf(minBufSize, samples.size * 4)

                    val track = AudioTrack.Builder()
                        .setAudioAttributes(AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build())
                        .setAudioFormat(AudioFormat.Builder()
                            .setSampleRate(audio.sampleRate)
                            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                        .setBufferSizeInBytes(bufferSizeInBytes)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build()
                    
                    if (track.state == AudioTrack.STATE_INITIALIZED) {
                        track.play()
                        val written = track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
                        if (written > 0) {
                            val durationMs = (samples.size.toFloat() / audio.sampleRate * 1000).toLong()
                            delay(durationMs + 200)
                        } else {
                            Log.e(TAG, "AudioTrack write returned code: $written")
                        }
                        try {
                            track.stop()
                        } catch (e: Exception) {
                            Log.w(TAG, "AudioTrack stop warning: ${e.message}")
                        }
                        track.release()
                    } else {
                        Log.e(TAG, "AudioTrack not initialized")
                    }
                } else {
                    Log.w(TAG, "TTS generate returned null or empty samples for text: \"$playbackText\"")
                }
            } catch (e: Exception) {
                Log.e(TAG, "TTS playback failed: ${e.message}", e)
            }
        }
    }

    fun release() {
        stopListening()
        synchronized(engineLock) {
            recognizer?.release()
            recognizer = null
            tts?.release()
            tts = null
            vad?.release()
            vad = null
        }
        scope.cancel()
    }
}
