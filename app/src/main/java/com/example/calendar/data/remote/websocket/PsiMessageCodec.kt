package com.example.calendar.data.remote.websocket

import android.util.Base64
import com.example.calendar.crypto.psi.PsiMessage
import org.json.JSONObject

/** Matches the relay envelope defined by server/websocket.py. */
object PsiMessageCodec {
    fun encode(message: PsiMessage): String = JSONObject().apply {
        put("type", message.type)
        put("session_id", message.sessionId)
        put("recipient_id", message.recipientId)
        put("payload", Base64.encodeToString(message.payload, Base64.NO_WRAP))
    }.toString()

    fun decode(text: String): PsiMessage {
        val json = JSONObject(text)
        return PsiMessage(
            sessionId = json.getString("session_id"),
            type = json.getString("type"),
            recipientId = json.getLong("recipient_id"),
            payload = Base64.decode(json.getString("payload"), Base64.NO_WRAP),
            senderId = json.optLong("sender_id").takeIf { json.has("sender_id") }
        )
    }
}
