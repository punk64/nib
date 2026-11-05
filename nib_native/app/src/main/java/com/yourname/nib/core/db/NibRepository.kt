package com.yourname.nib.core.db

import androidx.room.withTransaction
import com.yourname.nib.core.calendar.suggestChannel
import com.yourname.nib.core.export.NibSnapshot
import com.yourname.nib.core.localDeviceId
import com.yourname.nib.core.model.EntryBundle
import com.yourname.nib.core.model.draftFromRaw
import com.yourname.nib.core.organize.rebalanceKeys
import com.yourname.nib.core.organize.sortKeyBetween
import com.yourname.nib.core.newSortKey
import com.yourname.nib.core.newUlid
import com.yourname.nib.core.nextHlc
import com.yourname.nib.core.reminder.advanceDue
import com.yourname.nib.core.reminder.leadMinutesFor
import com.yourname.nib.core.reminder.remindAtFor
import com.yourname.nib.core.sync.SyncCursor
import com.yourname.nib.core.sync.SyncEngine
import com.yourname.nib.core.sync.SyncTransport
import com.yourname.nib.core.time.parseDateKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

data class SyncStats(val pending: Int, val cursor: Long, val conflicts: Int)

class NibRepository(private val db: NibDatabase) {
    fun observeLists(): Flow<List<NoteList>> = db.listDao().observeVisible()

    fun observeTags(): Flow<List<Tag>> = db.tagDao().observeVisible()

    fun observeConflicts(): Flow<List<Conflict>> = db.conflictDao().observeAll()

    fun observeBundles(): Flow<List<EntryBundle>> = combine(
        db.entryDao().observeVisible(),
        db.listDao().observeVisible(),
        db.tagDao().observeVisible(),
        db.entryTagDao().observeAll(),
    ) { entries, lists, tags, links ->
        val listNames = lists.associate { it.id to it.name }
        val tagNames = tags.associate { it.id to it.name }
        val byEntry = links.groupBy { it.entryId }
        entries.map { entry ->
            val ids = byEntry[entry.id].orEmpty().map { it.tagId }.filter { tagNames.containsKey(it) }.toSet()
            EntryBundle(
                entry = entry,
                tagIds = ids,
                tagNames = ids.mapNotNull { tagNames[it] },
                listName = entry.listId?.let { listNames[it] },
            )
        }
    }

    suspend fun resolveTagNames(names: List<String>): Set<String> = db.withTransaction {
        if (names.isEmpty()) return@withTransaction emptySet()
        val existing = db.tagDao().observeVisible().first()
        val result = mutableSetOf<String>()
        for (name in names) {
            val hit = existing.firstOrNull { it.name == name }
            if (hit != null) {
                result.add(hit.id)
            } else {
                val id = newUlid()
                val now = System.currentTimeMillis()
                db.tagDao().insert(Tag(id, name, now))
                log("tag", id, "name" to name, "created_at" to now.toString(), "deleted_at" to null)
                result.add(id)
            }
        }
        result
    }

    suspend fun createEntry(
        raw: String,
        title: String,
        dueDate: String?,
        dueTime: String?,
        listId: String?,
        tagIds: Set<String>,
        priority: Int,
        repeatFreq: String,
        repeatN: Int,
        leadMinutes: Int = 30,
        channel: String = "notify",
        durationMin: Int = 30,
    ): String {
        val now = System.currentTimeMillis()
        val id = newUlid()
        val entry = Entry(
            id = id,
            title = title,
            body = raw,
            listId = listId,
            dueDate = dueDate,
            dueTime = dueTime,
            remindAt = remindAtFor(dueDate, dueTime, leadMinutes),
            channel = channel,
            durationMin = durationMin,
            repeatFreq = repeatFreq,
            repeatN = repeatN,
            priority = priority,
            sort = newSortKey(),
            hlc = nextHlc(),
            deviceId = localDeviceId(),
            createdAt = now,
            updatedAt = now,
        )
        db.withTransaction {
            db.entryDao().insert(entry)
            linkTags(id, tagIds)
            log("entry", id, *entryFields(entry).toTypedArray())
        }
        return id
    }

