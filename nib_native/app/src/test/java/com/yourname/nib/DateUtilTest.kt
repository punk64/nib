package com.yourname.nib

import com.yourname.nib.core.time.ClockTime
import com.yourname.nib.core.time.NibDate
import com.yourname.nib.core.time.clampDay
import com.yourname.nib.core.time.daysInMonth
import com.yourname.nib.core.time.isLeapYear
import com.yourname.nib.core.time.parseDateKey
import com.yourname.nib.core.time.parseTimeKey
import com.yourname.nib.core.time.timeKey
import com.yourname.nib.core.time.twoDigits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DateUtilTest {
    @Test fun twoDigitsPads() {
        assertEquals("09", twoDigits(9))
        assertEquals("12", twoDigits(12))
    }

    @Test fun dateKeyFormats() {
        assertEquals("2026-11-04", NibDate(2026, 11, 4).dateKey())
        assertEquals("2027-01-01", NibDate(2027, 1, 1).dateKey())
    }

    @Test fun timeKeyFormats() {
        assertEquals("09:05", timeKey(ClockTime(9, 5)))
        assertEquals("20:00", timeKey(ClockTime(20, 0)))
    }

    @Test fun parseDateKeyRoundTrip() {
        assertEquals(NibDate(2026, 11, 4), parseDateKey("2026-11-04"))
        assertNull(parseDateKey(null))
        assertNull(parseDateKey(""))
        assertNull(parseDateKey("2026-11"))
        assertNull(parseDateKey("a-b-c"))
    }

    @Test fun parseTimeKeyRoundTrip() {
        assertEquals(ClockTime(10, 30), parseTimeKey("10:30"))
        assertNull(parseTimeKey(null))
        assertNull(parseTimeKey(""))
        assertNull(parseTimeKey("10"))
        assertNull(parseTimeKey("a:b"))
    }

    @Test fun plusDaysCrossesMonthAndYear() {
        assertEquals(NibDate(2026, 11, 5), NibDate(2026, 11, 4).plusDays(1))
        assertEquals(NibDate(2027, 1, 1), NibDate(2026, 12, 31).plusDays(1))
        assertEquals(NibDate(2026, 10, 31), NibDate(2026, 11, 4).plusDays(-4))
    }

    @Test fun monthEndOverflowNormalizes() {
        assertEquals(NibDate(2026, 3, 1), NibDate(2026, 2, 29).normalized())
        assertEquals(NibDate(2028, 2, 29), NibDate(2028, 2, 29).normalized())
        assertEquals(NibDate(2027, 2, 28), NibDate(2027, 1, 31).plusDays(28))
    }

    @Test fun dayOfWeekMatchesDart() {
        assertEquals(3, NibDate(2026, 11, 4).dayOfWeek)
        assertEquals(1, NibDate(2026, 11, 2).dayOfWeek)
        assertEquals(0, NibDate(2026, 11, 8).dayOfWeek)
    }

    @Test fun epochDayRoundTrip() {
        val date = NibDate(2026, 11, 4)
        assertEquals(date, NibDate.fromEpochDay(date.epochDay))
    }

    @Test fun leapYearRule() {
        assertEquals(true, isLeapYear(2024))
        assertEquals(true, isLeapYear(2000))
        assertEquals(false, isLeapYear(2100))
        assertEquals(false, isLeapYear(2026))
    }

    @Test fun monthLengths() {
        assertEquals(31, daysInMonth(2026, 1))
        assertEquals(30, daysInMonth(2026, 4))
        assertEquals(28, daysInMonth(2026, 2))
        assertEquals(29, daysInMonth(2024, 2))
        assertEquals(31, daysInMonth(2026, 12))
        assertEquals(30, daysInMonth(2026, 13))
    }

    @Test fun clampDayKeepsWithinMonth() {
        assertEquals(28, clampDay(2026, 2, 31))
        assertEquals(29, clampDay(2024, 2, 31))
        assertEquals(30, clampDay(2026, 4, 31))
        assertEquals(1, clampDay(2026, 4, 0))
        assertEquals(15, clampDay(2026, 4, 15))
    }
}
