package com.example.itantra

import android.util.Log

object IndicScriptConverter {
    private const val TAG = "IndicScriptConverter"

    /**
     * Converts phonetic Devanagari text (output by multilingual IndicConformer CTC)
     * to the target language's native Brahmic Unicode script.
     */
    fun toTargetScript(text: String, targetLang: AppLanguage): String {
        if (text.isBlank()) return text

        return when (targetLang) {
            AppLanguage.ENGLISH, AppLanguage.HINDI, AppLanguage.MARATHI -> text
            AppLanguage.BENGALI -> convertBlock(text, 0x0080)
            AppLanguage.GUJARATI -> convertBlock(text, 0x0180)
            AppLanguage.ODIA -> convertBlock(text, 0x0200)
            AppLanguage.TAMIL -> convertToTamil(text)
            AppLanguage.TELUGU -> convertBlock(text, 0x0300)
            AppLanguage.KANNADA -> convertBlock(text, 0x0380)
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

    /**
     * Tamil has a reduced consonant inventory in Unicode (unassigned aspirated and voiced stops).
     * We map unassigned code points to their corresponding base voiceless stops before shifting.
     */
    private fun convertToTamil(text: String): String {
        val sb = StringBuilder(text.length)
        for (i in 0 until text.length) {
            var code = text[i].code
            if (code in 0x0900..0x097F) {
                // Map Devanagari consonants that don't exist in Tamil to their base Tamil equivalents
                code = when (code) {
                    0x0916, 0x0917, 0x0918 -> 0x0915 // kh, g, gh -> k (க)
                    0x091B, 0x091D -> 0x091A       // ch, jh -> c (ச)
                    0x0920, 0x0921, 0x0922 -> 0x091F // th, d, dh (retroflex) -> t (ட)
                    0x0925, 0x0926, 0x0927 -> 0x0924 // th, d, dh (dental) -> t (த)
                    0x092B, 0x092C, 0x092D -> 0x092A // ph, b, bh -> p (ப)
                    0x0936 -> 0x0938               // sh -> s (ஸ)
                    0x0937 -> 0x0937               // ss -> ss (ஷ)
                    else -> code
                }
                sb.append((code + 0x0280).toChar())
            } else {
                sb.append(text[i])
            }
        }
        return sb.toString()
    }
}
