package com.yourname.nib

import com.yourname.nib.core.db.OpLog
import com.yourname.nib.core.sync.PULL_PAGE_SIZE
import com.yourname.nib.core.sync.TOMBSTONE_GRACE_MS
import com.yourname.nib.core.sync.mergeOps
import com.yourname.nib.core.sync.nextCursor
import com.yourname.nib.core.sync.purgeCandidates
import com.yourname.nib.core.sync.pushBatches
import com.yourname.nib.core.sync.retryDelay
import com.yourname.nib.core.sync.tombstonesFor
import com.yourname.nib.core.sync.withRetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

private fun op(
    id: String,
    hlc: String,
    field: String = "title",
    value: String? = "v",
    device: String = "devA",
    entityId: String = "e1",
    entityType: String = "entry",
    seq: Long? = null,
) = OpLog(id, entityType, entityId, field, value, hlc, device, seq)

private fun stamp(n: Long, device: String = "devA") = "%016d-0000-%s".format(n, device)

class SyncPlanTest {
    @Test
    fun differentFieldsBothSurviveMerge() {
        val result = mergeOps(
            listOf(
                op("a", stamp(1), "title", "本地标题", "devA"),
                op("b", stamp(2), "done", "1", "devB"),
            ),
            100L,
        )
        assertEquals(2, result.winners.size)
        assertTrue(result.conflicts.isEmpty())
        assertEquals("本地标题", result.winners.first { it.field == "title" }.value)
        assertEquals("1", result.winners.first { it.field == "done" }.value)
    }

    @Test
    fun sameFieldHlcWinsAndLoserKeptInConflict() {
        val result = mergeOps(
            listOf(
                op("a", stamp(1), "title", "先改", "devA"),
                op("b", stamp(9), "title", "后改", "devB"),
            ),
            100L,
        )
        val winner = result.winners.single()
        assertEquals("后改", winner.value)
        assertEquals("devB", winner.deviceId)
        assertEquals(1, result.conflicts.size)
        assertEquals("先改", result.conflicts.single().loserValue)
        assertEquals("devA", result.conflicts.single().deviceId)
        assertEquals(stamp(9), result.conflicts.single().winnerHlc)
    }

    @Test
    fun sameDeviceRepeatedWritesDoNotCreateConflict() {
        val result = mergeOps(
            listOf(
                op("a", stamp(1), "title", "旧", "devA"),
                op("b", stamp(2), "title", "新", "devA"),
            ),
            100L,
        )
        assertTrue(result.conflicts.isEmpty())
        assertEquals("新", result.winners.single().value)
    }

    @Test
    fun deleteWithLaterEditDefersTombstoneSevenDays() {
        val now = 1_000L
        val ops = listOf(
            op("d", stamp(1), "deleted_at", "500", "devA"),
            op("e", stamp(2), "title", "另一台改的", "devB"),
        )
        val tombstone = tombstonesFor(ops, now).single()
        assertTrue(tombstone.deferred)
        assertEquals(now + TOMBSTONE_GRACE_MS, tombstone.effectiveAt)
        val merged = mergeOps(ops, now)
        assertEquals((now + TOMBSTONE_GRACE_MS).toString(), merged.winners.first { it.field == "deleted_at" }.value)
        assertFalse(merged.winners.any { it.field == "deleted_at" && it.value == "500" })
    }

    @Test
    fun deleteWithoutConcurrentEditTakesEffectImmediately() {
        val decision = tombstonesFor(listOf(op("d", stamp(1), "deleted_at", "500")), 1_000L).single()
        assertFalse(decision.deferred)
        assertEquals(500L, decision.effectiveAt)
    }

    @Test
    fun undeleteClearsTombstone() {
        val decision = tombstonesFor(
            listOf(
                op("d", stamp(1), "deleted_at", "500"),
                op("u", stamp(2), "deleted_at", null),
            ),
            1_000L,
        ).single()
        assertNull(decision.effectiveAt)
        assertFalse(decision.deferred)
    }

    @Test
    fun purgeNeedsEveryDeviceCursorPastServerSeq() {
        val ops = listOf(op("d", stamp(1), "deleted_at", "500", seq = 7L))
        assertTrue(purgeCandidates(ops, emptyMap(), 2_000L).isEmpty())
        assertTrue(purgeCandidates(ops, mapOf("devA" to 7L, "devB" to 6L), 2_000L).isEmpty())
        assertEquals(1, purgeCandidates(ops, mapOf("devA" to 7L, "devB" to 9L), 2_000L).size)
    }

    @Test
    fun purgeWaitsForGraceAndServerSeq() {
        val now = 1_000L
        val ops = listOf(
            op("d", stamp(1), "deleted_at", "500", seq = 7L),
            op("e", stamp(2), "title", "改", "devB", seq = 8L),
        )
        assertTrue(purgeCandidates(ops, mapOf("devA" to 9L, "devB" to 9L), now).isEmpty())
        assertTrue(purgeCandidates(listOf(ops[0].copy(serverSeq = null)), mapOf("devA" to 9L), now).isEmpty())
    }

    @Test
    fun pushBatchesAreHlcOrderedAndChunked() {
        val ops = (3 downTo 1).map { op("o$it", stamp(it.toLong())) }
        val batches = pushBatches(ops, 2)
        assertEquals(2, batches.size)
        assertEquals(listOf("o1", "o2"), batches[0].map { it.id })
        assertEquals(listOf("o3"), batches[1].map { it.id })
        assertEquals(PULL_PAGE_SIZE, 500)
    }

    @Test
    fun nextCursorUsesLargestServerSeq() {
        assertEquals(9L, nextCursor(4L, listOf(op("a", stamp(1), seq = 7L), op("b", stamp(2), seq = 9L))))
        assertEquals(4L, nextCursor(4L, listOf(op("a", stamp(1)))))
    }

    @Test
    fun retryDelayDoublesAndCaps() {
        assertEquals(1_000L, retryDelay(0))
        assertEquals(2_000L, retryDelay(1))
        assertEquals(8_000L, retryDelay(3))
        assertEquals(60_000L, retryDelay(30))
    }

    @Test
    fun withRetryRetriesAndRecordsBackoff() {
        val sleeps = mutableListOf<Long>()
        var calls = 0
        val value = withRetry(sleep = { sleeps.add(it) }) { attempt ->
            calls++
            if (attempt < 2) throw IllegalStateException("flaky")
            "ok"
        }
        assertEquals("ok", value)
        assertEquals(3, calls)
        assertEquals(listOf(1_000L, 2_000L), sleeps)
    }

    @Test
    fun withRetryThrowsAfterExhaustion() {
        try {
            withRetry(maxAttempts = 3, sleep = {}) { throw IllegalStateException("down") }
            fail("should throw")
        } catch (e: IllegalStateException) {
            assertEquals("down", e.message)
        }
    }
}
