package com.yourname.nib.core.sync

import com.yourname.nib.core.db.Conflict
import com.yourname.nib.core.db.OpLog

data class PullResult(
    val ops: List<OpLog>,
    val cursor: Long,
    val deviceCursors: Map<String, Long> = emptyMap(),
)

interface SyncTransport {
    fun push(ops: List<OpLog>): List<Pair<String, Long>>
    fun pull(since: Long, limit: Int): PullResult
}

interface SyncStore {
    fun pending(): List<OpLog>
    fun markPushed(assignments: List<Pair<String, Long>>)
    fun apply(winners: List<OpLog>, conflicts: List<Conflict>, purged: List<PurgeTarget>)
    fun cursor(): Long
    fun saveCursor(seq: Long)
}

data class SyncReport(
    val pushed: Int,
    val pulled: Int,
    val conflicts: Int,
    val purged: Int,
    val cursor: Long,
)

class SyncEngine(
    private val transport: SyncTransport,
    private val store: SyncStore,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val sleep: (Long) -> Unit = {},
    private val maxAttempts: Int = 5,
) {
    fun sync(): SyncReport {
        val pending = store.pending()
        for (batch in pushBatches(pending)) {
            val assignments = withRetry(maxAttempts, sleep) { transport.push(batch) }
            store.markPushed(assignments)
        }
        var cursor = store.cursor()
        val pulled = mutableListOf<OpLog>()
        var deviceCursors = emptyMap<String, Long>()
        var guard = 0
        while (guard++ < 20) {
            val result = withRetry(maxAttempts, sleep) { transport.pull(cursor, PULL_PAGE_SIZE) }
            pulled.addAll(result.ops)
            deviceCursors = result.deviceCursors
            val next = maxOf(maxOf(cursor, result.cursor), nextCursor(cursor, result.ops))
            val stalled = next == cursor
            cursor = next
            if (result.ops.isEmpty() || stalled) break
        }
        val ownIds = pending.map { it.id }.toSet()
        val incoming = pulled.filter { it.id !in ownIds }
        val merged = mergeOps(incoming + pending, now())
        val purged = purgeCandidates(pulled, deviceCursors, now())
        store.apply(merged.winners, merged.conflicts, purged)
        store.saveCursor(cursor)
        return SyncReport(pending.size, incoming.size, merged.conflicts.size, purged.size, cursor)
    }
}
