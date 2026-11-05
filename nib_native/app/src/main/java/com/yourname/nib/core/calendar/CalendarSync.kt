package com.yourname.nib.core.calendar

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import com.yourname.nib.core.CrashCatcher
import com.yourname.nib.core.db.NibRepository
import com.yourname.nib.core.reminder.leadMinutesFor
import com.yourname.nib.core.reminder.localMillis
import com.yourname.nib.core.reminder.remindAtFor
import com.yourname.nib.core.time.NibDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.TimeZone

object CalendarSync {
    private val scope = CoroutineScope(Dispatchers.IO)

    @Volatile
    private var suppressUntil = 0L

    @Volatile
    private var lastFired = 0L

    fun push(context: Context, repo: NibRepository, onDone: (SyncReport) -> Unit = {}) {
        scope.launch {
            val report = runCatching { pushAll(context.applicationContext, repo) }
                .getOrElse { failure ->
                    CrashCatcher.record(failure)
                    SyncReport(error = failure.message?.take(120) ?: failure.javaClass.simpleName)
                }
            onDone(report)
        }
    }

    fun pull(context: Context, repo: NibRepository, onDone: () -> Unit = {}) {
        scope.launch {
            runCatching { pullAll(context.applicationContext, repo) }
            onDone()
        }
    }

    fun agenda(context: Context, from: NibDate, to: NibDate): AgendaResult =
        NibCalendar.agenda(
            context.applicationContext,
            localMillis(from, 0, 0),
            localMillis(to, 0, 0),
        )

    fun agendaToday(context: Context): AgendaResult {
        val today = NibDate.today()
        return agenda(context, today, today.plusDays(1))
    }

    fun calendarCount(context: Context): Int = NibCalendar.calendarCount(context.applicationContext)

    fun diagnose(context: Context, from: NibDate, to: NibDate): List<Pair<String, String>> =
        NibCalendar.diagnose(context.applicationContext, localMillis(from, 0, 0), localMillis(to, 0, 0))

    fun observe(context: Context, onChange: () -> Unit): ContentObserver? {
        if (!NibCalendar.hasReadPermission(context)) return null
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                val now = System.currentTimeMillis()
                if (now < suppressUntil || now - lastFired < 600) return
                lastFired = now
                onChange()
            }
        }
        return runCatching {
            context.applicationContext.contentResolver.registerContentObserver(
                CalendarContract.Events.CONTENT_URI,
                true,
                observer,
            )
            observer
        }.getOrNull()
    }

    private suspend fun pushAll(context: Context, repo: NibRepository): SyncReport {
        if (!NibCalendar.hasReadPermission(context)) return SyncReport(error = "未授予日历读取权限")
        if (!NibCalendar.hasPermission(context)) return SyncReport(error = "未授予日历写入权限")
        val (target, note) = NibCalendar.resolveTarget(context)
        if (target == null) return SyncReport(error = note ?: "没有可写入的系统日历")
        val zone = TimeZone.getDefault().id
        suppressUntil = System.currentTimeMillis() + 1500
        var created = 0
        var updated = 0
        var removed = 0
        var failed = 0
        var firstError: String? = null
        for (entry in repo.allEntriesIncludingDeleted()) {
            val values = eventValuesFor(entry, target.id, zone)
            val eventId = entry.calendarEventId
            if (values == null) {
                if (eventId != null) {
                    val outcome = NibCalendar.delete(context, eventId)
                    if (outcome.error == null) {
                        repo.setCalendarEventId(entry.id, null)
                        removed++
                    }
                }
                continue
            }
            if (eventId == null) {
                val outcome = NibCalendar.insert(context, values, target)
                if (outcome.id != null) {
                    repo.setCalendarEventId(entry.id, outcome.id)
                    created++
                } else {
                    failed++
                    if (firstError == null) firstError = outcome.error
                }
            } else {
                val outcome = NibCalendar.update(context, eventId, values)
                if (outcome.error == null) {
                    updated++
                } else {
                    val recreated = NibCalendar.insert(context, values, target)
                    if (recreated.id != null) {
                        repo.setCalendarEventId(entry.id, recreated.id)
                        created++
                    } else {
                        failed++
                        if (firstError == null) firstError = recreated.error ?: outcome.error
                    }
                }
            }
        }
        return SyncReport(
            calendarName = target.displayName,
            created = created,
            updated = updated,
            removed = removed,
            failed = failed,
            error = if (failed > 0) "写入失败 $failed 条：${firstError.orEmpty()}".take(160) else null,
            note = note,
        )
    }

    private suspend fun pullAll(context: Context, repo: NibRepository) {
        if (!NibCalendar.hasReadPermission(context)) return
        for (entry in repo.entriesWithCalendarEvents()) {
            val eventId = entry.calendarEventId ?: continue
            val snapshot = NibCalendar.snapshot(context, eventId) ?: continue
            val change = calendarChange(entry, snapshot) ?: continue
            val lead = leadMinutesFor(entry.dueDate, entry.dueTime, entry.remindAt)
            val remindAt = remindAtFor(change.dueDate, change.dueTime, lead)
            repo.applyCalendarChange(
                entry.id,
                change.dueDate,
                change.dueTime,
                change.durationMin,
                remindAt,
                change.fields,
            )
        }
    }
}

class CalendarBridge(private val context: Context, private val repo: NibRepository) {
    fun push(onDone: (SyncReport) -> Unit = {}) = CalendarSync.push(context, repo, onDone)

    fun pull(onDone: () -> Unit = {}) = CalendarSync.pull(context, repo, onDone)

    fun agenda(from: NibDate, to: NibDate): AgendaResult = CalendarSync.agenda(context, from, to)

    fun agenda(): AgendaResult = CalendarSync.agendaToday(context)

    fun calendarCount(): Int = CalendarSync.calendarCount(context)

    fun diagnose(from: NibDate, to: NibDate): List<Pair<String, String>> = CalendarSync.diagnose(context, from, to)

    fun observe(onChange: () -> Unit): ContentObserver? = CalendarSync.observe(context, onChange)

    fun unobserve(observer: ContentObserver) {
        runCatching { context.applicationContext.contentResolver.unregisterContentObserver(observer) }
    }
}
