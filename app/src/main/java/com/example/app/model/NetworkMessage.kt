package com.example.app.model

import org.json.JSONObject

/**
 * Data model for peer-to-peer network communication.
 * Provides built-in serialization using [JSONObject].
 */
data class NetworkMessage(
    val msgId: String,
    val text: String,
    val lang: String,
    val isAlert: Boolean,
    val timestamp: Long
) {
    /**
     * Serializes the message to a JSON string.
     */
    fun toJson(): String {
        val json = JSONObject()
        json.put("msgId", msgId)
        json.put("text", text)
        json.put("lang", lang)
        json.put("isAlert", isAlert)
        json.put("timestamp", timestamp)
        return json.toString()
    }

    companion object {
        /**
         * Parses a JSON string into a [NetworkMessage] instance.
         * @throws org.json.JSONException if parsing fails.
         */
        fun fromJson(jsonStr: String): NetworkMessage {
            val json = JSONObject(jsonStr)
            return NetworkMessage(
                msgId = json.getString("msgId"),
                text = json.getString("text"),
                lang = json.getString("lang"),
                isAlert = json.getBoolean("isAlert"),
                timestamp = json.getLong("timestamp")
            )
        }
    }
}
