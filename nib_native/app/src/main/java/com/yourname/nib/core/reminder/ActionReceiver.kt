package com.yourname.nib.core.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.yourname.nib.core.db.NibGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val entryId = intent.getStringExtra(EXTRA_ENTRY_ID) ?: return
        val action = intent.getStringExtra(EXTRA_ACTION) ?: return
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = NibGraph.repository(app)
                if (action == ACTION_DONE) {
                    repo.finishFromNotification(entryId)
                } else {
                    repo.applySnooze(entryId, snoozeTarget(System.currentTimeMillis(), action))
                }
                ReminderBootstrap.rescheduleNow(app)
                NotificationManagerCompat.from(app).cancel(notificationId(entryId))
            } finally {
                pending.finish()
            }
        }
    }
}
