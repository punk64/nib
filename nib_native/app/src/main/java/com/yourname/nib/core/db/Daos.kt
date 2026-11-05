package com.yourname.nib.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {
    @Query("SELECT * FROM entries WHERE deleted_at IS NULL ORDER BY pinned DESC, sort")
    fun observeVisible(): Flow<List<Entry>>

    @Query("SELECT * FROM entries WHERE deleted_at IS NULL ORDER BY pinned DESC, sort")
    suspend fun all(): List<Entry>

    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun byId(id: String): Entry?

    @Query("SELECT * FROM entries WHERE channel = :channel AND deleted_at IS NULL AND done = 0")
    suspend fun byChannel(channel: String): List<Entry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: Entry)

    @Update
    suspend fun update(entry: Entry)

    @Query("UPDATE entries SET deleted_at = :at, updated_at = :at WHERE id = :id")
    suspend fun softDelete(id: String, at: Long)

    @Query("UPDATE entries SET done = :done, done_at = :at, updated_at = :at WHERE id = :id")
    suspend fun setDone(id: String, done: Int, at: Long)

    @Query("UPDATE entries SET calendar_event_id = :eventId WHERE id = :id")
    suspend fun setCalendarEventId(id: String, eventId: Long?)

    @Query("SELECT COUNT(*) FROM entries WHERE id IN (:ids) AND calendar_event_id IS NOT NULL AND deleted_at IS NULL")
    suspend fun calendarEventCount(ids: List<String>): Int

    @Query("UPDATE entries SET calendar_event_id = NULL, updated_at = :at WHERE id IN (:ids)")
    suspend fun clearCalendarEventIds(ids: List<String>, at: Long)

    @Query("UPDATE entries SET snooze_until = :until, snooze_count = snooze_count + 1, updated_at = :at WHERE id = :id")
    suspend fun setSnooze(id: String, until: Long?, at: Long)

    @Query("UPDATE entries SET snooze_until = NULL, snooze_count = 0, updated_at = :at WHERE id = :id")
    suspend fun clearSnooze(id: String, at: Long)

    @Query("UPDATE entries SET due_date = :dueDate, due_time = :dueTime, remind_at = :remindAt, snooze_until = NULL, snooze_count = 0, updated_at = :at WHERE id = :id")
    suspend fun advance(id: String, dueDate: String?, dueTime: String?, remindAt: Long?, at: Long)

    @Query("SELECT * FROM entries ORDER BY sort")
    suspend fun allIncludingDeleted(): List<Entry>

    @Query("SELECT * FROM entries WHERE calendar_event_id IS NOT NULL AND deleted_at IS NULL")
    suspend fun withCalendarEvent(): List<Entry>

    @Query("UPDATE entries SET due_date = :dueDate, due_time = :dueTime, duration_min = :durationMin, remind_at = :remindAt, updated_at = :at WHERE id = :id")
    suspend fun applyCalendarChange(
        id: String,
        dueDate: String,
        dueTime: String,
        durationMin: Int,
        remindAt: Long?,
        at: Long,
    )

    @Query("UPDATE entries SET pinned = :pinned, updated_at = :at WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Int, at: Long)

    @Query("UPDATE entries SET sort = :sort, updated_at = :at WHERE id = :id")
    suspend fun setSort(id: String, sort: String, at: Long)

    @Query("UPDATE entries SET list_id = :listId, updated_at = :at WHERE id IN (:ids)")
    suspend fun setListForIds(ids: List<String>, listId: String?, at: Long)

    @Query("UPDATE entries SET deleted_at = :at, updated_at = :at WHERE id IN (:ids)")
    suspend fun softDeleteIds(ids: List<String>, at: Long)

    @Query("INSERT OR IGNORE INTO entry_tags(entry_id, tag_id) SELECT id, :tagId FROM entries WHERE id IN (:ids)")
    suspend fun addTagToIds(ids: List<String>, tagId: String)
}

