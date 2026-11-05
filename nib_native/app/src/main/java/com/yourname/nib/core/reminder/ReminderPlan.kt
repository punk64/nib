package com.yourname.nib.core.reminder

import com.yourname.nib.core.db.Entry
import com.yourname.nib.core.time.NibDate
import com.yourname.nib.core.time.parseDateKey
import com.yourname.nib.core.time.parseTimeKey
import java.util.Calendar

data class ReminderPlan(val id: Int, val at: Long, val title: String, val entryId: String)

const val ACTION_DONE = "done"
const val ACTION_SNOOZE_10 = "snooze10"
const val ACTION_SNOOZE_60 = "snooze60"
const val ACTION_SNOOZE_TOMORROW = "snoozeTomorrow"

fun notificationId(entryId: String): Int {
    var hash = 0
    for (ch in entryId) hash = (hash * 31 + ch.code) and 0x3FFFFFFF
    return hash
}

fun localMillis(date: NibDate, hour: Int, minute: Int): Long {
    val cal = Calendar.getInstance()
    cal.set(Calendar.YEAR, date.year)
    cal.set(Calendar.MONTH, date.month - 1)
    cal.set(Calendar.DAY_OF_MONTH, date.day)
    cal.set(Calendar.HOUR_OF_DAY, hour)
    cal.set(Calendar.MINUTE, minute)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

fun dueMillis(due: String?, time: String?): Long? {
    val date = parseDateKey(due) ?: return null
    val clock = parseTimeKey(time) ?: return null
    return localMillis(date, clock.hour, clock.minute)
}

fun remindAtFor(due: String?, time: String?, leadMinutes: Int): Long? =
    dueMillis(due, time)?.let { it - leadMinutes * 60_000L }

fun leadMinutesFor(due: String?, time: String?, remindAt: Long?): Int {
    val base = dueMillis(due, time) ?: return 30
    if (remindAt == null) return 30
    return ((base - remindAt) / 60_000L).toInt().coerceIn(-1, 60)
}

fun buildPlans(entries: List<Entry>, nowMs: Long): List<ReminderPlan> {
    val plans = mutableListOf<ReminderPlan>()
    for (entry in entries) {
        if (entry.done != 0 || entry.deletedAt != null || entry.channel != "notify") continue
        val snoozed = entry.snoozeUntil
        val at = if (snoozed != null && snoozed > nowMs) snoozed else entry.remindAt
        if (at == null || at <= nowMs) continue
        plans.add(ReminderPlan(notificationId(entry.id), at, entry.title, entry.id))
    }
    return plans.sortedBy { it.at }
}

fun snoozeTarget(nowMs: Long, actionId: String?): Long? = when (actionId) {
    ACTION_SNOOZE_10 -> nowMs + 10 * 60_000L
    ACTION_SNOOZE_60 -> nowMs + 60 * 60_000L
    ACTION_SNOOZE_TOMORROW -> {
        val cal = Calendar.getInstance()
        cal.timeInMillis = nowMs
        cal.add(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 9)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.timeInMillis
    }
    else -> null
}

fun advanceDue(due: NibDate, freq: String, n: Int): NibDate? {
    val gap = if (n < 1) 1 else n
    return when (freq) {
        "day" -> due.plusDays(gap.toLong())
        "week" -> due.plusDays(7L * gap)
        "month" -> advanceMonth(due, gap)
        "year" -> advanceMonth(due, gap * 12)
        else -> null
    }
}

private fun advanceMonth(due: NibDate, months: Int): NibDate {
    val total = due.year * 12 + due.month - 1 + months
    val year = Math.floorDiv(total, 12)
    val month = Math.floorMod(total, 12) + 1
    val lastDay = NibDate(year, month + 1, 0).normalized().day
    return NibDate(year, month, minOf(due.day, lastDay))
}
