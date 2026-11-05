package com.yourname.nib

import com.yourname.nib.core.calendar.agendaDayKey
import com.yourname.nib.core.calendar.expandEvent
import com.yourname.nib.core.calendar.parseDurationMinutes
import com.yourname.nib.core.calendar.parseRecurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class CalendarExpansionTest {

    private fun at(year: Int, month: Int, day: Int, hour: Int = 9, minute: Int = 0): Long {
        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(year, month - 1, day, hour, minute, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun expand(
        start: Long,
        end: Long,
        rule: String? = null,
        allDay: Boolean = false,
        extra: List<Long> = emptyList(),
        skip: List<Long> = emptyList(),
        windowStart: Long,
        windowEnd: Long,
    ) = expandEvent(
        eventId = 7L,
        title = "站会",
        start = start,
        end = end,
        allDay = allDay,
        rule = parseRecurrence(rule),
        extraDates = extra,
        skipDates = skip,
        windowStart = windowStart,
        windowEnd = windowEnd,
    )

    @Test
    fun singleEventEmitsOnceInsideWindow() {
        val start = at(2026, 11, 10)
        val items = expand(start, start + 3_600_000L, windowStart = at(2026, 11, 1, 0), windowEnd = at(2026, 12, 1, 0))
        assertEquals(1, items.size)
        assertEquals(start, items[0].start)
        assertEquals("站会", items[0].title)
    }

    @Test
    fun eventOutsideWindowIsDropped() {
        val start = at(2026, 12, 10)
        val items = expand(start, start + 3_600_000L, windowStart = at(2026, 11, 1, 0), windowEnd = at(2026, 12, 1, 0))
        assertTrue(items.isEmpty())
    }

    @Test
    fun dailyIntervalTwoSkipsOddDays() {
        val start = at(2026, 11, 1)
        val items = expand(
            start,
            start + 1_800_000L,
            rule = "FREQ=DAILY;INTERVAL=2",
            windowStart = at(2026, 11, 1, 0),
            windowEnd = at(2026, 11, 8, 0),
        )
        assertEquals(listOf(1, 3, 5, 7), items.map { agendaDayKey(it).takeLast(2).toInt() })
    }

    @Test
    fun weeklyByDayExpandsBothWeekdays() {
        val start = at(2026, 11, 2)
        val items = expand(
            start,
            start + 1_800_000L,
            rule = "FREQ=WEEKLY;BYDAY=MO,WE",
            windowStart = at(2026, 11, 2, 0),
            windowEnd = at(2026, 11, 9, 0),
        )
        assertEquals(listOf("2026-11-02", "2026-11-04"), items.map { agendaDayKey(it) })
    }

    @Test
    fun monthlySkipsMonthWithoutMatchingDay() {
        val start = at(2026, 1, 31)
        val february = expand(
            start,
            start + 3_600_000L,
            rule = "FREQ=MONTHLY",
            windowStart = at(2026, 2, 1, 0),
            windowEnd = at(2026, 3, 1, 0),
        )
        assertTrue(february.isEmpty())
        val march = expand(
            start,
            start + 3_600_000L,
            rule = "FREQ=MONTHLY",
            windowStart = at(2026, 3, 1, 0),
            windowEnd = at(2026, 4, 1, 0),
        )
        assertEquals(listOf("2026-03-31"), march.map { agendaDayKey(it) })
    }

    @Test
    fun countStopsExpansion() {
        val start = at(2026, 11, 1)
        val items = expand(
            start,
            start + 1_800_000L,
            rule = "FREQ=DAILY;COUNT=3",
            windowStart = at(2026, 11, 1, 0),
            windowEnd = at(2026, 12, 1, 0),
        )
        assertEquals(3, items.size)
    }

    @Test
    fun untilStopsExpansion() {
        val start = at(2026, 11, 1)
        val items = expand(
            start,
            start + 1_800_000L,
            rule = "FREQ=DAILY;UNTIL=20261105T000000Z",
            windowStart = at(2026, 11, 1, 0),
            windowEnd = at(2026, 12, 1, 0),
        )
        assertEquals(listOf("2026-11-01", "2026-11-02", "2026-11-03", "2026-11-04"), items.map { agendaDayKey(it) })
    }

    @Test
    fun excludedDateIsSkipped() {
        val start = at(2026, 11, 1)
        val items = expand(
            start,
            start + 1_800_000L,
            rule = "FREQ=DAILY",
            skip = listOf(at(2026, 11, 4, 9)),
            windowStart = at(2026, 11, 1, 0),
            windowEnd = at(2026, 11, 6, 0),
        )
        assertEquals(listOf("2026-11-01", "2026-11-02", "2026-11-03", "2026-11-05"), items.map { agendaDayKey(it) })
    }

    @Test
    fun extraDateIsAdded() {
        val start = at(2026, 11, 1)
        val items = expand(
            start,
            start + 1_800_000L,
            extra = listOf(at(2026, 11, 20, 15)),
            windowStart = at(2026, 11, 1, 0),
            windowEnd = at(2026, 12, 1, 0),
        )
        assertEquals(listOf("2026-11-01", "2026-11-20"), items.map { agendaDayKey(it) })
    }

    @Test
    fun longRunningSeriesJumpsToWindow() {
        val start = at(2020, 1, 1)
        val items = expand(
            start,
            start + 1_800_000L,
            rule = "FREQ=DAILY",
            windowStart = at(2026, 11, 1, 0),
            windowEnd = at(2026, 11, 4, 0),
        )
        assertEquals(3, items.size)
        assertEquals("2026-11-01", agendaDayKey(items[0]))
    }

    @Test
    fun allDayEventUsesUtcDayKey() {
        val utc = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        utc.clear()
        utc.set(2026, 10, 12, 0, 0, 0)
        val start = utc.timeInMillis
        val items = expand(
            start,
            start + 86_400_000L,
            allDay = true,
            windowStart = at(2026, 11, 1, 0),
            windowEnd = at(2026, 12, 1, 0),
        )
        assertEquals(1, items.size)
        assertTrue(items[0].allDay)
        assertEquals("2026-11-12", agendaDayKey(items[0]))
    }

    @Test
    fun durationParsesCommonForms() {
        assertEquals(90, parseDurationMinutes("PT1H30M"))
        assertEquals(45, parseDurationMinutes("PT45M"))
        assertEquals(1440, parseDurationMinutes("P1D"))
        assertEquals(10080, parseDurationMinutes("P1W"))
        assertNull(parseDurationMinutes(null))
        assertNull(parseDurationMinutes("PT0M"))
    }

    @Test
    fun recurrenceParsesFields() {
        val spec = parseRecurrence("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE;COUNT=10")
        assertNotNull(spec)
        assertEquals("WEEKLY", spec!!.freq)
        assertEquals(2, spec.interval)
        assertEquals(10, spec.count)
        assertEquals(2, spec.byDay.size)
        assertNull(parseRecurrence("BYDAY=MO"))
        assertNull(parseRecurrence(null))
    }
}
