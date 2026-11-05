package com.yourname.nib.core.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "lists")
data class NoteList(
    @PrimaryKey val id: String,
    val name: String,
    val color: String,
    val sort: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)

@Entity(
    tableName = "entries",
    foreignKeys = [
        ForeignKey(
            entity = NoteList::class,
            parentColumns = ["id"],
            childColumns = ["list_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("due_date", "done", "deleted_at", name = "idx_entries_due"),
        Index("list_id", "deleted_at", name = "idx_entries_list"),
    ],
)
data class Entry(
    @PrimaryKey val id: String,
    val type: String = "note",
    val title: String,
    val body: String = "",
    @ColumnInfo(name = "list_id") val listId: String? = null,
    @ColumnInfo(name = "due_date") val dueDate: String? = null,
    @ColumnInfo(name = "due_time") val dueTime: String? = null,
    @ColumnInfo(name = "remind_at") val remindAt: Long? = null,
    @ColumnInfo(name = "duration_min") val durationMin: Int = 30,
    @ColumnInfo(name = "snooze_until") val snoozeUntil: Long? = null,
    @ColumnInfo(name = "snooze_count") val snoozeCount: Int = 0,
    val channel: String = "notify",
    @ColumnInfo(name = "repeat_freq") val repeatFreq: String = "none",
    @ColumnInfo(name = "repeat_n") val repeatN: Int = 1,
    val priority: Int = 0,
    val done: Int = 0,
    @ColumnInfo(name = "done_at") val doneAt: Long? = null,
    val pinned: Int = 0,
    val sort: String,
    @ColumnInfo(name = "calendar_event_id") val calendarEventId: Long? = null,
    val hlc: String,
    @ColumnInfo(name = "device_id") val deviceId: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)

@Entity(tableName = "tags")
data class Tag(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)

@Entity(
    tableName = "entry_tags",
    primaryKeys = ["entry_id", "tag_id"],
    foreignKeys = [
        ForeignKey(
            entity = Entry::class,
            parentColumns = ["id"],
            childColumns = ["entry_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Tag::class,
            parentColumns = ["id"],
            childColumns = ["tag_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class EntryTag(
    @ColumnInfo(name = "entry_id") val entryId: String,
    @ColumnInfo(name = "tag_id") val tagId: String,
)

@Entity(
    tableName = "entry_items",
    foreignKeys = [
        ForeignKey(
            entity = Entry::class,
            parentColumns = ["id"],
            childColumns = ["entry_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class EntryItem(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "entry_id") val entryId: String,
    @ColumnInfo(name = "text") val content: String,
    val done: Int = 0,
    val sort: String,
)

@Entity(tableName = "pending_capture")
data class PendingCapture(
    @PrimaryKey val id: String,
    val raw: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(tableName = "op_log")
data class OpLog(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "entity_type") val entityType: String,
    @ColumnInfo(name = "entity_id") val entityId: String,
    val field: String,
    val value: String? = null,
    val hlc: String,
    @ColumnInfo(name = "device_id") val deviceId: String,
    @ColumnInfo(name = "server_seq") val serverSeq: Long? = null,
)

@Entity(tableName = "conflicts")
data class Conflict(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "entity_type") val entityType: String,
    @ColumnInfo(name = "entity_id") val entityId: String,
    val field: String,
    @ColumnInfo(name = "loser_value") val loserValue: String?,
    @ColumnInfo(name = "loser_hlc") val loserHlc: String,
    @ColumnInfo(name = "winner_hlc") val winnerHlc: String,
    @ColumnInfo(name = "device_id") val deviceId: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
