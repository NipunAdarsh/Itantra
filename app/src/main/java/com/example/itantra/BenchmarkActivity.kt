package com.example.itantra

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.os.Environment
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

class BenchmarkActivity : Activity() {

    private val TAG = "TenLangBenchmark"
    private val scope = CoroutineScope(Dispatchers.IO)

    data class TestCase(
        val language: AppLanguage,
        val domain: String,
        val groundTruth: String
    )

    private val testCorpus = listOf(
        // ENGLISH
        TestCase(AppLanguage.ENGLISH, "Tactical/Emergency", "Immediate evacuation required at sector four."),
        TestCase(AppLanguage.ENGLISH, "Conversational", "Can you hear me clearly on this channel?"),
        TestCase(AppLanguage.ENGLISH, "Phonetic Stress", "Check the electrical switch and battery power."),

        // HINDI
        TestCase(AppLanguage.HINDI, "Tactical/Emergency", "तुरंत सहायता की आवश्यकता है।"),
        TestCase(AppLanguage.HINDI, "Conversational", "क्या आप मेरी आवाज़ सुन सकते हैं?"),
        TestCase(AppLanguage.HINDI, "Phonetic Stress", "उत्तर दिशा की ओर सुरक्षित स्थान पर जाएं।"),

        // GUJARATI
        TestCase(AppLanguage.GUJARATI, "Tactical/Emergency", "તરત જ મદદની જરૂર છે."),
        TestCase(AppLanguage.GUJARATI, "Conversational", "શું તમે મારો અવાજ સાંભળી શકો છો?"),
        TestCase(AppLanguage.GUJARATI, "Phonetic Stress", "ઉત્તર દિશા તરફ સુરક્ષિત સ્થળે જાઓ."),

        // MARATHI
        TestCase(AppLanguage.MARATHI, "Tactical/Emergency", "तातडीने मदतीची गरज आहे."),
        TestCase(AppLanguage.MARATHI, "Conversational", "तुम्हाला माझा आवाज नीट येत आहे का?"),
        TestCase(AppLanguage.MARATHI, "Phonetic Stress", "सुरक्षित ठिकाणी लवकरात लवकर पोहोचा."),

        // KANNADA
        TestCase(AppLanguage.KANNADA, "Tactical/Emergency", "ತುರ್ತು ಸಹಾಯದ ಅಗತ್ಯವಿದೆ."),
        TestCase(AppLanguage.KANNADA, "Conversational", "ನಿಮಗೆ ನನ್ನ ಧ್ವನಿ ಕೇಳಿಸುತ್ತಿದೆಯೇ?"),
        TestCase(AppLanguage.KANNADA, "Phonetic Stress", "ಉತ್ತರ ದಿಕ್ಕಿನಲ್ಲಿರುವ ಸುರಕ್ಷಿತ ಸ್ಥಳಕ್ಕೆ ತೆರಳಿ."),

        // MALAYALAM
        TestCase(AppLanguage.MALAYALAM, "Tactical/Emergency", "ഉടൻ സഹായം ആവശ്യമാണ്."),
        TestCase(AppLanguage.MALAYALAM, "Conversational", "നിങ്ങൾക്ക് എന്റെ ശബ്ദം കേൾക്കാമോ?"),
        TestCase(AppLanguage.MALAYALAM, "Phonetic Stress", "സുരക്ഷിതമായ സ്ഥാനത്തേക്ക് മാറുക."),

        // TAMIL
        TestCase(AppLanguage.TAMIL, "Tactical/Emergency", "உடனடி உதவி தேவைப்படுகிறது."),
        TestCase(AppLanguage.TAMIL, "Conversational", "எனது குரல் தெளிவாக கேட்கிறதா?"),
        TestCase(AppLanguage.TAMIL, "Phonetic Stress", "பாதுகாப்பான இடத்திற்கு செல்லுங்கள்."),

        // TELUGU
        TestCase(AppLanguage.TELUGU, "Tactical/Emergency", "వెంటనే సహాయం కావాలి."),
        TestCase(AppLanguage.TELUGU, "Conversational", "మీకు నా మాటలు స్పష్టంగా వినిపిస్తున్నాయా?"),
        TestCase(AppLanguage.TELUGU, "Phonetic Stress", "సురక్షితమైన ప్రదేశానికి చేరుకోండి."),

        // ODIA
        TestCase(AppLanguage.ODIA, "Tactical/Emergency", "ତୁରନ୍ତ ସାହାଯ୍ୟ ଆବଶ୍ୟକ।"),
        TestCase(AppLanguage.ODIA, "Conversational", "ଆପଣ ମୋ ସ୍ୱର ଶୁଣିପାରୁଛନ୍ତି କି?"),
        TestCase(AppLanguage.ODIA, "Phonetic Stress", "ସୁରକ୍ଷିତ ସ୍ଥାନକୁ ଯାଆନ୍ତୁ।"),

        // BENGALI
        TestCase(AppLanguage.BENGALI, "Tactical/Emergency", "জরুরী সাহায্যের প্রয়োজন।"),
        TestCase(AppLanguage.BENGALI, "Conversational", "আপনি কি আমার কথা শুনতে পাচ্ছেন?"),
        TestCase(AppLanguage.BENGALI, "Phonetic Stress", "নিরাপদ স্থানে চলে যান।")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "BenchmarkActivity launched. Starting 10-Language Benchmark...")

