package com.yourname.nib

import com.yourname.nib.core.db.Entry
import com.yourname.nib.core.reminder.ACTION_DONE
import com.yourname.nib.core.reminder.ACTION_SNOOZE_10
import com.yourname.nib.core.reminder.ACTION_SNOOZE_60
import com.yourname.nib.core.reminder.ACTION_SNOOZE_TOMORROW
import com.yourname.nib.core.reminder.advanceDue
import com.yourname.nib.core.reminder.buildPlans
import com.yourname.nib.core.reminder.dueMillis
import com.yourname.nib.core.reminder.leadMinutesFor
import com.yourname.nib.core.reminder.localMillis
import com.yourname.nib.core.reminder.notificationId
import com.yourname.nib.core.reminder.remindAtFor
import com.yourname.nib.core.reminder.snoozeTarget
import com.yourname.nib.core.time.NibDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun entry(
    id: String,
    remindAt: Long? = null,
    snoozeUntil: Long? = null,
    done: Int = 0,
    channel: String = "notify",
    deletedAt: Long? = null,
    title: String = "条目",
): Entry = Entry(
    id = id,
    title = title,
    sort = id,
    hlc = "h",
    deviceId = "d",
    createdAt = 0,
    updatedAt = 0,
    remindAt = remindAt,
    snoozeUntil = snoozeUntil,
    done = done,
    channel = channel,
    deletedAt = deletedAt,
)

class ReminderPlanTest {
    private val due = "2026-03-01"
    private val time = "10:00"
    private val base = dueMillis(due, time)!!
    private val now = localMillis(NibDate(2026, 3, 1), 9, 0)

    @Test
    fun notificationIdIsStableAndBounded() {
        val id = "01J8ZQ3M4N5P6Q7R8S9T0VWXY"
        assertEquals(notificationId(id), notificationId(id))
        assertTrue(notificationId(id) in 0..0x3FFFFFFF)
        assertTrue(notificationId(id) != notificationId(id + "Z"))
    }

    @Test
    fun remindAtForAppliesLead() {
        assertEquals(base - 30 * 60_000L, remindAtFor(due, time, 30))
        assertEquals(base + 60_000L, remindAtFor(due, time, -1))
        assertEquals(base, remindAtFor(due, time, 0))
    }

    @Test
    fun remindAtForRequiresBothDateAndTime() {
        assertNull(remindAtFor(null, time, 30))
        assertNull(remindAtFor(due, null, 30))
        assertNull(remindAtFor("bad-date", time, 30))
        assertNull(remindAtFor(due, "bad-time", 30))
    }

    @Test
    fun leadMinutesRoundTrips() {
        for (lead in listOf(-1, 0, 5, 15, 30, 60)) {
            assertEquals(lead, leadMinutesFor(due, time, remindAtFor(due, time, lead)))
        }
        assertEquals(30, leadMinutesFor(due, time, null))
        assertEquals(30, leadMinutesFor(null, time, base))
    }

    @Test
    fun buildPlansSkipsIneligibleEntries() {
        val all = listOf(
            entry("ok", base + 3_600_000),
            entry("done", base + 3_600_000, done = 1),
            entry("deleted", base + 3_600_000, deletedAt = 1L),
            entry("calendar", base + 3_600_000, channel = "calendar"),
            entry("past", now - 1_000),
            entry("none"),
        )
        assertEquals(listOf("ok"), buildPlans(all, now).map { it.entryId })
    }

    @Test
    fun buildPlansUsesLiveSnooze() {
        val snoozed = now + 20 * 60_000L
        val plans = buildPlans(listOf(entry("a", now + 60_000L, snoozeUntil = snoozed)), now)
        assertEquals(snoozed, plans.single().at)
    }

    @Test
    fun buildPlansFallsBackAfterExpiredSnooze() {
        val remind = now + 60_000L
        val plans = buildPlans(listOf(entry("a", remind, snoozeUntil = now - 1_000)), now)
        assertEquals(remind, plans.single().at)
    }

    @Test
    fun buildPlansSortedByTriggerTime() {
        val all = listOf(
            entry("late", now + 3 * 60_000L),
            entry("early", now + 1 * 60_000L),
            entry("middle", now + 2 * 60_000L),
        )
        assertEquals(listOf("early", "middle", "late"), buildPlans(all, now).map { it.entryId })
    }

    @Test
    fun snoozeTargetsAreAbsoluteOffsets() {
        assertEquals(now + 10 * 60_000L, snoozeTarget(now, ACTION_SNOOZE_10))
        assertEquals(now + 60 * 60_000L, snoozeTarget(now, ACTION_SNOOZE_60))
        assertNull(snoozeTarget(now, ACTION_DONE))
        assertNull(snoozeTarget(now, null))
    }

    @Test
    fun snoozeTomorrowIsNineAm() {
        assertEquals(
            localMillis(NibDate(2026, 3, 2), 9, 0),
            snoozeTarget(now, ACTION_SNOOZE_TOMORROW),
        )
    }

    @Test
    fun advanceDueHandlesDayAndWeek() {
        assertEquals(NibDate(2026, 3, 3), advanceDue(NibDate(2026, 3, 1), "day", 2))
        assertEquals(NibDate(2026, 3, 15), advanceDue(NibDate(2026, 3, 1), "week", 2))
        assertEquals(NibDate(2026, 3, 2), advanceDue(NibDate(2026, 3, 1), "day", 0))
    }

    @Test
    fun advanceDueClampsMonthEnd() {
        assertEquals(NibDate(2026, 2, 28), advanceDue(NibDate(2026, 1, 31), "month", 1))
        assertEquals(NibDate(2028, 2, 28), advanceDue(NibDate(2027, 2, 28), "year", 1))
        assertEquals(NibDate(2028, 2, 29), advanceDue(NibDate(2027, 1, 31), "month", 13))
        assertEquals(NibDate(2027, 1, 31), advanceDue(NibDate(2026, 1, 31), "year", 1))
    }

    @Test
    fun advanceDueRejectsNone() {
        assertNull(advanceDue(NibDate(2026, 3, 1), "none", 1))
        assertNull(advanceDue(NibDate(2026, 3, 1), "other", 1))
    }
}
