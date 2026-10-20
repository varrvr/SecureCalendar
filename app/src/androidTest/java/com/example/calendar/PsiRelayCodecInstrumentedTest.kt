package com.example.calendar

import com.example.calendar.crypto.psi.PsiMessage
import com.example.calendar.data.remote.websocket.PsiRelayCodec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.json.JSONObject

class PsiRelayCodecTest {
    @Test
    fun preservesOpaqueBinaryPayloadInServerEnvelope() {
        val payload = byteArrayOf(0, 1, 2, -1, 42)
        val encoded = PsiRelayCodec.encode(PsiMessage("session", "psi_round1", 19, payload))
        // The server authenticates the sender and adds sender_id to its relay envelope.
        val relayed = JSONObject(encoded).put("sender_id", 7L).toString()
        val decoded = PsiRelayCodec.decode(relayed)
        assertEquals("session", decoded.sessionId)
        assertEquals("psi_round1", decoded.type)
        assertEquals(19L, decoded.recipientId)
        assertEquals(7L, decoded.senderId)
        assertArrayEquals(payload, decoded.payload)
    }
}
