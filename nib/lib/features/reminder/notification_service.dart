import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:flutter_timezone/flutter_timezone.dart';
import 'package:timezone/data/latest_all.dart' as tzdata;
import 'package:timezone/timezone.dart' as tz;

import 'reminder_actions.dart';

const reminderChannelId = 'nib_reminders';
const reminderChannelName = '落笔提醒';

final reminderPlugin = FlutterLocalNotificationsPlugin();

const reminderDetails = NotificationDetails(
  android: AndroidNotificationDetails(
    reminderChannelId,
    reminderChannelName,
    channelDescription: '待办与备忘提醒',
    importance: Importance.max,
    priority: Priority.high,
    actions: <AndroidNotificationAction>[
      AndroidNotificationAction('done', '完成'),
      AndroidNotificationAction('snooze10', '10 分钟后'),
      AndroidNotificationAction('snooze60', '1 小时后'),
      AndroidNotificationAction('snoozeTomorrow', '明天'),
    ],
  ),
);

Future<void> initReminders() async {
  tzdata.initializeTimeZones();
  try {
    final info = await FlutterTimezone.getLocalTimezone();
    tz.setLocalLocation(tz.getLocation(info.identifier));
  } catch (_) {
    tz.setLocalLocation(tz.UTC);
  }
  await reminderPlugin.initialize(
    settings: const InitializationSettings(
      android: AndroidInitializationSettings('@mipmap/ic_launcher'),
    ),
    onDidReceiveBackgroundNotificationResponse: reminderActionBackground,
  );
}

AndroidFlutterLocalNotificationsPlugin? get androidPlugin => reminderPlugin
    .resolvePlatformSpecificImplementation<
        AndroidFlutterLocalNotificationsPlugin>();

Future<bool> canScheduleExact() async =>
    await androidPlugin?.canScheduleExactNotifications() ?? false;

Future<void> requestExactAlarms() async =>
    androidPlugin?.requestExactAlarmsPermission();
