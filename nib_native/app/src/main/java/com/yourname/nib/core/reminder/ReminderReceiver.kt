package com.yourname.nib.core.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.yourname.nib.R

const val EXTRA_ENTRY_ID = "com.yourname.nib.entry_id"
const val EXTRA_TITLE = "com.yourname.nib.title"
const val EXTRA_ACTION = "com.yourname.nib.action"
const val CHANNEL_ID = "nib_reminders"

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val entryId = intent.getStringExtra(EXTRA_ENTRY_ID) ?: return
        val title = intent.getStringExtra(EXTRA_TITLE)?.ifEmpty { "落笔提醒" } ?: "落笔提醒"
        createChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nib_notification)
            .setContentTitle(title)
            .setContentText("该处理了")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
        listOf(
            ACTION_DONE to "完成",
            ACTION_SNOOZE_10 to "稍后 10 分钟",
            ACTION_SNOOZE_60 to "稍后 1 小时",
            ACTION_SNOOZE_TOMORROW to "明天",
        ).forEachIndexed { index, (action, label) ->
            builder.addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_nib_notification,
                    label,
                    actionPending(context, entryId, action, index + 1),
                ).build(),
            )
        }
        NotificationManagerCompat.from(context).notify(notificationId(entryId), builder.build())
    }

    private fun actionPending(context: Context, entryId: String, action: String, index: Int): PendingIntent {
        val intent = Intent(context, ActionReceiver::class.java)
            .putExtra(EXTRA_ENTRY_ID, entryId)
            .putExtra(EXTRA_ACTION, action)
        val code = (notificationId(entryId) xor (index * 7919)) and 0x3FFFFFFF
        return PendingIntent.getBroadcast(
            context,
            code,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(CHANNEL_ID, "落笔提醒", NotificationManager.IMPORTANCE_HIGH)
        manager.createNotificationChannel(channel)
    }
}
