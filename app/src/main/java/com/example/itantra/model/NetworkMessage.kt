package com.example.itantra.model

import org.json.JSONObject

/**
 * Data model for peer-to-peer network communication.
 *
 * ## Wire format (compact keys)
 * To minimise per-packet overhead — especially relevant when short phrases are
 * sent at high frequency — the JSON payload uses abbreviated keys:
 *
 * | Field      | Wire key | Example value          |
 * |------------|----------|------------------------|
 * | [msgId]    | `"id"`   | `"a3f9"`               |
 * | [text]     | `"txt"`  | `"Hello, world"`       |
 * | [lang]     | `"lang"` | `"en"`                 |
 * | [isAlert]  | `"alt"`  | `true`                 |
 * | [timestamp]| `"ts"`   | `1724865600000`        |
 *
 * Example packet:
 * ```
 * {"id":"a3f9","txt":"Hello","lang":"en","alt":false,"ts":1724865600000}
 * ```
 *
 * Serialization is handled by [toJson] / [fromJson] so all other code remains
 * unaffected by the key naming scheme.
 */
data class NetworkMessage(
    val msgId: String,
    val text: String,
    val lang: String,
    val isAlert: Boolean,
    val timestamp: Long
) {
    /**
     * Serializes this message to a compact JSON string using abbreviated wire keys.
     * The output is a single line with no trailing newline (callers append one if needed).
     */
    fun toJson(): String = JSONObject().apply {
        put("id",   msgId)
        put("txt",  text)
        put("lang", lang)
        put("alt",  isAlert)
        put("ts",   timestamp)
    }.toString()

    companion object {
        /**
         * Parses a compact JSON string (produced by [toJson]) into a [NetworkMessage].
         *
         * @param jsonStr The raw JSON payload received over the wire.
         * @return The deserialized [NetworkMessage], or `null` if the payload is malformed
         *         or missing required keys.
         */
        fun fromJson(jsonStr: String): NetworkMessage? {
            return try {
                val j = JSONObject(jsonStr)
                NetworkMessage(
                    msgId     = j.getString("id"),
                    text      = j.getString("txt"),
                    lang      = j.getString("lang"),
                    isAlert   = j.getBoolean("alt"),
                    timestamp = j.getLong("ts")
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
