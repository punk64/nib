package com.yourname.nib.core.export

import com.yourname.nib.core.db.Entry
import com.yourname.nib.core.db.EntryItem
import com.yourname.nib.core.db.EntryTag
import com.yourname.nib.core.db.NoteList
import com.yourname.nib.core.db.Tag
import com.yourname.nib.core.time.NibDate
import com.yourname.nib.core.time.parseDateKey
import com.yourname.nib.core.time.parseTimeKey
import com.yourname.nib.core.time.twoDigits
import java.util.Calendar
import java.util.TimeZone

data class NibSnapshot(
    val lists: List<NoteList> = emptyList(),
    val entries: List<Entry> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val links: List<EntryTag> = emptyList(),
    val items: List<EntryItem> = emptyList(),
)

fun snapshotToJson(snapshot: NibSnapshot): String = jsonObject(
    listOf(
        "schema" to jsonNumber(1),
        "lists" to jsonArray(snapshot.lists.map { listJson(it) }),
        "entries" to jsonArray(snapshot.entries.map { entryJson(it) }),
        "tags" to jsonArray(snapshot.tags.map { tagJson(it) }),
        "links" to jsonArray(snapshot.links.map { linkJson(it) }),
        "items" to jsonArray(snapshot.items.map { itemJson(it) }),
    ),
)

fun jsonToSnapshot(text: String): NibSnapshot {
    val root = parseJson(text) as? Map<*, *> ?: return NibSnapshot()
    return NibSnapshot(
        lists = rows(root["lists"]).map {
            NoteList(it.text("id"), it.text("name"), it.text("color"), it.text("sort"), it.num("createdAt"), it.num("updatedAt"), it.nullableNum("deletedAt"))
        },
        entries = rows(root["entries"]).map {
            Entry(
                id = it.text("id"),
                type = it.text("type"),
                title = it.text("title"),
                body = it.text("body"),
                listId = it.nullableText("listId"),
                dueDate = it.nullableText("dueDate"),
                dueTime = it.nullableText("dueTime"),
                remindAt = it.nullableNum("remindAt"),
                durationMin = it.intNum("durationMin"),
                snoozeUntil = it.nullableNum("snoozeUntil"),
                snoozeCount = it.intNum("snoozeCount"),
                channel = it.text("channel"),
                repeatFreq = it.text("repeatFreq"),
                repeatN = it.intNum("repeatN"),
                priority = it.intNum("priority"),
                done = it.intNum("done"),
                doneAt = it.nullableNum("doneAt"),
                pinned = it.intNum("pinned"),
                sort = it.text("sort"),
                calendarEventId = it.nullableNum("calendarEventId"),
                hlc = it.text("hlc"),
                deviceId = it.text("deviceId"),
                createdAt = it.num("createdAt"),
                updatedAt = it.num("updatedAt"),
                deletedAt = it.nullableNum("deletedAt"),
            )
        },
        tags = rows(root["tags"]).map {
            Tag(it.text("id"), it.text("name"), it.num("createdAt"), it.nullableNum("deletedAt"))
        },
        links = rows(root["links"]).map { EntryTag(it.text("entryId"), it.text("tagId")) },
        items = rows(root["items"]).map {
            EntryItem(it.text("id"), it.text("entryId"), it.text("text"), it.intNum("done"), it.text("sort"))
        },
    )
}

fun toMarkdown(snapshot: NibSnapshot): String {
    val listNames = snapshot.lists.associate { it.id to it.name }
    val tagNames = snapshot.tags.associate { it.id to it.name }
    val links = snapshot.links.groupBy { it.entryId }
    val items = snapshot.items.groupBy { it.entryId }
    val out = StringBuilder("# 落笔备份\n\n")
    snapshot.entries.filter { it.deletedAt == null }.groupBy { it.listId }.forEach { (listId, entries) ->
        out.append("## ").append(listId?.let { listNames[it] } ?: "未分类").append("\n\n")
        entries.forEach { entry ->
            out.append("- [").append(if (entry.done == 1) "x" else " ").append("] ").append(entry.title).append('\n')
            val meta = mutableListOf<String>()
            entry.dueDate?.let { meta.add(it + (entry.dueTime?.let { clock -> " $clock" } ?: "")) }
            links[entry.id]?.mapNotNull { tagNames[it.tagId] }?.forEach { meta.add("#$it") }
            if (meta.isNotEmpty()) out.append("  - ").append(meta.joinToString(" · ")).append('\n')
            if (entry.body.isNotBlank()) entry.body.lines().forEach { out.append("  - ").append(it).append('\n') }
            items[entry.id]?.forEach { item ->
                out.append("  - [").append(if (item.done == 1) "x" else " ").append("] ").append(item.content).append('\n')
            }
        }
        out.append('\n')
    }
    return out.toString()
}

