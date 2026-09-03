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
    HINDI("hi", "Hindi", "🇮🇳"),
    GUJARATI("gu", "Gujarati", "🇮🇳"),
    MARATHI("mr", "Marathi", "🇮🇳"),
    KANNADA("kn", "Kannada", "🇮🇳"),
    MALAYALAM("ml", "Malayalam", "🇮🇳"),
    TAMIL("ta", "Tamil", "🇮🇳"),
    TELUGU("te", "Telugu", "🇮🇳"),
    ODIA("or", "Odia", "🇮🇳"),
    BENGALI("bn", "Bengali", "🇮🇳")
}

enum class OperationalMode {
    WALKIE_TALKIE,
    PHONE_MODE
}

class SherpaOnnxEngine(
    private val context: Context,
    private val onTextReady: (String) -> Unit,
    private val onVadSpeechStateChanged: ((Boolean) -> Unit)? = null
) {
    private val TAG = "SherpaOnnxEngine"
    private val SENSEVOICE_DIR = "sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17"

    /** Shared fallback IndicConformer (Devanagari-biased) used for languages without a dedicated checkpoint. */
    private val INDIC_CONFORMER_DIR = "indic-conformer-onnx-sherpa"

    /**
     * Dedicated per-language IndicConformer checkpoints (AI4Bharat monolingual CTC exports).
     * Unlike the shared [INDIC_CONFORMER_DIR] model, each of these was trained on a single
     * language and emits native-script tokens directly with no cross-language bleed — see
     * IndicScriptConverter's pass-through for these languages.
     */
    private val DEDICATED_INDIC_MODEL_DIRS: Map<AppLanguage, String> = mapOf(
        AppLanguage.KANNADA to "indic-conformer-kn",
        AppLanguage.TELUGU to "indic-conformer-te",
        AppLanguage.TAMIL to "indic-conformer-ta"
    )

    /** Resolves which model directory should back STT decoding for [lang]. */
    private fun sttModelDirFor(lang: AppLanguage): String = when (lang) {
        AppLanguage.ENGLISH -> SENSEVOICE_DIR
        else -> DEDICATED_INDIC_MODEL_DIRS[lang] ?: INDIC_CONFORMER_DIR
    }

    private data class PiperVoice(val dir: String, val modelFile: String)

    /**
     * Bundled Piper VITS voices, one per language that has a verified neural TTS model.
     * Languages absent from this map fall back to Android's native TextToSpeech engine
     * (see TtsManager.speak) since no offline-guaranteed voice is bundled for them yet.
     */
    private val PIPER_VOICES: Map<AppLanguage, PiperVoice> = mapOf(
        AppLanguage.ENGLISH to PiperVoice("vits-piper-en_US-amy-low", "en_US-amy-low.onnx"),
        AppLanguage.HINDI to PiperVoice("vits-piper-hi_IN-pratham-medium", "hi_IN-pratham-medium.onnx"),
        AppLanguage.BENGALI to PiperVoice("vits-piper-bn_BD-google-medium", "bn_BD-google-medium.onnx"),
        AppLanguage.MALAYALAM to PiperVoice("vits-piper-ml_IN-meera-medium", "ml_IN-meera-medium.onnx"),
        AppLanguage.MARATHI to PiperVoice("vits-piper-mr_IN-google-medium", "mr_IN-google-medium.onnx"),
        AppLanguage.TELUGU to PiperVoice("vits-piper-te_IN-venkatesh-medium", "te_IN-venkatesh-medium.onnx"),
        AppLanguage.TAMIL to PiperVoice("vits-piper-ta_IN-rasa_female-medium", "ta_IN-rasa_female-medium.onnx")
    )

    // Synchronization locks
    private val engineLock = Any()
    private val audioBufferLock = Any()

    private var recognizer: OfflineRecognizer? = null
    private var activeSttModelType: String? = null // "sensevoice" or "indicconformer"
    private var vad: Vad? = null
    private var tts: OfflineTts? = null
    private var activeTtsLanguage: AppLanguage? = null // language currently loaded into [tts], independent of STT state
    private var currentLanguage = AppLanguage.ENGLISH
    private var currentOperationalMode = OperationalMode.WALKIE_TALKIE

    @Volatile private var isTtsSwitching = false
    @Volatile private var ttsReady = false
    @Volatile private var isSttSwitching = false
    @Volatile private var sttReady = false

    private var audioRecord: AudioRecord? = null
    @Volatile private var isListening = false
    @Volatile private var isContinuousPhoneModeActive = false
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
        copyAssetDir(INDIC_CONFORMER_DIR)
        DEDICATED_INDIC_MODEL_DIRS.values.forEach { copyAssetDir(it) }
        PIPER_VOICES.values.forEach { copyAssetDir(it.dir) }
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
     * - ENGLISH: SenseVoice (int8, English, ITN enabled)
     * - KANNADA, TELUGU, TAMIL: dedicated per-language AI4Bharat IndicConformer CTC
     *   checkpoints (native-script output, no transliteration needed)
     * - REMAINING INDIC LANGUAGES (hi, gu, mr, ml, or, bn): shared AI4Bharat
     *   IndicConformer 120M INT8 CTC (Devanagari-biased for non-Hindi input; routed
     *   through IndicScriptConverter as a best-effort transliteration)
     */
    private fun buildSttRecognizer(lang: AppLanguage): OfflineRecognizer? {
        val cores = Runtime.getRuntime().availableProcessors()
        val chosenThreads = cores.coerceIn(2, 4)
        Log.i(TAG, "Available CPU cores: $cores, STT threads: $chosenThreads")

        return when (lang) {
            AppLanguage.ENGLISH -> {
                val modelFile = File(context.filesDir, "$SENSEVOICE_DIR/model.int8.onnx")
                val tokensFile = File(context.filesDir, "$SENSEVOICE_DIR/tokens.txt")
                if (!modelFile.exists() || !tokensFile.exists()) {
                    Log.e(TAG, "SenseVoice files missing: model=${modelFile.exists()}, tokens=${tokensFile.exists()}")
                    return null
                }
                val config = OfflineRecognizerConfig(
                    featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
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
                activeSttModelType = SENSEVOICE_DIR
                Log.i(TAG, "SenseVoice STT initialized (English, int8, $chosenThreads threads)")
                rec
            }
            else -> {
                val modelDir = sttModelDirFor(lang)
                val modelFile = File(context.filesDir, "$modelDir/model.int8.onnx")
                val tokensFile = File(context.filesDir, "$modelDir/tokens.txt")
                if (!modelFile.exists() || !tokensFile.exists()) {
                    Log.e(TAG, "IndicConformer files missing for ${lang.label} ($modelDir): model=${modelFile.exists()}, tokens=${tokensFile.exists()}")
                    return null
                }
                val config = OfflineRecognizerConfig(
                    featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
                    modelConfig = OfflineModelConfig(
                        nemo = OfflineNemoEncDecCtcModelConfig(
                            model = modelFile.absolutePath
                        ),
                        tokens = tokensFile.absolutePath,
                        numThreads = chosenThreads,
                        debug = false
                    ),
                    decodingMethod = "greedy_search"
                )
                val rec = OfflineRecognizer(config = config)
                activeSttModelType = modelDir
                Log.i(TAG, "AI4Bharat IndicConformer STT initialized for ${lang.label} ($modelDir, int8 CTC, $chosenThreads threads)")
                rec
            }
        }
    }

    /**
     * Rebuild the Piper TTS engine for [lang]. Only languages present in [PIPER_VOICES] are
     * backed by a bundled neural voice; everything else is handled via Android native TTS
     * (see TtsManager) and this returns immediately without touching engine state.
     * All voices share the Hindi bundle's espeak-ng-data (same phonemization data files,
     * just augmented with each new language's dict) to avoid duplicating that ~1MB blob
     * per voice and to prevent voice conflict singleton bugs.
     */
    fun rebuildTTS(lang: AppLanguage) {
        val voice = PIPER_VOICES[lang] ?: return

        try {
            ttsReady = false
            tts?.release()
            tts = null

            val sharedEspeakDataDir = File(context.filesDir,
                "vits-piper-hi_IN-pratham-medium/espeak-ng-data").absolutePath

            val vitsConfig = OfflineTtsVitsModelConfig(
                model = File(context.filesDir, "${voice.dir}/${voice.modelFile}").absolutePath,
                lexicon = "",
                tokens = File(context.filesDir, "${voice.dir}/tokens.txt").absolutePath,
                dataDir = sharedEspeakDataDir
            )

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
            activeTtsLanguage = lang
            Log.i(TAG, "Piper TTS initialized for: ${lang.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Piper TTS rebuild failed for ${lang.name}: ${e.message}", e)
            tts = null
            ttsReady = false
            activeTtsLanguage = null
        }
    }

    private fun initModels() {
        try {
            copyAssets()

            // Initialize Silero VAD
            val vadConfigFile = File(context.filesDir, "silero_vad.onnx")
            val vadConfig = VadModelConfig(
                sileroVadModelConfig = SileroVadModelConfig(
                    model = vadConfigFile.absolutePath,
                    threshold = 0.5f,
                    minSilenceDuration = 0.5f,
                    minSpeechDuration = 0.15f
                ),
                sampleRate = 16000
            )
            vad = Vad(config = vadConfig)

            // Initialize STT (default English)
            synchronized(engineLock) {
                recognizer = buildSttRecognizer(AppLanguage.ENGLISH)
                sttReady = recognizer != null
                if (sttReady) {
                    Log.i(TAG, "Default STT ready: SenseVoice (English)")
                }
            }

            // Initialize Piper TTS (default English)
            synchronized(engineLock) {
                rebuildTTS(AppLanguage.ENGLISH)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing models", e)
        }
    }

    // ── OPERATIONAL MODE & RECORDING ──────────────────────────────────────────

    fun setOperationalMode(mode: OperationalMode) {
        if (currentOperationalMode == mode) return
        currentOperationalMode = mode
        Log.i(TAG, "OperationalMode switched to: $mode")

        if (mode == OperationalMode.PHONE_MODE) {
            startContinuousPhoneMode()
        } else {
            stopContinuousPhoneMode()
        }
    }

    fun getOperationalMode(): OperationalMode = currentOperationalMode

    // ── 1. WALKIE-TALKIE (PTT) ────────────────────────────────────────────────

    fun startListening() {
        if (currentOperationalMode == OperationalMode.PHONE_MODE) return
        if (isSttSwitching || !sttReady) {
            Log.w(TAG, "STT not ready (switching=$isSttSwitching, ready=$sttReady), ignoring PTT")
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
        if (currentOperationalMode == OperationalMode.PHONE_MODE) return
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
            Log.i(TAG, "Decoding PTT utterance: ${finalAudio.size} samples (${String.format("%.2f", durationSec)}s)")
            decodeAndDispatch(finalAudio)
        }
    }

    // ── 2. PHONE MODE (Continuous Auto-VAD Slicing) ───────────────────────────

    private fun startContinuousPhoneMode() {
        if (isContinuousPhoneModeActive) return
        isContinuousPhoneModeActive = true

        recordingJob = scope.launch {
            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )
                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord init failed for Phone Mode")
                    isContinuousPhoneModeActive = false
                    return@launch
                }
                audioRecord?.startRecording()

                val buffer = ShortArray(512)
                val utteranceBuffer = mutableListOf<Float>()
                var speechActive = false
                var lastSpeechTimestamp = 0L
                val SILENCE_THRESHOLD_MS = 500L

                Log.i(TAG, "Continuous Auto-VAD Phone Mode active")

                while (isContinuousPhoneModeActive) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        val floatChunk = FloatArray(read)
                        for (i in 0 until read) {
                            floatChunk[i] = buffer[i].toFloat() / 32768.0f
                        }

                        val localVad = vad
                        if (localVad != null) {
                            localVad.acceptWaveform(floatChunk)
                            val isSpeech = localVad.isSpeechDetected()

                            if (isSpeech) {
                                if (!speechActive) {
                                    speechActive = true
                                    onVadSpeechStateChanged?.invoke(true)
                                }
                                lastSpeechTimestamp = System.currentTimeMillis()
                                for (sample in floatChunk) {
                                    utteranceBuffer.add(sample)
                                }
                            } else if (speechActive) {
                                // Still keep short pause samples in buffer to preserve natural end phonemes
                                for (sample in floatChunk) {
                                    utteranceBuffer.add(sample)
                                }

                                val silenceDuration = System.currentTimeMillis() - lastSpeechTimestamp
                                if (silenceDuration > SILENCE_THRESHOLD_MS) {
                                    // Speech segment completed
                                    speechActive = false
                                    onVadSpeechStateChanged?.invoke(false)

                                    val audioSlice = utteranceBuffer.toFloatArray()
                                    utteranceBuffer.clear()
                                    localVad.clear()

                                    // Filter noise bursts: minimum 0.25s
                                    if (audioSlice.size >= 4000) {
                                        Log.i(TAG, "Phone Mode detected utterance (${audioSlice.size} samples), decoding...")
                                        launch(Dispatchers.IO) {
                                            decodeAndDispatch(audioSlice)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in Phone Mode audio loop", e)
            } finally {
                try {
                    audioRecord?.stop()
                    audioRecord?.release()
                } catch (e: Exception) {
                    Log.w(TAG, "AudioRecord cleanup warning: ${e.message}")
                }
                audioRecord = null
                onVadSpeechStateChanged?.invoke(false)
                Log.i(TAG, "Phone Mode recording stopped")
            }
        }
    }

    private fun stopContinuousPhoneMode() {
        isContinuousPhoneModeActive = false
        recordingJob?.cancel()
        recordingJob = null
        onVadSpeechStateChanged?.invoke(false)
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioRecord: ${e.message}")
        }
        audioRecord = null
    }

    // ── DECODING & TEXT SANITIZATION ──────────────────────────────────────────

    private fun decodeAndDispatch(audio: FloatArray) {
        scope.launch {
            val transcribed = synchronized(engineLock) {
                if (!sttReady) {
                    Log.w(TAG, "STT not ready, skipping decode")
                    null
                } else {
                    decodeAudio(audio)
                }
            }
            if (!transcribed.isNullOrBlank()) {
                val sanitized = sanitizeText(transcribed)
                if (sanitized.length >= 2) {
                    // Convert phonetic Devanagari output to target language's native script
                    val nativeScriptText = IndicScriptConverter.toTargetScript(sanitized, currentLanguage)
                    Log.i(TAG, "STT Transliteration: \"$sanitized\" -> Native (${currentLanguage.label}): \"$nativeScriptText\"")
                    onTextReady(nativeScriptText)
                }
            }
        }
    }

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

    fun decodeAudioForBenchmark(audio: FloatArray): String? {
        return synchronized(engineLock) {
            decodeAudio(audio)
        }
    }

    fun synthesizePiperWav(text: String): FloatArray? {
        val playbackText = text.trim()
        if (playbackText.isBlank()) return null
        return synchronized(engineLock) {
            try {
                tts?.generate(text = playbackText, sid = 0, speed = 1.0f)?.samples
            } catch (e: Exception) {
                null
            }
        }
    }

    fun switchLanguage(lang: AppLanguage) {
        if (currentLanguage == lang) return
        // Each language maps to a specific model directory (see sttModelDirFor); only
        // languages that share the SAME directory (e.g. hi/gu/mr/ml/or/bn on the shared
        // fallback model) can skip a reload. Kannada/Telugu/Tamil each have distinct
        // dedicated weights, so switching into or out of them always reloads.
        //
        // TTS reload is tracked independently of STT reload: two languages can share
        // the same fallback STT directory (e.g. Gujarati and Marathi both use the
        // shared IndicConformer) while needing different Piper voices, or one has a
        // bundled voice and the other doesn't. Coupling the two caused a real bug —
        // switching Gujarati -> Marathi short-circuited on the STT check and never
        // loaded Marathi's Piper voice at all.
        val targetModelDir = sttModelDirFor(lang)
        val needsSttReload = activeSttModelType != targetModelDir || recognizer == null
        val needsTtsReload = PIPER_VOICES.containsKey(lang) && activeTtsLanguage != lang

        currentLanguage = lang

        if (!needsSttReload && !needsTtsReload) {
            Log.i(TAG, "Instant switch to ${lang.label} (reusing active $targetModelDir engine)")
            return
        }

        if (needsSttReload) {
            isSttSwitching = true
            sttReady = false
        }

        scope.launch(Dispatchers.IO) {
            synchronized(engineLock) {
                try {
                    if (needsSttReload) {
                        Log.i(TAG, "Switching STT engine to ${lang.name} ($targetModelDir)...")
                        recognizer?.release()
                        recognizer = null
                        recognizer = buildSttRecognizer(lang)
                    }

                    if (needsTtsReload) {
                        rebuildTTS(lang)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Language switch failed for ${lang.name}: ${e.message}", e)
                } finally {
                    if (needsSttReload) {
                        sttReady = recognizer != null
                        isSttSwitching = false
                    }
                    Log.i(TAG, "Language switch complete: sttReady=$sttReady, ttsReady=$ttsReady for ${lang.label}")
                }
            }
        }
    }

    companion object {
        fun detectScript(text: String, preferredLang: AppLanguage = AppLanguage.ENGLISH): AppLanguage {
            for (ch in text) {
                val code = ch.code
                when (code) {
                    in 0x0980..0x09FF -> return AppLanguage.BENGALI
                    in 0x0A80..0x0AFF -> return AppLanguage.GUJARATI
                    in 0x0B00..0x0B7F -> return AppLanguage.ODIA
                    in 0x0B80..0x0BFF -> return AppLanguage.TAMIL
                    in 0x0C00..0x0C7F -> return AppLanguage.TELUGU
                    in 0x0C80..0x0CFF -> return AppLanguage.KANNADA
                    in 0x0D00..0x0D7F -> return AppLanguage.MALAYALAM
                    in 0x0900..0x097F -> return if (preferredLang == AppLanguage.MARATHI) AppLanguage.MARATHI else AppLanguage.HINDI
                }
            }
            return preferredLang
        }
    }

    fun sanitizeText(rawText: String): String {
        val withoutTags = rawText
            .replace(Regex("<\\|.*?\\|>"), "")
            .replace(Regex("\\[.*?\\]"), "")
            .replace(Regex("[\\x00-\\x1F\\x7F]"), "")

        // Allowed Unicode blocks across all 10 languages:
        // Basic Latin: \u0020-\u007E
        // Devanagari: \u0900-\u097F (Hindi, Marathi)
        // Bengali: \u0980-\u09FF
        // Gujarati: \u0A80-\u0AFF
        // Odia: \u0B00-\u0B7F
        // Tamil: \u0B80-\u0BFF
        // Telugu: \u0C00-\u0C7F
        // Kannada: \u0C80-\u0CFF
        // Malayalam: \u0D00-\u0D7F
        val allowedRegex = Regex("[^\\u0020-\\u007E\\u0900-\\u097F\\u0980-\\u09FF\\u0A80-\\u0AFF\\u0B00-\\u0B7F\\u0B80-\\u0BFF\\u0C00-\\u0C7F\\u0C80-\\u0CFF\\u0D00-\\u0D7F]")

        return withoutTags
            .replace(allowedRegex, "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    // ── PIPER TTS SYNTHESIS (Used by TtsManager for English & Hindi) ───────────

    fun synthesizeAndPlayPiper(text: String, lang: AppLanguage, isAlert: Boolean) {
        val playbackText = text.trim()
        if (playbackText.isBlank()) return

        scope.launch(Dispatchers.IO) {
            try {
                if (activeTtsLanguage != lang || !ttsReady || tts == null) {
                    synchronized(engineLock) {
                        rebuildTTS(lang)
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
                    val minBufSize = AudioTrack.getMinBufferSize(
                        audio.sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_FLOAT
                    )
                    val bufferSizeInBytes = maxOf(minBufSize, samples.size * 4)

                    val audioAttributes = AudioAttributes.Builder()
                        .setUsage(if (isAlert) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()

                    val track = AudioTrack.Builder()
                        .setAudioAttributes(audioAttributes)
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
                            delay(durationMs + 150)
                        }
                        try {
                            track.stop()
                        } catch (e: Exception) {
                            Log.w(TAG, "AudioTrack stop warning: ${e.message}")
                        }
                        track.release()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Piper TTS playback failed: ${e.message}", e)
            }
        }
    }

    fun release() {
        stopListening()
        stopContinuousPhoneMode()
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
