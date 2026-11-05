package com.yourname.nib.core.calendar

import com.yourname.nib.core.db.Entry
import com.yourname.nib.core.reminder.localMillis
import com.yourname.nib.core.time.NibDate
import com.yourname.nib.core.time.parseDateKey
import com.yourname.nib.core.time.parseTimeKey
import com.yourname.nib.core.time.twoDigits
import java.util.Calendar
import java.util.TimeZone

const val CHANNEL_NOTIFY = "notify"
const val CHANNEL_CALENDAR = "calendar"

private const val DAY_MS = 86_400_000L
private const val MIN_EVENT_MS = 60_000L
private const val MAX_WALK_DAYS = 1_200

data class EventValues(
    val calendarId: Long,
    val title: String,
    val description: String,
    val start: Long,
    val end: Long,
    val timezone: String,
)

data class EventSnapshot(
    val eventId: Long,
    val title: String,
    val start: Long,
    val end: Long,
)

data class CalendarChange(
    val dueDate: String,
    val dueTime: String,
    val durationMin: Int,
    val fields: List<String>,
)

data class AgendaItem(
    val eventId: Long,
    val title: String,
    val start: Long,
    val end: Long,
    val allDay: Boolean = false,
)

data class AgendaResult(
    val items: List<AgendaItem> = emptyList(),
    val error: String? = null,
)

data class RecurrenceSpec(
    val freq: String,
    val interval: Int,
    val count: Int?,
    val until: Long?,
    val byDay: Set<Int>,
)

fun suggestChannel(dueTime: String?, durationMin: Int): String =
    if (!dueTime.isNullOrEmpty() && durationMin > 0) CHANNEL_CALENDAR else CHANNEL_NOTIFY

fun eventEnd(start: Long, durationMin: Int): Long = start + durationMin.coerceAtLeast(1) * 60_000L

fun eventValuesFor(entry: Entry, calendarId: Long, timezone: String): EventValues? {
    if (entry.channel != CHANNEL_CALENDAR || entry.deletedAt != null || entry.done != 0) return null
    if (entry.durationMin <= 0) return null
    val date = parseDateKey(entry.dueDate) ?: return null
    val clock = parseTimeKey(entry.dueTime) ?: return null
    val start = localMillis(date, clock.hour, clock.minute)
    return EventValues(calendarId, entry.title, entry.body, start, eventEnd(start, entry.durationMin), timezone)
}

fun dateTimeKeys(millis: Long): Pair<String, String> {
    val cal = Calendar.getInstance()
    cal.timeInMillis = millis
    val date = NibDate(
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH) + 1,
        cal.get(Calendar.DAY_OF_MONTH),
    ).dateKey()
    val time = "${twoDigits(cal.get(Calendar.HOUR_OF_DAY))}:${twoDigits(cal.get(Calendar.MINUTE))}"
    return date to time
}

fun dateKeyOf(millis: Long, utc: Boolean): String {
    val cal = Calendar.getInstance(if (utc) TimeZone.getTimeZone("UTC") else TimeZone.getDefault())
    cal.timeInMillis = millis
    return NibDate(
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH) + 1,
        cal.get(Calendar.DAY_OF_MONTH),
    ).dateKey()
}

fun agendaDayKey(item: AgendaItem): String = dateKeyOf(item.start, item.allDay)

fun agendaClock(item: AgendaItem): String = if (item.allDay) "全天" else dateTimeKeys(item.start).second

fun agendaEndClock(item: AgendaItem): String = if (item.allDay) "全天" else dateTimeKeys(item.end).second

fun calendarChange(entry: Entry, snapshot: EventSnapshot): CalendarChange? {
    val (date, time) = dateTimeKeys(snapshot.start)
    val duration = ((snapshot.end - snapshot.start) / 60_000L).toInt().coerceAtLeast(1)
    val fields = buildList {
        if (entry.dueDate != date) add("due_date")
        if (entry.dueTime != time) add("due_time")
        if (entry.durationMin != duration) add("duration_min")
    }
    return if (fields.isEmpty()) null else CalendarChange(date, time, duration, fields)
}

