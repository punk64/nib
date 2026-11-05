import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:timezone/timezone.dart' as tz;

import '../../core/db/database.dart';
import 'notification_service.dart';
import 'reminder_plan.dart';

Future<bool> rescheduleAll(AppDatabase db) async {
  final now = DateTime.now().millisecondsSinceEpoch;
  await db.clearExpiredSnoozes(now);
  final plans = buildPlans(await db.allEntries(), now);
  for (final pending in await reminderPlugin.pendingNotificationRequests()) {
    await reminderPlugin.cancel(id: pending.id);
  }
  final exact = await canScheduleExact();
  final mode = exact
      ? AndroidScheduleMode.exactAllowWhileIdle
      : AndroidScheduleMode.inexactAllowWhileIdle;
  for (final plan in plans) {
    await reminderPlugin.zonedSchedule(
      id: plan.id,
      title: plan.title,
      body: '点按查看详情，或直接从通知完成',
      scheduledDate:
          tz.TZDateTime.fromMillisecondsSinceEpoch(tz.local, plan.at),
      notificationDetails: reminderDetails,
      androidScheduleMode: mode,
      payload: plan.entryId,
    );
  }
  return exact;
}

Future<void> cancelReminder(String entryId) async =>
    reminderPlugin.cancel(id: notificationId(entryId));
