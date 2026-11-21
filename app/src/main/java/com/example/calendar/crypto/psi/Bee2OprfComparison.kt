package com.example.calendar.crypto.psi

import android.util.Base64
import com.example.calendar.crypto.*
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.time.Instant
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import org.json.JSONArray
import org.json.JSONObject

/** Semi-honest OPRF-PSI. Authentication of the trusted relay requires TLS in MITM mode.
 * Only points/PRF outputs travel over the relay; matched outputs give both peers the result.
 */
class Bee2OprfComparison {
    companion object { const val PROFILE = "SC-OPRF-PSI-v1-BIGN128-XMD-BELT-SSWU-RO" }
    private val bee2 = Bee2Crypto()
    private val mapper = Bee2HashToGroup(bee2)
    private val group = Bee2OprfPointOperations()
    suspend fun run(session: PsiSession, localSlots: List<Long>,
                    send: suspend (PsiMessage) -> Unit, receive: suspend () -> PsiMessage): PsiResult {
        val initiator = if (session.role == PsiRole.INITIATOR) session.localUserId else session.peerUserId
        val responder = if (session.role == PsiRole.RESPONDER) session.localUserId else session.peerUserId
        val context = ByteArrayOutputStream().also { bytes -> DataOutputStream(bytes).use {
            it.writeUTF(PROFILE); it.writeUTF(session.id); it.writeLong(initiator); it.writeLong(responder)
            it.writeLong(session.periodStart.epochSecond); it.writeLong(session.periodEnd.epochSecond)
        } }.toByteArray()
        val contextHash = encode(bee2.hash(context))
        val domain = (PROFILE + "/H1").encodeToByteArray()
        val slots = localSlots.distinct().sorted()
        fun input(slot: Long) = context + SlotConverter.encodeSlot(slot)
        fun token(slot: Long, point: ByteArray) = bee2.hash((PROFILE + "/Finalize").encodeToByteArray() + input(slot) + point)
        suspend fun write(phase: String, values: List<ByteArray>) {
            val chunks = if (values.isEmpty()) listOf(emptyList()) else values.chunked(1024)
            chunks.forEachIndexed { seq, chunk ->
                coroutineContext.ensureActive()
                val body = JSONObject().put("profile", PROFILE).put("context", contextHash)
                    .put("phase", phase).put("seq", seq).put("last", seq == chunks.lastIndex)
                    .put("items", JSONArray(chunk.map(::encode)))
                send(PsiMessage(session.id, "psi_oprf", session.peerUserId, body.toString().encodeToByteArray()))
            }
        }
        suspend fun read(phase: String, size: Int): List<ByteArray> {
            val values = mutableListOf<ByteArray>(); var seq = 0
            while (true) {
                val packet = receive()
                require(packet.sessionId == session.id && packet.senderId == session.peerUserId && packet.recipientId == session.localUserId)
                require(packet.type == "psi_oprf")
                val body = JSONObject(packet.payload.toString(Charsets.UTF_8))
                require(body.getString("profile") == PROFILE && body.getString("context") == contextHash)
                require(body.getString("phase") == phase && body.getInt("seq") == seq++)
                val items = body.getJSONArray("items")
                require(items.length() <= 1024)
                for (i in 0 until items.length()) {
                    val item = Base64.decode(items.getString(i), Base64.NO_WRAP)
                    require(item.size == size)
                    values += item
                }
                if (body.getBoolean("last")) return values
            }
        }
        write("ready", emptyList()); require(read("ready", 0).isEmpty())
        fun selectionHash(values: List<ByteArray>): ByteArray {
            val bytes = ByteArray(values.size * 32)
            values.forEachIndexed { i, value -> value.copyInto(bytes, i * 32) }
            return try { bee2.hash(bytes) } finally { bytes.fill(0) }
        }
        val matches: List<Long>
        if (session.role == PsiRole.RESPONDER) {
            val key = bee2.generateScalar()
            try {
                val own = linkedMapOf<String, Long>()
                for (slot in slots) {
                    coroutineContext.ensureActive()
                    own[encode(token(slot, group.evaluate(mapper.map(input(slot), domain), key)))] = slot
                }
                // Sorting hashes avoids disclosing the responder's chronological order.
                val outputs = own.keys.sorted().map { Base64.decode(it, Base64.NO_WRAP) }
                write("outputs", outputs)
                val requests = read("query", 64)
                val evaluated = requests.map { coroutineContext.ensureActive(); group.evaluate(it, key) }
                write("evaluated", evaluated)
                val selected = read("matches", 32)
                require(selected.map(::encode).distinct().size == selected.size)
                matches = selected.map { requireNotNull(own[encode(it)]) }.sorted()
                write("ack", listOf(selectionHash(selected)))
            } finally { key.fill(0) }
        } else {
            val outputs = read("outputs", 32).map(::encode).toHashSet()
            val blinds = mutableListOf<ByteArray>()
            try {
                val query = slots.map { slot ->
                    coroutineContext.ensureActive()
                    val r = bee2.generateScalar(); blinds += r
                    group.blind(mapper.map(input(slot), domain), r)
                }
                write("query", query)
                val evaluated = read("evaluated", 64)
                require(evaluated.size == slots.size)
                val selected = mutableListOf<ByteArray>(); val result = mutableListOf<Long>()
                evaluated.forEachIndexed { i, point ->
                    coroutineContext.ensureActive()
                    val value = token(slots[i], group.unblind(point, blinds[i]))
                    if (encode(value) in outputs) { result += slots[i]; selected += value }
                }
                write("matches", selected)
                val ack = read("ack", 32)
                require(ack.size == 1 && java.security.MessageDigest.isEqual(ack.single(), selectionHash(selected)))
                matches = result
            } finally { blinds.forEach { it.fill(0) } }
        }
        return PsiResult(matches.map(SlotConverter::toInstant), Instant.now())
    }
    private fun encode(value: ByteArray) = Base64.encodeToString(value, Base64.NO_WRAP)
}