    suspend fun updateEntry(entry: Entry, tagIds: Set<String>) {
        db.withTransaction {
            val old = db.entryDao().byId(entry.id)
            val next = entry.copy(
                updatedAt = System.currentTimeMillis(),
                hlc = nextHlc(),
                deviceId = localDeviceId(),
            )
            db.entryDao().update(next)
            linkTags(entry.id, tagIds)
            log("entry", entry.id, *changedEntryFields(old, next).toTypedArray())
        }
    }

    suspend fun setDone(id: String, done: Boolean) {
        val at = System.currentTimeMillis()
        db.withTransaction {
            db.entryDao().setDone(id, if (done) 1 else 0, at)
            log(
                "entry", id,
                "done" to if (done) "1" else "0",
                "done_at" to at.toString(),
                "updated_at" to at.toString(),
            )
        }
    }

    suspend fun allEntries(): List<Entry> = db.entryDao().all()

    suspend fun finishFromNotification(id: String) {
        val entry = db.entryDao().byId(id) ?: return
        val at = System.currentTimeMillis()
        val next = if (entry.repeatFreq == "none") {
            null
        } else {
            entry.dueDate?.let { parseDateKey(it) }?.let { advanceDue(it, entry.repeatFreq, entry.repeatN) }
        }
        db.withTransaction {
            if (next == null) {
                db.entryDao().setDone(id, 1, at)
                log("entry", id, "done" to "1", "done_at" to at.toString(), "updated_at" to at.toString())
                return@withTransaction
            }
            val history = entry.copy(
                id = newUlid(),
                done = 1,
                doneAt = at,
                remindAt = null,
                snoozeUntil = null,
                snoozeCount = 0,
                hlc = nextHlc(),
                createdAt = at,
                updatedAt = at,
            )
            db.entryDao().insert(history)
            log("entry", history.id, *entryFields(history).toTypedArray())
            val lead = leadMinutesFor(entry.dueDate, entry.dueTime, entry.remindAt)
            val dueDate = next.dateKey()
            val remindAt = remindAtFor(dueDate, entry.dueTime, lead)
            db.entryDao().advance(
                id = id,
                dueDate = dueDate,
                dueTime = entry.dueTime,
                remindAt = remindAt,
                at = at,
            )
            log(
                "entry", id,
                "due_date" to dueDate,
                "due_time" to entry.dueTime,
                "remind_at" to remindAt?.toString(),
                "snooze_until" to null,
                "snooze_count" to "0",
                "done" to "0",
                "updated_at" to at.toString(),
            )
        }
    }

    suspend fun applySnooze(id: String, until: Long?) {
        val at = System.currentTimeMillis()
        db.withTransaction {
            if (until == null) db.entryDao().clearSnooze(id, at) else db.entryDao().setSnooze(id, until, at)
            log("entry", id, "snooze_until" to until?.toString(), "updated_at" to at.toString())
        }
    }

    suspend fun deleteEntry(id: String) {
        val at = System.currentTimeMillis()
        db.withTransaction {
            db.entryDao().softDelete(id, at)
            log("entry", id, "deleted_at" to at.toString(), "updated_at" to at.toString())
        }
    }

    suspend fun allEntriesIncludingDeleted(): List<Entry> = db.entryDao().allIncludingDeleted()

    suspend fun enqueueCapture(raw: String) {
        db.pendingCaptureDao().insert(PendingCapture(newUlid(), raw, System.currentTimeMillis()))
    }

    suspend fun consumePendingCaptures(): Int {
        val pending = db.pendingCaptureDao().all()
        for (item in pending) {
            val draft = draftFromRaw(item.raw)
            val extra = resolveTagNames(draft.newTagNames)
            val channel = suggestChannel(draft.dueTime, 30)
            createEntry(
                raw = draft.raw,
                title = draft.title,
                dueDate = draft.dueDate,
                dueTime = draft.dueTime,
                listId = draft.listId,
                tagIds = draft.tagIds + extra,
                priority = draft.priority,
                repeatFreq = draft.repeatFreq,
                repeatN = draft.repeatN,
                leadMinutes = draft.leadMinutes,
                channel = channel,
                durationMin = if (channel == "calendar") 30 else 0,
            )
            db.pendingCaptureDao().delete(item.id)
        }
        return pending.size
    }

