package com.yourname.nib.core.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.content.ContextCompat

private const val ACCOUNT_NAME = "nib"
private const val CALENDAR_NAME = "nib_local"
private const val CALENDAR_DISPLAY_NAME = "落笔"

data class CalendarTarget(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val accountType: String,
    val access: Int,
    val visible: Boolean,
    val primary: Boolean,
)

data class WriteOutcome(val id: Long? = null, val error: String? = null)

data class SyncReport(
    val calendarName: String? = null,
    val created: Int = 0,
    val updated: Int = 0,
    val removed: Int = 0,
    val failed: Int = 0,
    val error: String? = null,
    val note: String? = null,
)

object NibCalendar {
    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun hasReadPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun resolveTarget(context: Context): Pair<CalendarTarget?, String?> {
        if (!hasReadPermission(context)) return null to "未授予日历读取权限"
        if (!hasPermission(context)) return null to "未授予日历写入权限"
        val own = runCatching { ensureOwnCalendar(context) }
        own.getOrNull()?.let { return it to null }
        val ownError = own.exceptionOrNull()?.let { failure ->
            failure.message ?: failure.javaClass.simpleName
        } ?: "无法创建「落笔」本地日历"
        val writable = writableCalendars(context)
        val target = writable.firstOrNull { it.visible } ?: writable.firstOrNull()
        return if (target == null) {
            null to ownError
        } else {
            target to "自建日历不可用（$ownError），已改用系统日历「${target.displayName}」"
        }
    }

