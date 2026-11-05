package com.yourname.nib

import com.yourname.nib.core.db.Conflict
import com.yourname.nib.core.db.OpLog
import com.yourname.nib.core.sync.PullResult
import com.yourname.nib.core.sync.PurgeTarget
import com.yourname.nib.core.sync.SyncEngine
import com.yourname.nib.core.sync.SyncStore
import com.yourname.nib.core.sync.SyncTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun engineOp(
    id: String,
    hlc: String,
    field: String = "title",
    value: String? = "v",
    device: String = "devA",
    entityId: String = "e1",
    entityType: String = "entry",
    seq: Long? = null,
) = OpLog(id, entityType, entityId, field, value, hlc, device, seq)

private fun engineStamp(n: Long, device: String = "devA") = "%016d-0000-%s".format(n, device)

private class FakeTransport(initial: List<OpLog> = emptyList()) : SyncTransport {
    val remote = initial.toMutableList()
    val pushed = mutableListOf<OpLog>()
    val pullSince = mutableListOf<Long>()
    var deviceCursors = emptyMap<String, Long>()
    var failPushTimes = 0
    private var seq = initial.mapNotNull { it.serverSeq }.maxOrNull() ?: 0L

    override fun push(ops: List<OpLog>): List<Pair<String, Long>> {
        if (failPushTimes > 0) {
            failPushTimes--
            throw IllegalStateException("network")
        }
        val assignments = ops.map { op -> ++seq to op }.map { (assigned, op) -> op.id to assigned }
        ops.forEachIndexed { index, op -> remote.add(op.copy(serverSeq = assignments[index].second)) }
        pushed.addAll(ops)
        return assignments
    }

    override fun pull(since: Long, limit: Int): PullResult {
        pullSince.add(since)
        val page = remote.filter { (it.serverSeq ?: 0L) > since }.take(limit)
        return PullResult(page, remote.mapNotNull { it.serverSeq }.maxOrNull() ?: since, deviceCursors)
    }
}

private class FakeStore(initial: List<OpLog> = emptyList()) : SyncStore {
    val pendingOps = initial.toMutableList()
    val applied = mutableListOf<OpLog>()
    val conflicts = mutableListOf<Conflict>()
    val purged = mutableListOf<PurgeTarget>()
    var cursor = 0L

    override fun pending(): List<OpLog> = pendingOps.toList()

    override fun markPushed(assignments: List<Pair<String, Long>>) {
        assignments.forEach { (id, seq) ->
            val index = pendingOps.indexOfFirst { it.id == id }
            if (index >= 0) pendingOps[index] = pendingOps[index].copy(serverSeq = seq)
        }
    }

    override fun apply(winners: List<OpLog>, conflicts: List<Conflict>, purged: List<PurgeTarget>) {
        applied.addAll(winners)
        this.conflicts.addAll(conflicts)
        this.purged.addAll(purged)
    }

    override fun cursor(): Long = cursor

    override fun saveCursor(seq: Long) {
        cursor = seq
    }
}

class SyncEngineTest {
    @Test
    fun twoDevicesEditingDifferentFieldsBothSurvive() {
        val local = engineOp("l1", engineStamp(1), "title", "本地标题", "devA")
        val remoteOp = engineOp("r1", engineStamp(2), "done", "1", "devB", seq = 1L)
        val transport = FakeTransport(listOf(remoteOp))
        val store = FakeStore(listOf(local))
        val report = SyncEngine(transport, store, now = { 100L }).sync()

        assertEquals(1, report.pushed)
        assertEquals(1, report.pulled)
        assertEquals(0, report.conflicts)
        assertEquals("本地标题", store.applied.first { it.field == "title" }.value)
        assertEquals("1", store.applied.first { it.field == "done" }.value)
        assertTrue(store.conflicts.isEmpty())
        assertEquals(2L, store.cursor)
    }

    @Test
    fun sameFieldConflictKeepsLoserText() {
        val local = engineOp("l1", engineStamp(1), "title", "先改", "devA")
        val remoteOp = engineOp("r1", engineStamp(9), "title", "后改", "devB", seq = 1L)
        val store = FakeStore(listOf(local))
        val report = SyncEngine(FakeTransport(listOf(remoteOp)), store, now = { 100L }).sync()

        assertEquals(1, report.conflicts)
        assertEquals("后改", store.applied.single { it.field == "title" }.value)
        assertEquals("先改", store.conflicts.single().loserValue)
    }

    @Test
    fun ownPushedOpEchoDoesNotCreateConflict() {
        val local = engineOp("l1", engineStamp(1), "title", "同一个", "devA")
        val store = FakeStore(listOf(local))
        val report = SyncEngine(FakeTransport(), store, now = { 100L }).sync()

        assertEquals(0, report.pulled)
        assertEquals(0, report.conflicts)
        assertEquals(1, store.applied.count { it.field == "title" })
    }

    @Test
    fun pushFailureRetriesWithBackoff() {
        val sleeps = mutableListOf<Long>()
        val transport = FakeTransport().apply { failPushTimes = 2 }
        val store = FakeStore(listOf(engineOp("l1", engineStamp(1))))
        val report = SyncEngine(transport, store, now = { 100L }, sleep = { sleeps.add(it) }).sync()

        assertEquals(1, report.pushed)
        assertEquals(listOf(1_000L, 2_000L), sleeps)
        assertEquals(1L, store.pendingOps.single().serverSeq)
        assertEquals(1, transport.pushed.size)
    }

    @Test
    fun cursorIsBreakpointForNextSync() {
        val transport = FakeTransport(listOf(engineOp("r1", engineStamp(1), seq = 4L)))
        val store = FakeStore()
        val engine = SyncEngine(transport, store, now = { 100L })
        engine.sync()
        assertEquals(4L, store.cursor)

        transport.pullSince.clear()
        engine.sync()
        assertEquals(4L, transport.pullSince.first())
    }

    @Test
    fun tombstonesPurgedOnlyWhenAllDevicesCaughtUp() {
        val delete = engineOp("d1", engineStamp(1), "deleted_at", "500", "devA", entityId = "e2", seq = 3L)
        val transport = FakeTransport(listOf(delete)).apply {
            deviceCursors = mapOf("devA" to 3L, "devB" to 3L)
        }
        val store = FakeStore()
        val report = SyncEngine(transport, store, now = { 10_000L }).sync()

        assertEquals(1, report.purged)
        assertEquals("e2", store.purged.single().entityId)
        assertEquals("entry", store.purged.single().entityType)
    }

    @Test
    fun noDeviceCursorsMeansNoPurge() {
        val delete = engineOp("d1", engineStamp(1), "deleted_at", "500", "devA", entityId = "e2", seq = 3L)
        val store = FakeStore()
        val report = SyncEngine(FakeTransport(listOf(delete)), store, now = { 10_000L }).sync()
        assertEquals(0, report.purged)
        assertTrue(store.purged.isEmpty())
    }
}