    suspend fun markDoneFromWidget(id: String) {
        db.pendingCaptureDao().markDone(id)
    }

    suspend fun entriesWithCalendarEvents(): List<Entry> = db.entryDao().withCalendarEvent()

    suspend fun setCalendarEventId(id: String, eventId: Long?) {
        db.entryDao().setCalendarEventId(id, eventId)
    }

    suspend fun calendarEventCount(ids: List<String>): Int =
        if (ids.isEmpty()) 0 else db.entryDao().calendarEventCount(ids)

    suspend fun clearCalendarEventIds(ids: List<String>) {
        if (ids.isEmpty()) return
        val at = System.currentTimeMillis()
        db.withTransaction {
            db.entryDao().clearCalendarEventIds(ids, at)
            ids.forEach { id -> log("entry", id, "calendar_event_id" to "", "updated_at" to at.toString()) }
        }
    }

    suspend fun applyCalendarChange(
        id: String,
        dueDate: String,
        dueTime: String,
        durationMin: Int,
        remindAt: Long?,
        fields: List<String>,
    ) {
        val at = System.currentTimeMillis()
        db.withTransaction {
            db.entryDao().applyCalendarChange(id, dueDate, dueTime, durationMin, remindAt, at)
            for (field in fields) {
                val value = when (field) {
                    "due_date" -> dueDate
                    "due_time" -> dueTime
                    "duration_min" -> durationMin.toString()
                    else -> null
                }
                log("entry", id, field to value)
            }
            log("entry", id, "updated_at" to at.toString())
        }
    }

    suspend fun seedDefaults() {
        if (db.listDao().observeVisible().first().isNotEmpty()) return
        if (db.tagDao().observeVisible().first().isNotEmpty()) return
        val now = System.currentTimeMillis()
        val lists = listOf("工作" to "#5b8cff", "个人" to "#b487ff", "家庭" to "#ffa245")
        val tags = listOf("汇报", "协作", "采购", "健康", "阅读", "周报")
        db.withTransaction {
            lists.forEachIndexed { index, (name, color) ->
                val sort = "%016d-%s".format(now + index, newUlid())
                val list = NoteList(newUlid(), name, color, sort, now, now)
                db.listDao().insert(list)
                log(
                    "list", list.id,
                    "name" to name,
                    "color" to color,
                    "sort" to sort,
                    "created_at" to now.toString(),
                    "updated_at" to now.toString(),
                    "deleted_at" to null,
                )
            }
            tags.forEach { name ->
                val tag = Tag(newUlid(), name, now)
                db.tagDao().insert(tag)
                log("tag", tag.id, "name" to name, "created_at" to now.toString(), "deleted_at" to null)
            }
        }
    }

    suspend fun addList(name: String) {
        val now = System.currentTimeMillis()
        val list = NoteList(newUlid(), name, "indigo", newSortKey(), now, now)
        db.withTransaction {
            db.listDao().insert(list)
            log(
                "list", list.id,
                "name" to name,
                "color" to list.color,
                "sort" to list.sort,
                "created_at" to now.toString(),
                "updated_at" to now.toString(),
                "deleted_at" to null,
            )
        }
    }

    suspend fun renameList(list: NoteList, name: String) {
        val at = System.currentTimeMillis()
        db.withTransaction {
            db.listDao().update(list.copy(name = name, updatedAt = at))
            log("list", list.id, "name" to name, "updated_at" to at.toString())
        }
    }

    suspend fun deleteList(id: String) {
        val at = System.currentTimeMillis()
        db.withTransaction {
            db.listDao().softDelete(id, at)
            log("list", id, "deleted_at" to at.toString())
        }
    }

    suspend fun addTag(name: String) {
        val now = System.currentTimeMillis()
        val tag = Tag(newUlid(), name, now)
        db.withTransaction {
            db.tagDao().insert(tag)
            log("tag", tag.id, "name" to name, "created_at" to now.toString(), "deleted_at" to null)
        }
    }

    suspend fun deleteTag(id: String) {
        val at = System.currentTimeMillis()
        db.withTransaction {
            db.tagDao().softDelete(id, at)
            log("tag", id, "deleted_at" to at.toString())
        }
    }

