package com.yourname.nib.core.db

import androidx.sqlite.db.SupportSQLiteDatabase
import com.yourname.nib.core.sync.PurgeTarget
import com.yourname.nib.core.sync.SyncCursor
import com.yourname.nib.core.sync.SyncStore

class RoomSyncStore(private val db: NibDatabase, private val cursor: SyncCursor) : SyncStore {
    override fun pending(): List<OpLog> = db.opLogDao().pendingSync()

    override fun markPushed(assignments: List<Pair<String, Long>>) {
        db.runInTransaction {
            assignments.forEach { (id, seq) -> db.opLogDao().markPushedSync(id, seq) }
        }
    }

    override fun apply(winners: List<OpLog>, conflicts: List<Conflict>, purged: List<PurgeTarget>) {
        db.runInTransaction {
            winners.forEach { applyOp(it) }
            if (conflicts.isNotEmpty()) db.conflictDao().insertAllSync(conflicts)
            purged.forEach { purge(it) }
            db.opLogDao().purgeSyncedSync()
        }
    }

    override fun cursor(): Long = cursor.get()

    override fun saveCursor(seq: Long) = cursor.set(seq)

    private fun applyOp(op: OpLog) {
        val sqlite = db.openHelper.writableDatabase
        if (op.entityType == "entry_tag") {
            val parts = op.entityId.split(':', limit = 2)
            if (parts.size != 2) return
            if (op.value == null) {
                sqlite.execSQL(
                    "DELETE FROM entry_tags WHERE entry_id = ? AND tag_id = ?",
                    arrayOf<Any?>(parts[0], parts[1]),
                )
            } else {
                sqlite.execSQL(
                    "INSERT OR IGNORE INTO entry_tags(entry_id, tag_id) VALUES(?, ?)",
                    arrayOf<Any?>(parts[0], parts[1]),
                )
            }
            return
        }
        val table = tableFor(op.entityType) ?: return
        if (op.field !in columnsFor(op.entityType)) return
        ensureRow(op, sqlite)
        sqlite.execSQL(
            "UPDATE $table SET ${op.field} = ? WHERE id = ?",
            arrayOf<Any?>(op.value, op.entityId),
        )
    }

    private fun ensureRow(op: OpLog, sqlite: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()
        when (op.entityType) {
            "entry" -> sqlite.execSQL(
                "INSERT OR IGNORE INTO entries(id, title, sort, hlc, device_id, created_at, updated_at) VALUES(?, '', ?, ?, ?, ?, ?)",
                arrayOf<Any?>(op.entityId, op.hlc, op.hlc, op.deviceId, now, now),
            )
            "list" -> sqlite.execSQL(
                "INSERT OR IGNORE INTO lists(id, name, color, sort, created_at, updated_at) VALUES(?, '', 'indigo', ?, ?, ?)",
                arrayOf<Any?>(op.entityId, op.hlc, now, now),
            )
            "tag" -> sqlite.execSQL(
                "INSERT OR IGNORE INTO tags(id, name, created_at) VALUES(?, '', ?)",
                arrayOf<Any?>(op.entityId, now),
            )
        }
    }

    private fun purge(target: PurgeTarget) {
        val table = tableFor(target.entityType) ?: return
        db.openHelper.writableDatabase.execSQL(
            "DELETE FROM $table WHERE id = ?",
            arrayOf<Any?>(target.entityId),
        )
    }

    private fun tableFor(type: String): String? = when (type) {
        "entry" -> "entries"
        "list" -> "lists"
        "tag" -> "tags"
        "item" -> "entry_items"
        else -> null
    }

    private fun columnsFor(type: String): Set<String> = when (type) {
        "entry" -> setOf(
            "type", "title", "body", "list_id", "due_date", "due_time", "remind_at", "duration_min",
            "snooze_until", "snooze_count", "channel", "repeat_freq", "repeat_n", "priority", "done",
            "done_at", "pinned", "sort", "calendar_event_id", "hlc", "device_id", "created_at",
            "updated_at", "deleted_at",
        )
        "list" -> setOf("name", "color", "sort", "created_at", "updated_at", "deleted_at")
        "tag" -> setOf("name", "created_at", "deleted_at")
        "item" -> setOf("entry_id", "text", "done", "sort")
        else -> emptySet()
    }
}
