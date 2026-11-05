package com.yourname.nib.core.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

class AlarmScheduler(private val context: Context) {
    private val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

    fun reschedule(plans: List<ReminderPlan>) {
        for (plan in plans) {
            val pending = reminderPending(plan.entryId, plan.title, plan.id)
            if (canScheduleExact()) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, plan.at, pending)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, plan.at, pending)
            }
        }
    }

    fun cancel(entryId: String) {
        val pending = reminderPending(entryId, "", notificationId(entryId))
        alarms.cancel(pending)
        pending.cancel()
    }

    private fun reminderPending(entryId: String, title: String, code: Int): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .putExtra(EXTRA_ENTRY_ID, entryId)
            .putExtra(EXTRA_TITLE, title)
        return PendingIntent.getBroadcast(
            context,
            code,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