fun agendaForDay(events: List<AgendaItem>, dayStart: Long, dayEnd: Long): List<AgendaItem> =
    events.filter { it.end > dayStart && it.start < dayEnd }.sortedBy { it.start }

fun parseDurationMinutes(text: String?): Int? {
    val raw = text?.trim().orEmpty()
    if (raw.length < 2 || !raw.startsWith("P")) return null
    var days = 0
    var hours = 0
    var minutes = 0
    var number = ""
    var inTime = false
    for (ch in raw.drop(1)) {
        when {
            ch == 'T' -> inTime = true
            ch.isDigit() -> number += ch
            ch == 'W' -> {
                days += (number.toIntOrNull() ?: 0) * 7
                number = ""
            }
            ch == 'D' -> {
                days += number.toIntOrNull() ?: 0
                number = ""
            }
            ch == 'H' -> {
                hours += number.toIntOrNull() ?: 0
                number = ""
            }
            ch == 'M' && inTime -> {
                minutes += number.toIntOrNull() ?: 0
                number = ""
            }
            ch == 'S' -> {
                minutes += ((number.toIntOrNull() ?: 0) + 59) / 60
                number = ""
            }
            else -> number = ""
        }
    }
    return (days * 1440 + hours * 60 + minutes).takeIf { it > 0 }
}

fun parseRecurrence(rule: String?): RecurrenceSpec? {
    val text = rule?.trim().orEmpty()
    if (text.isEmpty()) return null
    var freq = ""
    var interval = 1
    var count: Int? = null
    var until: Long? = null
    val byDay = mutableSetOf<Int>()
    for (part in text.split(';')) {
        val idx = part.indexOf('=')
        if (idx <= 0) continue
        val key = part.substring(0, idx).trim().uppercase()
        val value = part.substring(idx + 1).trim()
        when (key) {
            "FREQ" -> freq = value.uppercase()
            "INTERVAL" -> interval = value.toIntOrNull()?.coerceIn(1, 999) ?: 1
            "COUNT" -> count = value.toIntOrNull()?.coerceAtLeast(0)
            "UNTIL" -> until = parseUntil(value)
            "BYDAY" -> for (token in value.split(',')) {
                weekdayOf(token)?.let { byDay.add(it) }
            }
        }
    }
    if (freq !in RECURRENCE_FREQS) return null
    return RecurrenceSpec(freq, interval, count, until, byDay)
}

fun parseRecurrenceDates(text: String?): List<Long> {
    val raw = text?.trim().orEmpty()
    if (raw.isEmpty()) return emptyList()
    return raw.split(',').mapNotNull { parseUntil(it.trim()) }
}

