package com.example.itantra

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

class TtsManager(
    private val context: Context,
    private val onVoiceUnavailable: ((AppLanguage) -> Unit)? = null,
    private val speakPiper: (text: String, lang: AppLanguage, isAlert: Boolean) -> Unit
) : TextToSpeech.OnInitListener {

    private val TAG = "TtsManager"
    private var androidTts: TextToSpeech? = null
    @Volatile private var isAndroidTtsReady = false

    init {
        try {
            androidTts = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Android TextToSpeech", e)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isAndroidTtsReady = true
            Log.i(TAG, "Native Android TextToSpeech engine initialized successfully")
            androidTts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d(TAG, "Android TTS started: $utteranceId")
                }
                override fun onDone(utteranceId: String?) {
                    Log.d(TAG, "Android TTS finished: $utteranceId")
                }
                override fun onError(utteranceId: String?) {
                    Log.e(TAG, "Android TTS error on utterance: $utteranceId")
                }
            })
        } else {
            isAndroidTtsReady = false
            Log.e(TAG, "Native Android TextToSpeech init failed with code $status")
        }
    }

    fun speak(text: String, targetLanguage: AppLanguage) {
        val isAlert = text.startsWith("[ALERT]")
        val cleanText = if (isAlert) text.removePrefix("[ALERT]").trim() else text.trim()
        if (cleanText.isBlank()) return

        when (targetLanguage) {
            AppLanguage.ENGLISH, AppLanguage.HINDI -> {
                // Route to ultra-low-latency neural Piper VITS
                Log.d(TAG, "Routing to Piper VITS for ${targetLanguage.label}: \"$cleanText\"")
                speakPiper(cleanText, targetLanguage, isAlert)
            }
            else -> {
                // Route to Android native TextToSpeech engine (0 MB APK overhead)
                speakNative(cleanText, targetLanguage, isAlert)
            }
        }
    }

    private fun speakNative(text: String, lang: AppLanguage, isAlert: Boolean) {
        val tts = androidTts
        if (!isAndroidTtsReady || tts == null) {
            Log.w(TAG, "Android TTS not ready, falling back to Hindi Piper")
            speakPiper(text, AppLanguage.HINDI, isAlert)
            return
        }

        val locale = getLocaleForLanguage(lang)
        val availability = tts.isLanguageAvailable(locale)

        if (availability == TextToSpeech.LANG_NOT_SUPPORTED || availability == TextToSpeech.LANG_MISSING_DATA) {
            Log.w(TAG, "Language ${lang.label} ($locale) unavailable in Android TTS (code=$availability). Falling back to Hindi Piper.")
            onVoiceUnavailable?.invoke(lang)
            speakPiper(text, AppLanguage.HINDI, isAlert)
            return
        }

        try {
            tts.language = locale

            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (isAlert) {
                val maxAlarm = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxAlarm, AudioManager.FLAG_SHOW_UI)

                val alertAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                tts.setAudioAttributes(alertAttributes)
            } else {
                val mediaAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                tts.setAudioAttributes(mediaAttributes)
            }

            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                if (isAlert) {
                    putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_ALARM)
                }
            }

            val utteranceId = "tts_${System.currentTimeMillis()}"
            val res = tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            Log.i(TAG, "Android TTS dispatched \"$text\" for ${lang.label} (result=$res)")
        } catch (e: Exception) {
            Log.e(TAG, "Error speaking via Android TTS for ${lang.label}", e)
            speakPiper(text, AppLanguage.HINDI, isAlert)
        }
    }

    private fun getLocaleForLanguage(lang: AppLanguage): Locale {
        return when (lang) {
            AppLanguage.ENGLISH -> Locale.US
            AppLanguage.HINDI -> Locale.forLanguageTag("hi-IN")
            AppLanguage.GUJARATI -> Locale.forLanguageTag("gu-IN")
            AppLanguage.MARATHI -> Locale.forLanguageTag("mr-IN")
            AppLanguage.KANNADA -> Locale.forLanguageTag("kn-IN")
            AppLanguage.MALAYALAM -> Locale.forLanguageTag("ml-IN")
            AppLanguage.TAMIL -> Locale.forLanguageTag("ta-IN")
            AppLanguage.TELUGU -> Locale.forLanguageTag("te-IN")
            AppLanguage.ODIA -> Locale.forLanguageTag("or-IN")
            AppLanguage.BENGALI -> Locale.forLanguageTag("bn-IN")
        }
    }

    fun release() {
        try {
            androidTts?.stop()
            androidTts?.shutdown()
            androidTts = null
            isAndroidTtsReady = false
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down Android TTS", e)
        }
    }
}
