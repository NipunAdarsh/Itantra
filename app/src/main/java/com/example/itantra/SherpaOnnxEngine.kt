package com.example.itantra

import android.content.Context
import android.media.*
import android.util.Log
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
    private var vad: Vad? = null
    private var tts: OfflineTts? = null
    
    private var audioRecord: AudioRecord? = null
    private var isListening = false
    private val scope = CoroutineScope(Dispatchers.IO)
    private var recordingJob: Job? = null

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

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
            val vadConfig = VadModelConfig(
                sileroVadModelConfig = SileroVadModelConfig(
                    model = File(context.filesDir, "silero_vad.onnx").absolutePath,
                    threshold = 0.5f,
                    minSpeechDuration = 0.25f,
                    minSilenceDuration = 0.5f,
                    windowSize = 512
                ),
                sampleRate = sampleRate,
                numThreads = 1
            )
            vad = Vad(null, vadConfig)

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
        isListening = true
        
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
                return
            }
            
            audioRecord?.startRecording()
            
            recordingJob = scope.launch {
                val buffer = ShortArray(512)
                val audioData = mutableListOf<Float>()
                var isSpeechStarted = false
                
                while (isListening) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        val floatBuffer = FloatArray(read)
                        for (i in 0 until read) {
                            floatBuffer[i] = buffer[i] / 32768.0f
                        }
                        
                        vad?.acceptWaveform(floatBuffer)
                        
                        if (vad?.isSpeechDetected() == true) {
                            if (!isSpeechStarted) {
                                Log.d(TAG, "Speech started")
                                isSpeechStarted = true
                            }
                            audioData.addAll(floatBuffer.toList())
                        } else if (isSpeechStarted) {
                            Log.d(TAG, "Speech ended")
                            val finalAudio = audioData.toFloatArray()
                            if (finalAudio.isNotEmpty()) {
                                val stream = recognizer?.createStream()
                                stream?.acceptWaveform(finalAudio, sampleRate)
                                recognizer?.decode(stream!!)
                                val result = recognizer?.getResult(stream!!)?.text
                                if (!result.isNullOrBlank()) {
                                    onTextReady(result.trim())
                                }
                            }
                            audioData.clear()
                            isSpeechStarted = false
                        }
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for AudioRecord", e)
        }
    }

    fun stopListening() {
        isListening = false
        recordingJob?.cancel()
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord", e)
        }
        audioRecord = null
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
                val samples = audio.samples
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
                    .setBufferSizeInBytes(samples.size * 4)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                
                track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
                track.play()
                val durationMs = (samples.size.toFloat() / audio.sampleRate * 1000).toLong()
                delay(durationMs + 500)
                track.release()
            }
        }
    }
}
