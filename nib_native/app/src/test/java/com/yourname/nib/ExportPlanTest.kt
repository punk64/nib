package com.yourname.nib

import com.yourname.nib.core.db.Entry
import com.yourname.nib.core.db.EntryItem
import com.yourname.nib.core.db.EntryTag
import com.yourname.nib.core.db.NoteList
import com.yourname.nib.core.db.Tag
import com.yourname.nib.core.export.NibSnapshot
import com.yourname.nib.core.export.jsonToSnapshot
import com.yourname.nib.core.export.parseJson
import com.yourname.nib.core.export.snapshotToJson
import com.yourname.nib.core.export.toIcs
import com.yourname.nib.core.export.toMarkdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun exportEntry(
    id: String = "e1",
    title: String = "周报",
    body: String = "正文第一行",
    listId: String? = "l1",
    dueDate: String? = "2026-11-04",
    dueTime: String? = "09:00",
    durationMin: Int = 30,
    done: Int = 0,
    deletedAt: Long? = null,
) = Entry(
    id = id,
    title = title,
    body = body,
    listId = listId,
    dueDate = dueDate,
    dueTime = dueTime,
    remindAt = 1_700_000_000_000L,
    durationMin = durationMin,
    snoozeUntil = 2_000L,
    snoozeCount = 2,
    channel = "calendar",
    repeatFreq = "week",
    repeatN = 2,
    priority = 3,
    done = done,
    doneAt = 3_000L,
    pinned = 1,
    sort = "0000000000000001-$id",
    calendarEventId = 42L,
    hlc = "0000000000000001-0000-devA",
    deviceId = "devA",
    createdAt = 1_000L,
    updatedAt = 2_000L,
    deletedAt = deletedAt,
)

private fun fullSnapshot() = NibSnapshot(
    lists = listOf(NoteList("l1", "工作", "indigo", "0000000000000001-l1", 100L, 200L, null)),
    entries = listOf(exportEntry(), exportEntry("e2", "买菜", body = "", dueDate = null, dueTime = null, done = 1)),
    tags = listOf(Tag("t1", "重要", 100L, null), Tag("t2", "已归档", 100L, 500L)),
    links = listOf(EntryTag("e1", "t1")),
    items = listOf(EntryItem("i1", "e1", "核对数据", 1, "0000000000000001-i1")),
)

class ExportPlanTest {
    @Test
    fun jsonRoundTripKeepsDataAndRelations() {
        val original = fullSnapshot()
        assertEquals(original, jsonToSnapshot(snapshotToJson(original)))
    }

    @Test
    fun jsonEscapesQuotesNewlinesAndChinese() {
        val title = "标题 \"引号\" \\ 反斜杠\n换行\t制表"
        val snapshot = NibSnapshot(entries = listOf(exportEntry(title = title, body = "中文，逗号")))
        assertEquals(title, jsonToSnapshot(snapshotToJson(snapshot)).entries.single().title)
        assertTrue(snapshotToJson(snapshot).contains("\\n"))
        assertTrue(snapshotToJson(snapshot).contains("中文"))
    }

    @Test
    fun parseJsonHandlesPrimitivesAndNesting() {
        val value = parseJson("{\"n\":-12,\"f\":1.5,\"b\":true,\"z\":null,\"a\":[1,\"x\"]}") as Map<*, *>
        assertEquals(-12L, (value["n"] as Number).toLong())
        assertEquals(1.5, (value["f"] as Number).toDouble(), 0.0)
        assertEquals(true, value["b"])
        assertEquals(null, value["z"])
        assertEquals(2, (value["a"] as List<*>).size)
    }

    @Test
    fun markdownHasListTagsBodyAndSubitems() {
        val text = toMarkdown(fullSnapshot())
        assertTrue(text.startsWith("# 落笔备份"))
        assertTrue(text.contains("## 工作"))
        assertTrue(text.contains("- [ ] 周报"))
        assertTrue(text.contains("- [x] 买菜"))
        assertTrue(text.contains("2026-11-04 09:00"))
        assertTrue(text.contains("#重要"))
        assertTrue(text.contains("正文第一行"))
        assertTrue(text.contains("  - [x] 核对数据"))
        assertFalse(text.contains("已归档"))
    }

    @Test
    fun icsContainsTimedEventAndSkipsUndated() {
        val text = toIcs(fullSnapshot())
        assertTrue(text.startsWith("BEGIN:VCALENDAR"))
        assertTrue(text.endsWith("END:VCALENDAR\r\n"))
        assertEquals(1, text.split("BEGIN:VEVENT").size - 1)
        assertTrue(text.contains("DTSTART:20261104T090000"))
        assertTrue(text.contains("DTEND:20261104T093000"))
        assertTrue(text.contains("SUMMARY:周报"))
        assertTrue(text.contains("DESCRIPTION:正文第一行"))
        assertTrue(text.contains("UID:e1@nib"))
    }

    @Test
    fun icsEscapesAndHandlesAllDayEvents() {
        val text = toIcs(
            NibSnapshot(
                entries = listOf(
                    exportEntry(title = "会议, 分号;", body = "第一行\n第二行", dueTime = null),
                ),
            ),
        )
        assertTrue(text.contains("SUMMARY:会议\\, 分号\\;"))
        assertTrue(text.contains("DESCRIPTION:第一行\\n第二行"))
        assertTrue(text.contains("DTSTART;VALUE=DATE:20261104"))
        assertTrue(text.contains("DTEND;VALUE=DATE:20261105"))
    }

    @Test
    fun icsDurationCrossesMidnight() {
        val text = toIcs(NibSnapshot(entries = listOf(exportEntry(dueTime = "23:30", durationMin = 60))))
        assertTrue(text.contains("DTSTART:20261104T233000"))
        assertTrue(text.contains("DTEND:20261105T003000"))
    }

    @Test
    fun deletedEntriesAreExcludedFromHumanExportsButKeptInBackup() {
        val snapshot = NibSnapshot(entries = listOf(exportEntry(deletedAt = 9_000L)))
        assertFalse(toMarkdown(snapshot).contains("周报"))
        assertFalse(toIcs(snapshot).contains("BEGIN:VEVENT"))
        assertEquals(9_000L, jsonToSnapshot(snapshotToJson(snapshot)).entries.single().deletedAt)
    }
}