        scope.launch {
            runBenchmark()
        }
    }

    private fun runBenchmark() {
        Log.i(TAG, "=================================================================")
        Log.i(TAG, "STARTING 10-LANGUAGE EMPIRICAL BENCHMARK ON DEVICE CPU (30 Tests)")
        Log.i(TAG, "=================================================================")

        var nativeTts: TextToSpeech? = null
        val ttsInitLatch = CountDownLatch(1)
        nativeTts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                Log.i(TAG, "Android Native TextToSpeech initialized")
            } else {
                Log.e(TAG, "Android Native TextToSpeech init failed: $status")
            }
            ttsInitLatch.countDown()
        }
        ttsInitLatch.await(5, TimeUnit.SECONDS)

        val engine = SherpaOnnxEngine(this, {}, null)
        val resultsArray = JSONArray()

        try {
            var activeLang = AppLanguage.ENGLISH
            engine.switchLanguage(AppLanguage.ENGLISH)
            Thread.sleep(1500)

            for ((idx, test) in testCorpus.withIndex()) {
                Log.i(TAG, "--- [Test ${idx + 1}/30] ${test.language.label} | ${test.domain} ---")

                if (test.language != activeLang) {
                    val tStartSwitch = System.currentTimeMillis()
                    engine.switchLanguage(test.language)
                    activeLang = test.language
                    Thread.sleep(if (test.language == AppLanguage.ENGLISH || activeLang == AppLanguage.ENGLISH) 1200 else 100)
                    val switchLatency = System.currentTimeMillis() - tStartSwitch
                    Log.i(TAG, "Switched STT model to ${test.language.label} in ${switchLatency}ms")
                }

                // Generate audio waveform
                val audioFloatArray = generateAudioForText(this, nativeTts, engine, test.groundTruth, test.language)
                val audioDurationMs = (audioFloatArray.size.toFloat() / 16000.0f * 1000.0f).toLong()

                // STT Inference
                val tStartInfer = System.currentTimeMillis()
                val rawDecoded = engine.decodeAudioForBenchmark(audioFloatArray) ?: ""
                val inferTimeMs = System.currentTimeMillis() - tStartInfer

                val cleanSanitized = engine.sanitizeText(rawDecoded)
                val finalTranscribed = IndicScriptConverter.toTargetScript(cleanSanitized, test.language)

                // Error Metrics
                val wer = computeWER(test.groundTruth, finalTranscribed)
                val cer = computeCER(test.groundTruth, finalTranscribed)
                val accuracy = max(0.0, 100.0 - cer)
                val rtf = if (audioDurationMs > 0) inferTimeMs.toDouble() / audioDurationMs.toDouble() else 0.0

                // TTS Evaluation
                val ttsResult = evaluateTTS(nativeTts, engine, finalTranscribed.ifBlank { test.groundTruth }, test.language)

                Log.i(TAG, "GroundTruth : \"${test.groundTruth}\"")
                Log.i(TAG, "Raw Deva    : \"$rawDecoded\"")
                Log.i(TAG, "Transcribed : \"$finalTranscribed\"")
                Log.i(TAG, "WER: ${"%.2f".format(wer)}% | CER: ${"%.2f".format(cer)}% | Acc: ${"%.2f".format(accuracy)}% | Infer: ${inferTimeMs}ms (RTF: ${"%.3f".format(rtf)}) | TTS: ${ttsResult.engine} [${ttsResult.status}] (${ttsResult.latencyMs}ms)")

                val resObj = JSONObject().apply {
                    put("id", idx + 1)
                    put("language", test.language.name)
                    put("lang_code", test.language.code)
                    put("lang_label", test.language.label)
                    put("domain", test.domain)
                    put("ground_truth", test.groundTruth)
                    put("raw_deva", rawDecoded)
                    put("transcribed", finalTranscribed)
                    put("audio_duration_ms", audioDurationMs)
                    put("inference_time_ms", inferTimeMs)
                    put("rtf", rtf)
                    put("wer", wer)
                    put("cer", cer)
                    put("accuracy", accuracy)
                    put("tts_engine", ttsResult.engine)
                    put("tts_status", ttsResult.status)
                    put("tts_latency_ms", ttsResult.latencyMs)
                }
                resultsArray.put(resObj)
            }

            val jsonOutput = resultsArray.toString(2)
            Log.i(TAG, "BENCHMARK_JSON_OUTPUT_START\n$jsonOutput\nBENCHMARK_JSON_OUTPUT_END")

            try {
                val outFile = File(filesDir, "benchmark_results.json")
                outFile.writeText(jsonOutput)
                Log.i(TAG, "Benchmark results saved to: ${outFile.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Could not write benchmark file: ${e.message}")
            }

        } finally {
            engine.release()
            try { nativeTts?.shutdown() } catch (e: Exception) {}
            Log.i(TAG, "Benchmark execution complete.")
        }
    }

    data class TtsEvalResult(val engine: String, val status: String, val latencyMs: Long)

    private fun evaluateTTS(
        nativeTts: TextToSpeech?,
        engine: SherpaOnnxEngine,
        text: String,
        lang: AppLanguage
    ): TtsEvalResult {
        return when (lang) {
            AppLanguage.ENGLISH, AppLanguage.HINDI -> {
                val t0 = System.currentTimeMillis()
                try {
                    val wav = engine.synthesizePiperWav(text)
                    val lat = System.currentTimeMillis() - t0
                    TtsEvalResult("Piper VITS", if (wav != null && wav.isNotEmpty()) "PASS" else "FAIL", lat)
                } catch (e: Exception) {
                    TtsEvalResult("Piper VITS", "ERROR: ${e.message}", System.currentTimeMillis() - t0)
                }
            }
            else -> {
                if (nativeTts == null) {
                    return TtsEvalResult("Native Android", "UNAVAILABLE", 0)
                }
                val locale = Locale.forLanguageTag("${lang.code}-IN")
                val avail = nativeTts.isLanguageAvailable(locale)
                val statusStr = when (avail) {
                    TextToSpeech.LANG_COUNTRY_AVAILABLE, TextToSpeech.LANG_AVAILABLE, TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> "PASS"
                    TextToSpeech.LANG_MISSING_DATA -> "MISSING_DATA"
                    TextToSpeech.LANG_NOT_SUPPORTED -> "NOT_SUPPORTED"
                    else -> "CODE_$avail"
                }

                val t0 = System.currentTimeMillis()
                val latch = CountDownLatch(1)
                val utteranceId = "bench_${System.currentTimeMillis()}"
                nativeTts.language = locale
                nativeTts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) { latch.countDown() }
                    override fun onDone(id: String?) { latch.countDown() }
                    override fun onError(id: String?) { latch.countDown() }
                })
                nativeTts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
                latch.await(1000, TimeUnit.MILLISECONDS)
                val lat = System.currentTimeMillis() - t0

                TtsEvalResult("Native Android", statusStr, lat)
            }
        }
    }

    private fun generateAudioForText(
        context: Context,
        nativeTts: TextToSpeech?,
        engine: SherpaOnnxEngine,
        text: String,
        lang: AppLanguage
    ): FloatArray {
        if (lang == AppLanguage.ENGLISH || lang == AppLanguage.HINDI) {
            val wavFloats = engine.synthesizePiperWav(text)
            if (wavFloats != null && wavFloats.isNotEmpty()) {
                return wavFloats
            }
        }

        if (nativeTts != null) {
            val locale = Locale.forLanguageTag("${lang.code}-IN")
            nativeTts.language = locale
            val tempWav = File(context.cacheDir, "synth_${lang.code}_${System.currentTimeMillis()}.wav")
            val latch = CountDownLatch(1)
            val uttId = "synth_file_${System.currentTimeMillis()}"

            nativeTts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) { latch.countDown() }
                override fun onError(id: String?) { latch.countDown() }
            })

            val params = Bundle()
            val res = nativeTts.synthesizeToFile(text, params, tempWav, uttId)
            if (res == TextToSpeech.SUCCESS) {
                latch.await(3000, TimeUnit.MILLISECONDS)
                if (tempWav.exists() && tempWav.length() > 44) {
                    val floats = readWavToFloatArray(tempWav)
                    tempWav.delete()
                    if (floats.isNotEmpty()) return floats
                }
            }
        }

        val numSamples = 16000 * 3
        return FloatArray(numSamples) { 0.0f }
    }

    private fun readWavToFloatArray(wavFile: File): FloatArray {
        return try {
            val bytes = wavFile.readBytes()
            if (bytes.size <= 44) return FloatArray(0)
            val numShorts = (bytes.size - 44) / 2
            val floats = FloatArray(numShorts)
            for (i in 0 until numShorts) {
                val b0 = bytes[44 + i * 2].toInt() and 0xFF
                val b1 = bytes[44 + i * 2 + 1].toInt()
                val s = ((b1 shl 8) or b0).toShort()
                floats[i] = s.toFloat() / 32768.0f
            }
            floats
        } catch (e: Exception) {
            FloatArray(0)
        }
    }

    private fun computeCER(groundTruth: String, transcribed: String): Double {
        val gt = normalizeText(groundTruth)
        val tr = normalizeText(transcribed)
        if (gt.isEmpty()) return if (tr.isEmpty()) 0.0 else 100.0
        val dist = levenshtein(gt, tr)
        return (dist.toDouble() / gt.length.toDouble()) * 100.0
    }

    private fun computeWER(groundTruth: String, transcribed: String): Double {
        val gtWords = normalizeText(groundTruth).split("\\s+".toRegex()).filter { it.isNotBlank() }
        val trWords = normalizeText(transcribed).split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (gtWords.isEmpty()) return if (trWords.isEmpty()) 0.0 else 100.0

        val dist = levenshteinList(gtWords, trWords)
        return (dist.toDouble() / gtWords.size.toDouble()) * 100.0
    }

    private fun normalizeText(text: String): String {
        return text.replace(Regex("[।.,?!;:\u0964\u0965]"), "").trim().lowercase()
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j

        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = min(
                    dp[i - 1][j] + 1,
                    min(
                        dp[i][j - 1] + 1,
                        dp[i - 1][j - 1] + cost
                    )
                )
            }
        }
        return dp[a.length][b.length]
    }

    private fun levenshteinList(a: List<String>, b: List<String>): Int {
        val dp = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in 0..a.size) dp[i][0] = i
        for (j in 0..b.size) dp[0][j] = j

        for (i in 1..a.size) {
            for (j in 1..b.size) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = min(
                    dp[i - 1][j] + 1,
                    min(
                        dp[i][j - 1] + 1,
                        dp[i - 1][j - 1] + cost
                    )
                )
            }
        }
        return dp[a.size][b.size]
    }
}
