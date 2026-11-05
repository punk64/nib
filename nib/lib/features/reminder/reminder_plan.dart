import '../../core/db/database.dart';
import '../../core/model/date_util.dart';

class ReminderPlan {
  const ReminderPlan({
    required this.id,
    required this.at,
    required this.title,
    required this.entryId,
  });

  final int id;
  final int at;
  final String title;
  final String entryId;
}

int notificationId(String entryId) {
  var hash = 0;
  for (final unit in entryId.codeUnits) {
    hash = (hash * 31 + unit) & 0x3FFFFFFF;
  }
  return hash;
}

int? remindAtFor(String? due, String? time, int leadMinutes) {
  final date = parseDateKey(due);
  final clock = parseTimeKey(time);
  if (date == null || clock == null) return null;
  final at = DateTime(date.year, date.month, date.day, clock.hour, clock.minute);
  return at
      .subtract(Duration(minutes: leadMinutes))
      .millisecondsSinceEpoch;
}

List<ReminderPlan> buildPlans(List<Entry> entries, int nowMs) {
  final plans = <ReminderPlan>[];
  for (final entry in entries) {
    if (entry.done != 0 || entry.deletedAt != null) continue;
    if (entry.channel != 'notify') continue;
    final snoozed = entry.snoozeUntil;
    final at = (snoozed != null && snoozed > nowMs)
        ? snoozed
        : entry.remindAt;
    if (at == null || at <= nowMs) continue;
    plans.add(ReminderPlan(
      id: notificationId(entry.id),
      at: at,
      title: entry.title,
      entryId: entry.id,
    ));
  }
  plans.sort((a, b) => a.at.compareTo(b.at));
  return plans;
}

int? snoozeTarget(int nowMs, String? actionId) {
  final now = DateTime.fromMillisecondsSinceEpoch(nowMs);
  return switch (actionId) {
    'snooze10' => now.add(const Duration(minutes: 10)).millisecondsSinceEpoch,
    'snooze60' => now.add(const Duration(hours: 1)).millisecondsSinceEpoch,
    'snoozeTomorrow' =>
      DateTime(now.year, now.month, now.day + 1, 9).millisecondsSinceEpoch,
    _ => null,
  };
}

DateTime? advanceDue(DateTime due, String freq, int n) {
  final gap = n < 1 ? 1 : n;
  return switch (freq) {
    'day' => DateTime(due.year, due.month, due.day + gap, due.hour, due.minute),
    'week' => DateTime(
        due.year, due.month, due.day + 7 * gap, due.hour, due.minute),
    'month' => _advanceMonth(due, gap),
    'year' => _advanceMonth(due, gap * 12),
    _ => null,
  };
}

DateTime _advanceMonth(DateTime due, int months) {
  final total = due.year * 12 + due.month - 1 + months;
  final year = total ~/ 12;
  final month = total % 12 + 1;
  final lastDay = DateTime(year, month + 1, 0).day;
  return DateTime(year, month, due.day > lastDay ? lastDay : due.day,
      due.hour, due.minute);
}