@Dao
interface ListDao {
    @Query("SELECT * FROM lists WHERE deleted_at IS NULL ORDER BY sort")
    fun observeVisible(): Flow<List<NoteList>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(list: NoteList)

    @Update
    suspend fun update(list: NoteList)

    @Query("UPDATE lists SET deleted_at = :at WHERE id = :id")
    suspend fun softDelete(id: String, at: Long)

    @Query("SELECT * FROM lists")
    suspend fun allIncludingDeleted(): List<NoteList>
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tags WHERE deleted_at IS NULL ORDER BY name")
    fun observeVisible(): Flow<List<Tag>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tag: Tag)

    @Query("UPDATE tags SET deleted_at = :at WHERE id = :id")
    suspend fun softDelete(id: String, at: Long)

    @Query("SELECT * FROM tags")
    suspend fun allIncludingDeleted(): List<Tag>
}

@Dao
interface EntryTagDao {
    @Query("SELECT * FROM entry_tags")
    fun observeAll(): Flow<List<EntryTag>>

    @Query("SELECT * FROM entry_tags")
    suspend fun all(): List<EntryTag>

    @Query("DELETE FROM entry_tags WHERE entry_id = :entryId")
    suspend fun clear(entryId: String)

    @Insert
    suspend fun insertAll(links: List<EntryTag>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(links: List<EntryTag>)

    @Query("SELECT tag_id FROM entry_tags WHERE entry_id = :entryId")
    suspend fun tagIds(entryId: String): List<String>
}

@Dao
interface EntryItemDao {
    @Query("SELECT * FROM entry_items WHERE entry_id = :entryId ORDER BY sort")
    fun observeByEntry(entryId: String): Flow<List<EntryItem>>

    @Query("SELECT * FROM entry_items WHERE entry_id = :entryId ORDER BY sort")
    suspend fun byEntry(entryId: String): List<EntryItem>

    @Query("SELECT * FROM entry_items")
    suspend fun all(): List<EntryItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: EntryItem)

    @Query("UPDATE entry_items SET text = :text WHERE id = :id")
    suspend fun setText(id: String, text: String)

    @Query("UPDATE entry_items SET done = :done WHERE id = :id")
    suspend fun setDone(id: String, done: Int)

    @Query("UPDATE entry_items SET sort = :sort WHERE id = :id")
    suspend fun setSort(id: String, sort: String)

    @Query("DELETE FROM entry_items WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface OpLogDao {
    @Insert
    suspend fun insert(op: OpLog)

    @Query("SELECT * FROM op_log WHERE server_seq IS NULL ORDER BY hlc")
    suspend fun pending(): List<OpLog>

    @Query("SELECT * FROM op_log WHERE server_seq IS NULL ORDER BY hlc")
    fun pendingSync(): List<OpLog>

    @Query("UPDATE op_log SET server_seq = :seq WHERE id = :id")
    fun markPushedSync(id: String, seq: Long)

    @Query("DELETE FROM op_log WHERE server_seq IS NOT NULL")
    fun purgeSyncedSync()

    @Query("SELECT COUNT(*) FROM op_log WHERE server_seq IS NULL")
    fun pendingCountSync(): Int
}

@Dao
interface ConflictDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<Conflict>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertAllSync(items: List<Conflict>)

    @Query("SELECT * FROM conflicts ORDER BY created_at DESC")
    fun observeAll(): Flow<List<Conflict>>

    @Query("SELECT COUNT(*) FROM conflicts")
    fun countSync(): Int

    @Query("DELETE FROM conflicts WHERE id = :id")
    fun deleteSync(id: String)
}

@Dao
interface PendingCaptureDao {
    @Insert
    suspend fun insert(capture: PendingCapture)

    @Query("SELECT * FROM pending_capture ORDER BY created_at")
    suspend fun all(): List<PendingCapture>

    @Query("DELETE FROM pending_capture WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE entries SET done = 1 WHERE id = :id")
    suspend fun markDone(id: String)
}