    fun writableCalendars(context: Context): List<CalendarTarget> {
        if (!hasReadPermission(context)) return emptyList()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.VISIBLE,
            CalendarContract.Calendars.IS_PRIMARY,
        )
        return runCatching {
            context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, projection, null, null, null)?.use { cursor ->
                val indexes = projection.map { cursor.getColumnIndex(it) }
                if (indexes[0] < 0) return@use emptyList<CalendarTarget>()
                buildList {
                    while (cursor.moveToNext()) {
                        val access = if (indexes[4] >= 0) cursor.getInt(indexes[4]) else CalendarContract.Calendars.CAL_ACCESS_OWNER
                        if (access < CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) continue
                        add(
                            CalendarTarget(
                                id = cursor.getLong(indexes[0]),
                                displayName = if (indexes[1] >= 0) cursor.getString(indexes[1]).orEmpty() else "",
                                accountName = if (indexes[2] >= 0) cursor.getString(indexes[2]).orEmpty() else "",
                                accountType = if (indexes[3] >= 0) cursor.getString(indexes[3]).orEmpty() else "",
                                access = access,
                                visible = indexes[5] < 0 || cursor.getInt(indexes[5]) == 1,
                                primary = indexes[6] >= 0 && cursor.getInt(indexes[6]) == 1,
                            ),
                        )
                    }
                }.sortedWith(
                    compareByDescending<CalendarTarget> { it.primary }
                        .thenByDescending { it.visible }
                        .thenByDescending { it.access },
                )
            }
        }.getOrNull().orEmpty()
    }

    private fun ensureOwnCalendar(context: Context): CalendarTarget {
        val projection = arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
        val selection = "${CalendarContract.Calendars.ACCOUNT_TYPE} = ? AND ${CalendarContract.Calendars.NAME} = ?"
        val args = arrayOf(CalendarContract.ACCOUNT_TYPE_LOCAL, CALENDAR_NAME)
        val existing = runCatching {
            context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, projection, selection, args, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else null
            }
        }.getOrNull()
        if (existing != null) {
            return CalendarTarget(existing, CALENDAR_DISPLAY_NAME, ACCOUNT_NAME, CalendarContract.ACCOUNT_TYPE_LOCAL, CalendarContract.Calendars.CAL_ACCESS_OWNER, true, true)
        }
        val values = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Calendars.NAME, CALENDAR_NAME)
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CALENDAR_DISPLAY_NAME)
            put(CalendarContract.Calendars.CALENDAR_COLOR, 0xFF4F6BED.toInt())
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, ACCOUNT_NAME)
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.SYNC_EVENTS, 0)
        }
        val outcome = runCatching { context.contentResolver.insert(syncAdapterUri(), values) }
        val uri = outcome.getOrNull()
        val failure = outcome.exceptionOrNull()
        if (uri == null) {
            throw IllegalStateException(failure?.let { "${it.javaClass.simpleName}: ${it.message.orEmpty()}" } ?: "provider 返回空")
        }
        val id = uri.lastPathSegment?.toLongOrNull() ?: throw IllegalStateException("provider 未返回日历 id")
        return CalendarTarget(id, CALENDAR_DISPLAY_NAME, ACCOUNT_NAME, CalendarContract.ACCOUNT_TYPE_LOCAL, CalendarContract.Calendars.CAL_ACCESS_OWNER, true, true)
    }

    private fun syncAdapterUri(): Uri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
        .build()

    private fun eventSyncAdapterUri(accountName: String, accountType: String): Uri = CalendarContract.Events.CONTENT_URI.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, accountName)
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, accountType)
        .build()

    private fun contentValues(values: EventValues): ContentValues = ContentValues().apply {
        put(CalendarContract.Events.CALENDAR_ID, values.calendarId)
        put(CalendarContract.Events.TITLE, values.title)
        put(CalendarContract.Events.DESCRIPTION, values.description)
        put(CalendarContract.Events.DTSTART, values.start)
        put(CalendarContract.Events.DTEND, values.end)
        put(CalendarContract.Events.EVENT_TIMEZONE, values.timezone)
        put(CalendarContract.Events.HAS_ALARM, 1)
        put(CalendarContract.Events.EVENT_END_TIMEZONE, values.timezone)
    }

    fun insert(context: Context, values: EventValues, target: CalendarTarget): WriteOutcome {
        if (!hasPermission(context)) return WriteOutcome(error = "未授予日历写入权限")
        val attempts = listOf(
            CalendarContract.Events.CONTENT_URI,
            eventSyncAdapterUri(target.accountName, target.accountType),
        )
        var lastError = "provider 返回空"
        for (uri in attempts) {
            val outcome = runCatching { context.contentResolver.insert(uri, contentValues(values)) }
            val id = outcome.getOrNull()?.lastPathSegment?.toLongOrNull()
            if (id != null) return WriteOutcome(id = id)
            lastError = outcome.exceptionOrNull()?.let { "${it.javaClass.simpleName}: ${it.message.orEmpty()}" } ?: "provider 返回空"
        }
        return WriteOutcome(error = lastError)
    }

    fun update(context: Context, eventId: Long, values: EventValues): WriteOutcome {
        if (!hasPermission(context)) return WriteOutcome(error = "未授予日历写入权限")
        val outcome = runCatching {
            context.contentResolver.update(
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
                contentValues(values),
                null,
                null,
            )
        }
        val rows = outcome.getOrNull()
        if (rows != null && rows > 0) return WriteOutcome(id = eventId)
        return WriteOutcome(error = outcome.exceptionOrNull()?.message ?: "更新无匹配日程")
    }

    fun delete(context: Context, eventId: Long): WriteOutcome {
        if (!hasPermission(context)) return WriteOutcome(error = "未授予日历写入权限")
        val outcome = runCatching {
            context.contentResolver.delete(
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
                null,
                null,
            )
        }
        if (outcome.isSuccess) return WriteOutcome(id = eventId)
        return WriteOutcome(error = outcome.exceptionOrNull()?.message ?: "删除失败")
    }

    fun snapshot(context: Context, eventId: Long): EventSnapshot? {
        if (!hasReadPermission(context)) return null
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
        )
        return runCatching {
            context.contentResolver.query(
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
                projection,
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val start = cursor.getLong(2)
                val end = if (cursor.isNull(3)) start + 60_000L else cursor.getLong(3)
                EventSnapshot(cursor.getLong(0), cursor.getString(1) ?: "", start, maxOf(end, start + 60_000L))
            }
        }.getOrNull()
    }

    fun agenda(context: Context, windowStart: Long, windowEnd: Long): AgendaResult {
        if (!hasReadPermission(context)) return AgendaResult(error = "未授予日历读取权限")
        return runCatching { queryEvents(context, windowStart, windowEnd) }
            .fold(
                onSuccess = { AgendaResult(it.sortedBy { item -> item.start }) },
                onFailure = { AgendaResult(error = it.message?.take(120) ?: it.javaClass.simpleName) },
            )
    }

    private fun countRows(context: Context, uri: Uri, projection: Array<String>): Int =
        context.contentResolver.query(uri, projection, null, null, null)?.use { it.count } ?: 0

    fun calendarCount(context: Context): Int {
        if (!hasReadPermission(context)) return 0
        return runCatching { countRows(context, CalendarContract.Calendars.CONTENT_URI, arrayOf(CalendarContract.Calendars._ID)) }
            .getOrDefault(0)
    }

    fun diagnose(context: Context, windowStart: Long, windowEnd: Long): List<Pair<String, String>> {
        val read = hasReadPermission(context)
        val write = hasPermission(context)
        val calendars = runCatching {
            countRows(context, CalendarContract.Calendars.CONTENT_URI, arrayOf(CalendarContract.Calendars._ID))
        }.fold({ it }, { -1 })
        val writable = writableCalendars(context)
        val events = runCatching {
            countRows(context, CalendarContract.Events.CONTENT_URI, arrayOf(CalendarContract.Events._ID))
        }.fold({ it }, { -1 })
        val window = agenda(context, windowStart, windowEnd)
        val target = if (write) runCatching { resolveTarget(context) }.getOrNull() else null
        return listOf(
            "读取权限" to if (read) "已授予" else "未授予",
            "写入权限" to if (write) "已授予" else "未授予",
            "日历总数" to if (calendars < 0) "查询失败" else "$calendars 个",
            "可写日历" to if (writable.isEmpty()) "无" else "${writable.size} 个（${writable.first().displayName}）",
            "事件总数" to if (events < 0) "查询失败" else "$events 条",
            "本月读到" to (window.error?.let { "失败：$it" } ?: "${window.items.size} 条"),
            "写入目标" to (target?.first?.let { "${it.displayName} #${it.id}" } ?: (target?.second ?: "未授予写入权限")),
        )
    }

    private fun queryEvents(context: Context, windowStart: Long, windowEnd: Long): List<AgendaItem> {
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.DURATION,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.RRULE,
            CalendarContract.Events.RDATE,
            CalendarContract.Events.EXDATE,
        )
        val visible = allCalendarIds(context)
        val selection = buildString {
            append("${CalendarContract.Events.DELETED} = 0")
            append(" AND ${CalendarContract.Events.DTSTART} IS NOT NULL")
            append(" AND ${CalendarContract.Events.DTSTART} < ?")
            append(" AND (${CalendarContract.Events.DTEND} > ?")
            append(" OR ${CalendarContract.Events.DTEND} IS NULL")
            append(" OR ${CalendarContract.Events.DURATION} IS NOT NULL")
            append(" OR ${CalendarContract.Events.RRULE} IS NOT NULL)")
            if (!visible.isNullOrEmpty()) {
                append(" AND ${CalendarContract.Events.CALENDAR_ID} IN (${visible.joinToString(",")})")
            }
        }
        val args = arrayOf(windowEnd.toString(), windowStart.toString())
        val items = ArrayList<AgendaItem>()
        context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            selection,
            args,
            "${CalendarContract.Events.DTSTART} ASC",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndex(CalendarContract.Events._ID)
            val titleIndex = cursor.getColumnIndex(CalendarContract.Events.TITLE)
            val startIndex = cursor.getColumnIndex(CalendarContract.Events.DTSTART)
            val endIndex = cursor.getColumnIndex(CalendarContract.Events.DTEND)
            val durationIndex = cursor.getColumnIndex(CalendarContract.Events.DURATION)
            val allDayIndex = cursor.getColumnIndex(CalendarContract.Events.ALL_DAY)
            val ruleIndex = cursor.getColumnIndex(CalendarContract.Events.RRULE)
            val rdateIndex = cursor.getColumnIndex(CalendarContract.Events.RDATE)
            val exdateIndex = cursor.getColumnIndex(CalendarContract.Events.EXDATE)
            if (idIndex < 0 || startIndex < 0) return emptyList()
            while (cursor.moveToNext()) {
                if (cursor.isNull(startIndex)) continue
                val start = cursor.getLong(startIndex)
                val allDay = allDayIndex >= 0 && cursor.getInt(allDayIndex) == 1
                val end = if (endIndex < 0 || cursor.isNull(endIndex)) {
                    val minutes = if (durationIndex >= 0 && !cursor.isNull(durationIndex)) {
                        parseDurationMinutes(cursor.getString(durationIndex))
                    } else {
                        null
                    }
                    if (minutes != null) start + minutes * 60_000L else start + if (allDay) 86_400_000L else 3_600_000L
                } else {
                    cursor.getLong(endIndex)
                }
                items += expandEvent(
                    eventId = cursor.getLong(idIndex),
                    title = if (titleIndex < 0 || cursor.isNull(titleIndex)) "" else cursor.getString(titleIndex).orEmpty(),
                    start = start,
                    end = maxOf(end, start + 60_000L),
                    allDay = allDay,
                    rule = parseRecurrence(if (ruleIndex < 0 || cursor.isNull(ruleIndex)) null else cursor.getString(ruleIndex)),
                    extraDates = parseRecurrenceDates(if (rdateIndex < 0 || cursor.isNull(rdateIndex)) null else cursor.getString(rdateIndex)),
                    skipDates = parseRecurrenceDates(if (exdateIndex < 0 || cursor.isNull(exdateIndex)) null else cursor.getString(exdateIndex)),
                    windowStart = windowStart,
                    windowEnd = windowEnd,
                )
            }
        }
        return items
    }

    private fun allCalendarIds(context: Context): List<Long>? = runCatching {
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID),
            "${CalendarContract.Calendars.DELETED} = 0",
            null,
            null,
        )?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getLong(0))
            }
        }
    }.getOrNull()?.takeIf { it.isNotEmpty() }
}
