package com.example.calendar

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.calendar.crypto.Bee2HashToGroup
import com.example.calendar.crypto.psi.*
import java.time.Instant
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Bee2OprfProtocolTest {
    private fun hex(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    @Test fun fullHashToGroupMatchesIndependentReference() {
        assertArrayEquals(hex("6707703d4ccceccb122df210b26cd00c9c3e31d64acad8e39bd12196ead27cdc545507c1fd48c45eab24b4a46655c31c9a8e40a460325c4cd2a5be9d664231e3"),
            Bee2HashToGroup().map("calendar-slot-vector".encodeToByteArray(),
                (Bee2OprfComparison.PROFILE + "/H1").encodeToByteArray()))
    }
    @Test fun actualBee2OprfHandlesEmptyPartialAndFullIntersectionForBothPeers() = runBlocking<Unit> {
        withTimeout(60000) {
            for ((a,b,expected) in listOf(
                Triple(emptyList<Long>(), listOf(100L), emptyList<Long>()),
                Triple(listOf(100L), emptyList<Long>(), emptyList<Long>()),
                Triple(listOf(100L,101L,102L), listOf(101L,102L,103L), listOf(101L,102L)),
                Triple(listOf(100L,101L), listOf(100L,101L), listOf(100L,101L)),
                Triple(listOf(100L), listOf(200L), emptyList<Long>())
            )) {
                val toA = Channel<PsiMessage>(64); val toB = Channel<PsiMessage>(64)
                val sa = PsiSession(localUserId=1,peerUserId=2,role=PsiRole.INITIATOR,
                    periodStart=Instant.EPOCH,periodEnd=Instant.ofEpochSecond(900000))
                val sb = sa.copy(localUserId=2,peerUserId=1,role=PsiRole.RESPONDER)
                coroutineScope {
                    val ra = async { Bee2OprfComparison().run(sa,a,{toB.send(it.copy(senderId=1))},{toA.receive()}) }
                    val rb = async { Bee2OprfComparison().run(sb,b,{toA.send(it.copy(senderId=2))},{toB.receive()}) }
                    val times = expected.map { Instant.ofEpochSecond(it*900) }
                    assertEquals(times,ra.await().intersections)
                    assertEquals(times,rb.await().intersections)
                }
                toA.cancel();toB.cancel()
            }
        }
    }
}