    fun items(entryId: String): Flow<List<EntryItem>> = db.entryItemDao().observeByEntry(entryId)

    suspend fun addItem(entryId: String, text: String) {
        val items = db.entryItemDao().byEntry(entryId)
        val sort = sortKeyBetween(items.lastOrNull()?.sort, null) ?: newSortKey()
        val item = EntryItem(newUlid(), entryId, text, 0, sort)
        db.withTransaction {
            db.entryItemDao().insert(item)
            log(
                "item", item.id,
                "entry_id" to entryId,
                "text" to text,
                "done" to "0",
                "sort" to sort,
            )
        }
    }

    suspend fun setItemDone(id: String, done: Boolean) {
        db.withTransaction {
            db.entryItemDao().setDone(id, if (done) 1 else 0)
            log("item", id, "done" to if (done) "1" else "0")
        }
    }

    suspend fun setItemText(id: String, text: String) {
        db.withTransaction {
            db.entryItemDao().setText(id, text)
            log("item", id, "text" to text)
        }
    }

    suspend fun deleteItem(id: String) {
        db.withTransaction {
            db.entryItemDao().delete(id)
            log("item", id, "deleted_at" to System.currentTimeMillis().toString())
        }
    }

    suspend fun moveItem(id: String, prev: String?, next: String?): Boolean {
        val key = sortKeyBetween(prev, next) ?: return false
        db.withTransaction {
            db.entryItemDao().setSort(id, key)
            log("item", id, "sort" to key)
        }
        return true
    }

    suspend fun swapItemSort(firstId: String, firstSort: String, secondId: String, secondSort: String) {
        db.withTransaction {
            db.entryItemDao().setSort(firstId, secondSort)
            db.entryItemDao().setSort(secondId, firstSort)
            log("item", firstId, "sort" to secondSort)
            log("item", secondId, "sort" to firstSort)
        }
    }

    suspend fun setPinned(id: String, pinned: Boolean) {
        val at = System.currentTimeMillis()
        db.withTransaction {
            db.entryDao().setPinned(id, if (pinned) 1 else 0, at)
            log("entry", id, "pinned" to if (pinned) "1" else "0", "updated_at" to at.toString())
        }
    }

    suspend fun reorder(id: String, prev: String?, next: String?): Boolean {
        val key = sortKeyBetween(prev, next) ?: return false
        val at = System.currentTimeMillis()
        db.withTransaction {
            db.entryDao().setSort(id, key, at)
            log("entry", id, "sort" to key, "updated_at" to at.toString())
        }
        return true
    }

    suspend fun rebalance(ids: List<String>) {
        val keys = rebalanceKeys(null, null, ids.size)
        val at = System.currentTimeMillis()
        db.withTransaction {
            ids.forEachIndexed { index, id ->
                db.entryDao().setSort(id, keys[index], at)
                log("entry", id, "sort" to keys[index], "updated_at" to at.toString())
            }
        }
    }

    suspend fun setListForIds(ids: Set<String>, listId: String?) {
        if (ids.isEmpty()) return
        val at = System.currentTimeMillis()
        db.withTransaction {
            db.entryDao().setListForIds(ids.toList(), listId, at)
            ids.forEach { id -> log("entry", id, "list_id" to listId, "updated_at" to at.toString()) }
        }
    }

    suspend fun addTagToIds(ids: Set<String>, tagId: String) {
        if (ids.isEmpty()) return
        db.withTransaction {
            db.entryDao().addTagToIds(ids.toList(), tagId)
            ids.forEach { id -> log("entry_tag", "$id:$tagId", "linked" to "1") }
        }
    }

    suspend fun softDeleteIds(ids: Set<String>) {
        if (ids.isEmpty()) return
        val at = System.currentTimeMillis()
        db.withTransaction {
            db.entryDao().softDeleteIds(ids.toList(), at)
            ids.forEach { id -> log("entry", id, "deleted_at" to at.toString(), "updated_at" to at.toString()) }
        }
    }

    suspend fun snapshot(): NibSnapshot = NibSnapshot(
        lists = db.listDao().allIncludingDeleted(),
        entries = db.entryDao().allIncludingDeleted(),
        tags = db.tagDao().allIncludingDeleted(),
        links = db.entryTagDao().all(),
        items = db.entryItemDao().all(),
    )

