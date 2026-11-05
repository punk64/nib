import 'dart:async';
import 'dart:ui';

import 'package:flutter/material.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';

import '../../core/db/database.dart';
import '../../core/model/date_util.dart';
import 'reminder_plan.dart';
import 'reminder_scheduler.dart';

@pragma('vm:entry-point')
void reminderActionBackground(NotificationResponse response) {
  DartPluginRegistrant.ensureInitialized();
  unawaited(handleReminderAction(response));
}

Future<void> handleReminderAction(NotificationResponse response) async {
  final entryId = response.payload;
  if (entryId == null) return;
  final db = AppDatabase();
  try {
    final entry = await db.entryById(entryId);
    if (entry == null) return;
    if (response.actionId == 'done') {
      await _finish(db, entry);
    } else {
      final until = snoozeTarget(
        DateTime.now().millisecondsSinceEpoch,
        response.actionId,
      );
      if (until == null) return;
      await db.applySnooze(entry, until);
    }
    await rescheduleAll(db);
  } finally {
    await db.close();
  }
}

Future<void> _finish(AppDatabase db, Entry entry) async {
  final date = parseDateKey(entry.dueDate);
  final clock = parseTimeKey(entry.dueTime);
  var nextDue = entry.dueDate;
  var nextTime = entry.dueTime;
  int? nextRemindAt;
  if (entry.repeatFreq != 'none' && date != null && clock != null) {
    final current =
        DateTime(date.year, date.month, date.day, clock.hour, clock.minute);
    final next = advanceDue(current, entry.repeatFreq, entry.repeatN);
    if (next != null) {
      final lead = entry.remindAt == null
          ? const Duration(minutes: 30)
          : Duration(
              milliseconds:
                  current.millisecondsSinceEpoch - entry.remindAt!);
      nextDue = dateKey(next);
      nextTime = timeKey(TimeOfDay(hour: next.hour, minute: next.minute));
      nextRemindAt =
          next.millisecondsSinceEpoch - lead.inMilliseconds;
    }
  }
  await db.finishFromNotification(
    entry,
    nextDue: nextDue,
    nextTime: nextTime,
    nextRemindAt: nextRemindAt,
  );
}