fun expandEvent(
    eventId: Long,
    title: String,
    start: Long,
    end: Long,
    allDay: Boolean,
    rule: RecurrenceSpec?,
    extraDates: List<Long> = emptyList(),
    skipDates: List<Long> = emptyList(),
    windowStart: Long,
    windowEnd: Long,
): List<AgendaItem> {
    if (windowEnd <= windowStart) return emptyList()
    val zone = TimeZone.getTimeZone(if (allDay) "UTC" else TimeZone.getDefault().id)
    val length = (end - start).coerceAtLeast(if (allDay) DAY_MS else MIN_EVENT_MS)
    val out = ArrayList<AgendaItem>()
    val seen = HashSet<Long>()

    fun overflow(occurrenceStart: Long) = occurrenceStart + length

    fun emit(occurrenceStart: Long) {
        val occurrenceEnd = overflow(occurrenceStart)
        if (occurrenceEnd <= windowStart || occurrenceStart >= windowEnd) return
        if (!seen.add(occurrenceStart)) return
        out.add(AgendaItem(eventId, title, occurrenceStart, occurrenceEnd, allDay))
    }

    fun skipped(occurrenceStart: Long): Boolean = skipDates.any { dateKeyOf(it, allDay) == dateKeyOf(occurrenceStart, allDay) }

    val cal = Calendar.getInstance(zone)
    cal.timeInMillis = start
    val startYear = cal.get(Calendar.YEAR)
    val startMonth = cal.get(Calendar.MONTH)
    val startDayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
    val startWeekday = cal.get(Calendar.DAY_OF_WEEK)

    var cursorDay = 0
    if (rule != null && start < windowStart - DAY_MS) {
        val gap = ((windowStart - start) / DAY_MS).toInt().coerceAtLeast(0)
        cursorDay = when (rule.freq) {
            "DAILY" -> (gap / rule.interval) * rule.interval
            "WEEKLY" -> ((gap / 7) / rule.interval) * rule.interval * 7
            "MONTHLY", "YEARLY" -> (gap - 35).coerceAtLeast(0)
            else -> 0
        }
        cal.add(Calendar.DAY_OF_MONTH, cursorDay)
    }

    val walkLimit = cursorDay + MAX_WALK_DAYS
    var occurrenceIndex = 0
    while (cal.timeInMillis < windowEnd && cursorDay <= walkLimit) {
        val hit = when {
            rule == null -> cursorDay == 0
            rule.freq == "DAILY" -> cursorDay % rule.interval == 0
            rule.freq == "WEEKLY" -> {
                val weekday = cal.get(Calendar.DAY_OF_WEEK)
                val inWeek = cursorDay / 7 % rule.interval == 0
                val dayMatch = if (rule.byDay.isEmpty()) weekday == startWeekday else weekday in rule.byDay
                inWeek && dayMatch
            }
            rule.freq == "MONTHLY" -> {
                val months = (cal.get(Calendar.YEAR) - startYear) * 12 + (cal.get(Calendar.MONTH) - startMonth)
                cal.get(Calendar.DAY_OF_MONTH) == startDayOfMonth && months % rule.interval == 0
            }
            rule.freq == "YEARLY" -> {
                val years = cal.get(Calendar.YEAR) - startYear
                cal.get(Calendar.DAY_OF_MONTH) == startDayOfMonth &&
                    cal.get(Calendar.MONTH) == startMonth &&
                    years % rule.interval == 0
            }
            else -> cursorDay == 0
        }
        if (hit) {
            occurrenceIndex++
            if (rule?.count != null && occurrenceIndex > rule.count) break
            if (rule?.until != null && cal.timeInMillis > rule.until) break
            val occurrenceStart = cal.timeInMillis
            if (!skipped(occurrenceStart)) emit(occurrenceStart)
        }
        if (rule == null) break
        if (rule.count != null && occurrenceIndex >= rule.count) break
        if (rule.until != null && cal.timeInMillis >= rule.until) break
        cal.add(Calendar.DAY_OF_MONTH, 1)
        cursorDay++
    }

    for (extra in extraDates) {
        if (!skipped(extra)) emit(extra)
    }
    out.sortBy { it.start }
    return out
}

private val RECURRENCE_FREQS = setOf("DAILY", "WEEKLY", "MONTHLY", "YEARLY")

private fun weekdayOf(token: String): Int? {
    val name = token.filter { it.isLetter() }.takeLast(2).uppercase()
    return when (name) {
        "SU" -> Calendar.SUNDAY
        "MO" -> Calendar.MONDAY
        "TU" -> Calendar.TUESDAY
        "WE" -> Calendar.WEDNESDAY
        "TH" -> Calendar.THURSDAY
        "FR" -> Calendar.FRIDAY
        "SA" -> Calendar.SATURDAY
        else -> null
    }
}

private fun parseUntil(text: String): Long? {
    val digits = text.filter { it.isDigit() }
    if (digits.length < 8) return null
    val year = digits.substring(0, 4).toIntOrNull() ?: return null
    val month = digits.substring(4, 6).toIntOrNull() ?: return null
    val day = digits.substring(6, 8).toIntOrNull() ?: return null
    val hour = if (digits.length >= 10) digits.substring(8, 10).toIntOrNull() ?: 0 else 23
    val minute = if (digits.length >= 12) digits.substring(10, 12).toIntOrNull() ?: 0 else 59
    val second = if (digits.length >= 14) digits.substring(12, 14).toIntOrNull() ?: 0 else 59
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    cal.clear()
    cal.set(year, month - 1, day, hour, minute, second)
    return cal.timeInMillis
}
