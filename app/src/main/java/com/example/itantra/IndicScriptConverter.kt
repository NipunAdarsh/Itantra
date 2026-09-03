package com.example.itantra

import android.util.Log

object IndicScriptConverter {
    private const val TAG = "IndicScriptConverter"

    /**
     * Converts phonetic Devanagari text (output by the shared multilingual IndicConformer
     * CTC fallback) to the target language's native Brahmic Unicode script.
     *
     * Kannada, Telugu and Tamil are excluded: they run on dedicated per-language
     * IndicConformer checkpoints (see SherpaOnnxEngine.DEDICATED_INDIC_MODEL_DIRS) that
     * emit native-script tokens directly, so remapping here would be a no-op at best and
     * a corruption risk at worst if applied to already-correct text.
     */
    fun toTargetScript(text: String, targetLang: AppLanguage): String {
        if (text.isBlank()) return text

        return when (targetLang) {
            AppLanguage.ENGLISH, AppLanguage.HINDI, AppLanguage.MARATHI,
            AppLanguage.KANNADA, AppLanguage.TELUGU, AppLanguage.TAMIL -> text
            AppLanguage.BENGALI -> convertBlock(text, 0x0080)
            AppLanguage.GUJARATI -> convertBlock(text, 0x0180)
            AppLanguage.ODIA -> convertBlock(text, 0x0200)
            AppLanguage.MALAYALAM -> convertBlock(text, 0x0400)
        }
    }

    /**
     * Standard Brahmic block shift for scripts that share 1-to-1 Unicode structure with Devanagari
     * (Devanagari is base \u0900..\u097F).
     */
    private fun convertBlock(text: String, offset: Int): String {
        val sb = StringBuilder(text.length)
        for (i in 0 until text.length) {
            val code = text[i].code
            if (code in 0x0900..0x097F) {
                sb.append((code + offset).toChar())
            } else {
                sb.append(text[i])
            }
        }
        return sb.toString()
    }
}
