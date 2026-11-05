package com.yourname.nib

import com.yourname.nib.core.calendar.AgendaItem
import com.yourname.nib.core.calendar.CHANNEL_CALENDAR
import com.yourname.nib.core.calendar.CHANNEL_NOTIFY
import com.yourname.nib.core.calendar.CalendarChange
import com.yourname.nib.core.calendar.EventSnapshot
import com.yourname.nib.core.calendar.agendaForDay
import com.yourname.nib.core.calendar.calendarChange
import com.yourname.nib.core.calendar.dateTimeKeys
import com.yourname.nib.core.calendar.eventEnd
import com.yourname.nib.core.calendar.eventValuesFor
import com.yourname.nib.core.calendar.suggestChannel
import com.yourname.nib.core.db.Entry
import com.yourname.nib.core.reminder.localMillis
import com.yourname.nib.core.time.NibDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

private fun calEntry(
    id: String = "e1",
    dueDate: String? = "2026-03-01",
    dueTime: String? = "10:00",
    channel: String = CHANNEL_CALENDAR,
    durationMin: Int = 30,
    done: Int = 0,
    deletedAt: Long? = null,
    calendarEventId: Long? = 7L,
    title: String = "牙科复诊",
    body: String = "带医保卡",
): Entry = Entry(
    id = id,
    title = title,
    body = body,
    dueDate = dueDate,
    dueTime = dueTime,
    channel = channel,
    durationMin = durationMin,
    done = done,
    deletedAt = deletedAt,
    calendarEventId = calendarEventId,
    sort = id,
    hlc = "h",
    deviceId = "d",
    createdAt = 0,
    updatedAt = 0,
)

class CalendarPlanTest {
    private val start = localMillis(NibDate(2026, 3, 1), 10, 0)
    private val zone = TimeZone.getDefault().id

    @Test
    fun suggestChannelFollowsTimeAndDuration() {
        assertEquals(CHANNEL_CALENDAR, suggestChannel("10:00", 30))
        assertEquals(CHANNEL_NOTIFY, suggestChannel(null, 30))
        assertEquals(CHANNEL_NOTIFY, suggestChannel("10:00", 0))
        assertEquals(CHANNEL_NOTIFY, suggestChannel("", 30))
    }

    @Test
    fun eventEndUsesDurationAndClampsToMinute() {
        assertEquals(start + 30 * 60_000L, eventEnd(start, 30))
        assertEquals(start + 60_000L, eventEnd(start, 0))
        assertEquals(start + 60_000L, eventEnd(start, -5))
    }

    @Test
    fun eventValuesForBuildsCalendarWindow() {
        val values = eventValuesFor(calEntry(), 42L, zone)
        assertEquals(42L, values?.calendarId)
        assertEquals("牙科复诊", values?.title)
        assertEquals("带医保卡", values?.description)
        assertEquals(start, values?.start)
        assertEquals(start + 30 * 60_000L, values?.end)
        assertEquals(zone, values?.timezone)
    }

    @Test
    fun eventValuesForRejectsIneligibleEntries() {
        assertNull(eventValuesFor(calEntry(channel = CHANNEL_NOTIFY), 42L, zone))
        assertNull(eventValuesFor(calEntry(dueDate = null), 42L, zone))
        assertNull(eventValuesFor(calEntry(dueTime = null), 42L, zone))
        assertNull(eventValuesFor(calEntry(dueTime = "bad"), 42L, zone))
        assertNull(eventValuesFor(calEntry(durationMin = 0), 42L, zone))
        assertNull(eventValuesFor(calEntry(done = 1), 42L, zone))
        assertNull(eventValuesFor(calEntry(deletedAt = 1L), 42L, zone))
    }

    @Test
    fun dateTimeKeysRoundTripsLocalMillis() {
        val (date, time) = dateTimeKeys(start)
        assertEquals("2026-03-01", date)
        assertEquals("10:00", time)
        assertEquals("2026-12-31" to "23:59", dateTimeKeys(localMillis(NibDate(2026, 12, 31), 23, 59)))
    }

    @Test
    fun calendarChangeIsNullWhenSystemMatchesLocal() {
        val snapshot = EventSnapshot(7L, "牙科复诊", start, start + 30 * 60_000L)
        assertNull(calendarChange(calEntry(), snapshot))
    }

    @Test
    fun calendarChangeTakesSystemTimeAsTruth() {
        val moved = localMillis(NibDate(2026, 3, 2), 14, 30)
        val snapshot = EventSnapshot(7L, "牙科复诊", moved, moved + 30 * 60_000L)
        assertEquals(
            CalendarChange("2026-03-02", "14:30", 30, listOf("due_date", "due_time")),
            calendarChange(calEntry(), snapshot),
        )
    }

    @Test
    fun calendarChangeDetectsDurationOnly() {
        val snapshot = EventSnapshot(7L, "牙科复诊", start, start + 90 * 60_000L)
        assertEquals(
            CalendarChange("2026-03-01", "10:00", 90, listOf("duration_min")),
            calendarChange(calEntry(), snapshot),
        )
    }

    @Test
    fun calendarChangeIgnoresTitleChange() {
        val snapshot = EventSnapshot(7L, "改名了", start, start + 30 * 60_000L)
        assertNull(calendarChange(calEntry(), snapshot))
    }

    @Test
    fun agendaForDayKeepsOverlappingEventsInStartOrder() {
        val dayStart = localMillis(NibDate(2026, 3, 1), 0, 0)
        val dayEnd = localMillis(NibDate(2026, 3, 2), 0, 0)
        val events = listOf(
            AgendaItem(3L, "晚", start + 3 * 60_000L, start + 4 * 60_000L),
            AgendaItem(1L, "早", start + 60_000L, start + 2 * 60_000L),
            AgendaItem(4L, "昨天", dayStart - 60_000L, dayStart),
            AgendaItem(5L, "明天", dayEnd, dayEnd + 60_000L),
            AgendaItem(2L, "跨天", dayEnd - 60_000L, dayEnd + 60_000L),
        )
        assertEquals(listOf(1L, 3L, 2L), agendaForDay(events, dayStart, dayEnd).map { it.eventId })
    }

    @Test
    fun agendaForDayFiltersOutsideRange() {
        val dayStart = localMillis(NibDate(2026, 3, 1), 0, 0)
        val dayEnd = localMillis(NibDate(2026, 3, 2), 0, 0)
        val events = listOf(
            AgendaItem(1L, "昨天结束", dayStart - 60_000L, dayStart),
            AgendaItem(2L, "明天开始", dayEnd, dayEnd + 60_000L),
        )
        assertTrue(agendaForDay(events, dayStart, dayEnd).isEmpty())
    }
}
