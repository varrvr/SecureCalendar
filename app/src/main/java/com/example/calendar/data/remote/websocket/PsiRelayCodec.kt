package com.example.calendar.data.remote.websocket

import com.example.calendar.crypto.psi.PsiMessage
import android.util.Base64
import org.json.JSONObject

/** Encodes opaque PSI protocol bytes inside the Python server's existing JSON relay envelope. */
object PsiRelayCodec {
    fun encode(message: PsiMessage): String = JSONObject()
        .put("type", message.type)
        .put("session_id", message.sessionId)
        .put("recipient_id", message.recipientId)
        .put("payload", Base64.encodeToString(message.payload, Base64.NO_WRAP))
        .toString()

    fun decode(text: String): PsiMessage {
        val json = JSONObject(text)
        val type = json.getString("type")
        require(type.startsWith("psi_")) { "Unexpected relay message type" }
        val payload = Base64.decode(json.getString("payload"), Base64.NO_WRAP)
        require(payload.size <= MAX_PAYLOAD_BYTES) { "PSI payload is too large" }
        return PsiMessage(
            sessionId = json.getString("session_id"),
            type = type,
            recipientId = json.getLong("recipient_id"),
            payload = payload,
            senderId = json.optLong("sender_id").takeIf { json.has("sender_id") }
        )
    }

    private const val MAX_PAYLOAD_BYTES = 1_500_000
}
