package com.yourname.nib.core.sync

import com.yourname.nib.core.db.Conflict
import com.yourname.nib.core.db.OpLog
import com.yourname.nib.core.newUlid

const val PUSH_BATCH_SIZE = 100
const val PULL_PAGE_SIZE = 500
const val TOMBSTONE_GRACE_MS = 7L * 24 * 60 * 60 * 1000

data class MergeResult(val winners: List<OpLog>, val conflicts: List<Conflict>)

data class Tombstone(val entityType: String, val entityId: String, val effectiveAt: Long?, val deferred: Boolean)

data class PurgeTarget(val entityType: String, val entityId: String)

fun mergeOps(ops: List<OpLog>, now: Long): MergeResult {
    val tombstones = tombstonesFor(ops, now).associateBy { it.entityType to it.entityId }
    val winners = mutableListOf<OpLog>()
    val conflicts = mutableListOf<Conflict>()
    ops.groupBy { Triple(it.entityType, it.entityId, it.field) }.forEach { (key, group) ->
        val ranked = group.sortedWith(compareByDescending<OpLog> { it.hlc }.thenByDescending { it.deviceId })
        var winner = ranked.first()
        val tombstone = tombstones[key.first to key.second]
        if (key.third == "deleted_at" && tombstone != null && tombstone.deferred) {
            winner = winner.copy(value = tombstone.effectiveAt?.toString())
        }
        winners.add(winner)
        ranked.drop(1)
            .filter { it.deviceId != winner.deviceId }
            .forEach { loser ->
                conflicts.add(
                    Conflict(
                        id = newUlid(),
                        entityType = loser.entityType,
                        entityId = loser.entityId,
                        field = loser.field,
                        loserValue = loser.value,
                        loserHlc = loser.hlc,
                        winnerHlc = winner.hlc,
                        deviceId = loser.deviceId,
                        createdAt = now,
                    ),
                )
            }
    }
    return MergeResult(winners, conflicts)
}

fun tombstonesFor(ops: List<OpLog>, now: Long, graceMs: Long = TOMBSTONE_GRACE_MS): List<Tombstone> =
    ops.filter { it.field == "deleted_at" }
        .groupBy { it.entityType to it.entityId }
        .map { (key, deletes) ->
            val latest = deletes.maxBy { it.hlc }
            val at = latest.value?.toLongOrNull()
            val concurrent = ops.any {
                it.entityType == key.first && it.entityId == key.second &&
                    it.field != "deleted_at" && it.hlc > latest.hlc
            }
            when {
                at == null -> Tombstone(key.first, key.second, null, false)
                concurrent -> Tombstone(key.first, key.second, maxOf(at, now) + graceMs, true)
                else -> Tombstone(key.first, key.second, at, false)
            }
        }

fun purgeCandidates(
    ops: List<OpLog>,
    deviceCursors: Map<String, Long>,
    now: Long,
    graceMs: Long = TOMBSTONE_GRACE_MS,
): List<PurgeTarget> {
    if (deviceCursors.isEmpty()) return emptyList()
    val decisions = tombstonesFor(ops, now, graceMs).associateBy { it.entityType to it.entityId }
    return ops.filter { it.field == "deleted_at" && it.value != null && it.serverSeq != null }
        .groupBy { it.entityType to it.entityId }
        .mapNotNull { (key, group) ->
            val seq = group.maxBy { it.hlc }.serverSeq ?: return@mapNotNull null
            val at = decisions[key]?.effectiveAt ?: return@mapNotNull null
            if (at <= now && deviceCursors.values.all { it >= seq }) PurgeTarget(key.first, key.second)
            else null
        }
}

fun pushBatches(ops: List<OpLog>, size: Int = PUSH_BATCH_SIZE): List<List<OpLog>> =
    ops.sortedBy { it.hlc }.chunked(size.coerceAtLeast(1))

fun nextCursor(current: Long, ops: List<OpLog>): Long =
    maxOf(current, ops.mapNotNull { it.serverSeq }.maxOrNull() ?: current)

fun retryDelay(attempt: Int, baseMs: Long = 1_000L, maxMs: Long = 60_000L): Long =
    minOf(baseMs shl attempt.coerceIn(0, 20), maxMs)

fun <T> withRetry(maxAttempts: Int = 5, sleep: (Long) -> Unit = {}, block: (Int) -> T): T {
    var last: Throwable? = null
    for (attempt in 0 until maxAttempts.coerceAtLeast(1)) {
        try {
            return block(attempt)
        } catch (e: Exception) {
            last = e
            if (attempt < maxAttempts - 1) sleep(retryDelay(attempt))
        }
    }
    throw last ?: IllegalStateException("sync failed")
}
