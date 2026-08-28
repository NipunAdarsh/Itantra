package com.example.app.model

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
 * | [lang]     | `"lng"`  | `"en"`                 |
 * | [isAlert]  | `"alt"`  | `true`                 |
 * | [timestamp]| `"ts"`   | `1724865600000`        |
 *
 * Example packet (51 bytes vs 87 bytes with verbose keys — a ~41 % saving):
 * ```
 * {"id":"a3f9","txt":"Hello","lng":"en","alt":false,"ts":1724865600000}
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
        put("id",  msgId)
        put("txt", text)
        put("lng", lang)
        put("alt", isAlert)
        put("ts",  timestamp)
    }.toString()

    companion object {
        /**
         * Parses a compact JSON string (produced by [toJson]) into a [NetworkMessage].
         *
         * @throws org.json.JSONException if any required key is absent or has the wrong type.
         */
        fun fromJson(jsonStr: String): NetworkMessage {
            val j = JSONObject(jsonStr)
            return NetworkMessage(
                msgId     = j.getString("id"),
                text      = j.getString("txt"),
                lang      = j.getString("lng"),
                isAlert   = j.getBoolean("alt"),
                timestamp = j.getLong("ts")
            )
        }
    }
}