fun toIcs(snapshot: NibSnapshot): String {
    val out = StringBuilder("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Nib//CN\r\nCALSCALE:GREGORIAN\r\n")
    snapshot.entries.filter { it.deletedAt == null && it.dueDate != null }.forEach { entry ->
        val date = parseDateKey(entry.dueDate) ?: return@forEach
        out.append("BEGIN:VEVENT\r\n")
            .append("UID:").append(entry.id).append("@nib\r\n")
            .append("DTSTAMP:").append(utcStamp(entry.updatedAt)).append("\r\n")
        val clock = parseTimeKey(entry.dueTime)
        if (clock == null) {
            out.append("DTSTART;VALUE=DATE:").append(compactDate(date.dateKey())).append("\r\n")
                .append("DTEND;VALUE=DATE:").append(compactDate(date.plusDays(1).dateKey())).append("\r\n")
        } else {
            out.append("DTSTART:").append(localStamp(date.dateKey(), clock.hour, clock.minute)).append("\r\n")
            val (endDate, endHour, endMinute) = endOf(date, clock.hour, clock.minute, entry.durationMin)
            out.append("DTEND:").append(localStamp(endDate, endHour, endMinute)).append("\r\n")
        }
        out.append("SUMMARY:").append(icsEscape(entry.title)).append("\r\n")
        if (entry.body.isNotBlank()) out.append("DESCRIPTION:").append(icsEscape(entry.body)).append("\r\n")
        out.append("END:VEVENT\r\n")
    }
    return out.append("END:VCALENDAR\r\n").toString()
}

private fun listJson(v: NoteList) = jsonObject(
    listOf(
        "id" to jsonText(v.id),
        "name" to jsonText(v.name),
        "color" to jsonText(v.color),
        "sort" to jsonText(v.sort),
        "createdAt" to jsonNumber(v.createdAt),
        "updatedAt" to jsonNumber(v.updatedAt),
        "deletedAt" to jsonNumber(v.deletedAt),
    ),
)

private fun entryJson(v: Entry) = jsonObject(
    listOf(
        "id" to jsonText(v.id),
        "type" to jsonText(v.type),
        "title" to jsonText(v.title),
        "body" to jsonText(v.body),
        "listId" to jsonText(v.listId),
        "dueDate" to jsonText(v.dueDate),
        "dueTime" to jsonText(v.dueTime),
        "remindAt" to jsonNumber(v.remindAt),
        "durationMin" to jsonNumber(v.durationMin),
        "snoozeUntil" to jsonNumber(v.snoozeUntil),
        "snoozeCount" to jsonNumber(v.snoozeCount),
        "channel" to jsonText(v.channel),
        "repeatFreq" to jsonText(v.repeatFreq),
        "repeatN" to jsonNumber(v.repeatN),
        "priority" to jsonNumber(v.priority),
        "done" to jsonNumber(v.done),
        "doneAt" to jsonNumber(v.doneAt),
        "pinned" to jsonNumber(v.pinned),
        "sort" to jsonText(v.sort),
        "calendarEventId" to jsonNumber(v.calendarEventId),
        "hlc" to jsonText(v.hlc),
        "deviceId" to jsonText(v.deviceId),
        "createdAt" to jsonNumber(v.createdAt),
        "updatedAt" to jsonNumber(v.updatedAt),
        "deletedAt" to jsonNumber(v.deletedAt),
    ),
)

private fun tagJson(v: Tag) = jsonObject(
    listOf(
        "id" to jsonText(v.id),
        "name" to jsonText(v.name),
        "createdAt" to jsonNumber(v.createdAt),
        "deletedAt" to jsonNumber(v.deletedAt),
    ),
)

private fun linkJson(v: EntryTag) = jsonObject(
    listOf("entryId" to jsonText(v.entryId), "tagId" to jsonText(v.tagId)),
)

private fun itemJson(v: EntryItem) = jsonObject(
    listOf(
        "id" to jsonText(v.id),
        "entryId" to jsonText(v.entryId),
        "text" to jsonText(v.content),
        "done" to jsonNumber(v.done),
        "sort" to jsonText(v.sort),
    ),
)

private fun rows(raw: Any?): List<Map<*, *>> =
    (raw as? List<*>)?.filterIsInstance<Map<*, *>>() ?: emptyList()

private fun Map<*, *>.text(key: String): String = this[key] as? String ?: ""
private fun Map<*, *>.nullableText(key: String): String? = this[key] as? String
private fun Map<*, *>.num(key: String): Long = (this[key] as? Number)?.toLong() ?: 0L
private fun Map<*, *>.nullableNum(key: String): Long? = (this[key] as? Number)?.toLong()
private fun Map<*, *>.intNum(key: String): Int = (this[key] as? Number)?.toInt() ?: 0

private fun compactDate(dateKey: String): String = dateKey.replace("-", "")

private fun localStamp(dateKey: String, hour: Int, minute: Int): String =
    compactDate(dateKey) + "T" + twoDigits(hour) + twoDigits(minute) + "00"

private fun utcStamp(millis: Long): String {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    cal.timeInMillis = millis
    return "%04d%02d%02dT%02d%02d%02dZ".format(
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH) + 1,
        cal.get(Calendar.DAY_OF_MONTH),
        cal.get(Calendar.HOUR_OF_DAY),
        cal.get(Calendar.MINUTE),
        cal.get(Calendar.SECOND),
    )
}

private fun endOf(date: NibDate, hour: Int, minute: Int, duration: Int): Triple<String, Int, Int> {
    val total = hour * 60 + minute + duration.coerceAtLeast(1)
    val rest = total % 1440
    return Triple(date.plusDays((total / 1440).toLong()).dateKey(), rest / 60, rest % 60)
}

private fun icsEscape(text: String): String = text
    .replace("\\", "\\\\")
    .replace(";", "\\;")
    .replace(",", "\\,")
    .replace("\r\n", "\\n")
    .replace("\n", "\\n")
