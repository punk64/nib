package com.yourname.nib.core.reminder

import android.content.Context
import com.yourname.nib.core.db.NibGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object ReminderBootstrap {
    suspend fun rescheduleNow(context: Context) {
        val entries = NibGraph.repository(context).allEntriesIncludingDeleted()
        val scheduler = AlarmScheduler(context)
        val plans = buildPlans(entries, System.currentTimeMillis())
        val active = plans.map { it.entryId }.toSet()
        for (entry in entries) {
            if (entry.id !in active) scheduler.cancel(entry.id)
        }
        scheduler.reschedule(plans)
    }

    fun reschedule(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { rescheduleNow(context.applicationContext) }
        }
    }
}