    suspend fun importSnapshot(snapshot: NibSnapshot) {
        db.withTransaction {
            snapshot.lists.forEach { db.listDao().insert(it) }
            snapshot.tags.forEach { db.tagDao().insert(it) }
            snapshot.entries.forEach { db.entryDao().insert(it) }
            if (snapshot.links.isNotEmpty()) db.entryTagDao().insertAllIgnore(snapshot.links)
            snapshot.items.forEach { db.entryItemDao().insert(it) }
        }
    }

    fun syncStats(cursor: SyncCursor): SyncStats = SyncStats(
        db.opLogDao().pendingCountSync(),
        cursor.get(),
        db.conflictDao().countSync(),
    )

    fun deleteConflict(id: String) {
        db.conflictDao().deleteSync(id)
    }

    fun syncEngine(transport: SyncTransport, cursor: SyncCursor): SyncEngine =
        SyncEngine(transport, RoomSyncStore(db, cursor))

    fun ftsAvailable(): Boolean = runCatching {
        db.openHelper.readableDatabase.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'entries_fts'",
            arrayOf<Any?>(),
        ).use { it.moveToFirst() }
    }.getOrDefault(false)

    fun searchFts(match: String): Set<String> = runCatching {
        db.openHelper.readableDatabase.query(
            "SELECT f.entry_id FROM entries_fts f JOIN entries e ON e.id = f.entry_id WHERE entries_fts MATCH ? AND e.deleted_at IS NULL",
            arrayOf<Any?>(match),
        ).use { cursor ->
            val ids = mutableSetOf<String>()
            while (cursor.moveToNext()) ids.add(cursor.getString(0))
            ids
        }
    }.getOrDefault(emptySet())

    private suspend fun linkTags(entryId: String, tagIds: Set<String>) {
        val old = db.entryTagDao().tagIds(entryId).toSet()
        db.entryTagDao().clear(entryId)
        if (tagIds.isNotEmpty()) db.entryTagDao().insertAll(tagIds.map { EntryTag(entryId, it) })
        (old - tagIds).forEach { tagId -> log("entry_tag", "$entryId:$tagId", "linked" to null) }
        (tagIds - old).forEach { tagId -> log("entry_tag", "$entryId:$tagId", "linked" to "1") }
    }

    private suspend fun log(entityType: String, entityId: String, vararg fields: Pair<String, String?>) {
        fields.forEach { (field, value) ->
            db.opLogDao().insert(
                OpLog(
                    id = newUlid(),
                    entityType = entityType,
                    entityId = entityId,
                    field = field,
                    value = value,
                    hlc = nextHlc(),
                    deviceId = localDeviceId(),
                ),
            )
        }
    }

    private fun entryFields(e: Entry): List<Pair<String, String?>> = listOf(
        "type" to e.type,
        "title" to e.title,
        "body" to e.body,
        "list_id" to e.listId,
        "due_date" to e.dueDate,
        "due_time" to e.dueTime,
        "remind_at" to e.remindAt?.toString(),
        "duration_min" to e.durationMin.toString(),
        "snooze_until" to e.snoozeUntil?.toString(),
        "snooze_count" to e.snoozeCount.toString(),
        "channel" to e.channel,
        "repeat_freq" to e.repeatFreq,
        "repeat_n" to e.repeatN.toString(),
        "priority" to e.priority.toString(),
        "done" to e.done.toString(),
        "done_at" to e.doneAt?.toString(),
        "pinned" to e.pinned.toString(),
        "sort" to e.sort,
        "calendar_event_id" to e.calendarEventId?.toString(),
        "created_at" to e.createdAt.toString(),
        "updated_at" to e.updatedAt.toString(),
        "deleted_at" to e.deletedAt?.toString(),
    )

    private fun changedEntryFields(old: Entry?, next: Entry): List<Pair<String, String?>> {
        val all = entryFields(next)
        if (old == null) return all
        val before = entryFields(old).toMap()
        val changed = all.filter { (field, value) -> before[field] != value }.toMutableList()
        if (changed.isEmpty()) changed.add("updated_at" to next.updatedAt.toString())
        return changed
    }
}
