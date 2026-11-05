import 'package:flutter/widgets.dart';

import '../../core/db/database.dart';
import 'reminder_scheduler.dart';

class ReminderLifecycle extends WidgetsBindingObserver {
  ReminderLifecycle(this.db);

  final AppDatabase db;

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) rescheduleAll(db);
  }
}

Future<void> bootstrapReminders(AppDatabase db) async {
  WidgetsBinding.instance.addObserver(ReminderLifecycle(db));
  await rescheduleAll(db);
}
