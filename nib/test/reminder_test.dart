import 'package:drift/drift.dart' show Value;
import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:nib/core/db/database.dart';
import 'package:nib/core/db/ids.dart';
import 'package:nib/features/reminder/reminder_plan.dart';

EntriesCompanion entryData({
  required String id,
  required String title,
  int? remindAt,
  int? snoozeUntil,
  int snoozeCount = 0,
  int done = 0,
  int? deletedAt,
  String channel = 'notify',
  String repeatFreq = 'none',
  int repeatN = 1,
  String? dueDate,
  String? dueTime,
}) {
  final now = DateTime.now().millisecondsSinceEpoch;
  return EntriesCompanion.insert(
    id: id,
    title: title,
    sort: newSortKey(),
    hlc: nextHlc(),
    deviceId: localDeviceId,
    createdAt: now,
    updatedAt: now,
    remindAt: Value<int?>(remindAt),
    snoozeUntil: Value<int?>(snoozeUntil),
    snoozeCount: Value(snoozeCount),
    done: Value(done),
    deletedAt: Value<int?>(deletedAt),
    channel: Value(channel),
    repeatFreq: Value(repeatFreq),
    repeatN: Value(repeatN),
    dueDate: Value<String?>(dueDate),
    dueTime: Value<String?>(dueTime),
  );
}

void main() {
  late AppDatabase db;
  setUp(() => db = AppDatabase.forTesting(NativeDatabase.memory()));
  tearDown(() => db.close());

  Future<List<Entry>> seed(List<EntriesCompanion> rows) async {
    for (final row in rows) {
      await db.createEntry(row, const []);
    }
    return db.allEntries();
  }

  group('通知 id', () {
    test('ULID 映射稳定且在 31 位正整数内', () {
      final id = newUlid();
      expect(notificationId(id), notificationId(id));
      expect(notificationId(id), greaterThanOrEqualTo(0));
      expect(notificationId(id), lessThan(1 << 30));
      expect(notificationId('a'), isNot(notificationId('b')));
    });
  });

  group('提前提醒换算', () {
    test('缺日期或缺时间不落提醒', () {
      expect(remindAtFor(null, '10:00', 30), isNull);
      expect(remindAtFor('2026-11-05', null, 30), isNull);
    });
    test('提前 30 分钟换算成绝对毫秒', () {
      expect(remindAtFor('2026-11-05', '10:00', 30),
          DateTime(2026, 11, 5, 9, 30).millisecondsSinceEpoch);
    });
    test('准时提醒等于 due', () {
      expect(remindAtFor('2026-11-05', '10:00', 0),
          DateTime(2026, 11, 5, 10).millisecondsSinceEpoch);
    });
  });

  group('调度计划', () {
    test('只取未来、未完成、未删除、通道 A 的条目', () async {
      final now = DateTime.now().millisecondsSinceEpoch;
      final entries = await seed([
        entryData(id: 'a', title: '有效', remindAt: now + 60000),
        entryData(id: 'b', title: '已完成', remindAt: now + 60000, done: 1),
        entryData(id: 'c', title: '已删除', remindAt: now + 60000,
            deletedAt: now),
        entryData(id: 'd', title: '走日历', remindAt: now + 60000,
            channel: 'calendar'),
        entryData(id: 'e', title: '已过期', remindAt: now - 60000),
        entryData(id: 'f', title: '无提醒'),
      ]);
      final plans = buildPlans(entries, now);
      expect(plans.map((p) => p.entryId), ['a']);
      expect(plans.single.id, notificationId('a'));
    });

    test('snooze_until 覆盖 remind_at，过期的稍后回落到 remind_at', () async {
      final now = DateTime.now().millisecondsSinceEpoch;
      final entries = await seed([
        entryData(id: 'x', title: '稍后', remindAt: now + 3600000,
            snoozeUntil: now + 600000),
        entryData(id: 'y', title: '过期稍后', remindAt: now + 3600000,
            snoozeUntil: now - 60000),
      ]);
      final plans = buildPlans(entries, now);
      expect(plans.firstWhere((p) => p.entryId == 'x').at,
          now + 600000);
      expect(plans.firstWhere((p) => p.entryId == 'y').at,
          now + 3600000);
    });
  });

  group('稍后提醒', () {
    test('10 分钟 / 1 小时 / 明天', () {
      final now = DateTime(2026, 11, 4, 9).millisecondsSinceEpoch;
      expect(snoozeTarget(now, 'snooze10'),
          DateTime(2026, 11, 4, 9, 10).millisecondsSinceEpoch);
      expect(snoozeTarget(now, 'snooze60'),
          DateTime(2026, 11, 4, 10).millisecondsSinceEpoch);
      expect(snoozeTarget(now, 'snoozeTomorrow'),
          DateTime(2026, 11, 5, 9).millisecondsSinceEpoch);
      expect(snoozeTarget(now, 'unknown'), isNull);
      expect(snoozeTarget(now, null), isNull);
    });
  });

  group('循环顺延', () {
    test('天 / 周 / 月 / 年', () {
      final due = DateTime(2026, 1, 31, 9);
      expect(advanceDue(due, 'day', 2), DateTime(2026, 2, 2, 9));
      expect(advanceDue(due, 'week', 2), DateTime(2026, 2, 14, 9));
      expect(advanceDue(due, 'month', 1), DateTime(2026, 2, 28, 9));
      expect(advanceDue(due, 'year', 1), DateTime(2027, 1, 31, 9));
    });
    test('闰年二月取当月最后一天', () {
      expect(advanceDue(DateTime(2028, 1, 31, 9), 'month', 1),
          DateTime(2028, 2, 29, 9));
    });
    test('不重复返回 null，间隔最小 1', () {
      expect(advanceDue(DateTime(2026, 1, 1), 'none', 3), isNull);
      expect(advanceDue(DateTime(2026, 1, 1), 'day', 0),
          DateTime(2026, 1, 2));
    });
  });

  group('通知动作落库', () {
    test('稍后提醒叠加计数并覆盖时间', () async {
      await seed([entryData(id: 's1', title: '测试', remindAt: 1000)]);
      final before = await db.entryById('s1');
      await db.applySnooze(before!, 5000);
      final after = await db.entryById('s1');
      expect(after!.snoozeUntil, 5000);
      expect(after.snoozeCount, 1);
    });

    test('普通任务完成后标记 done 并清 snooze', () async {
      await seed([
        entryData(id: 'd1', title: '测试', snoozeUntil: 5000,
            snoozeCount: 2)
      ]);
      final before = await db.entryById('d1');
      await db.finishFromNotification(before!,
          nextDue: null, nextTime: null, nextRemindAt: null);
      final after = await db.entryById('d1');
      expect(after!.done, 1);
      expect(after.doneAt, isNotNull);
      expect(after.snoozeUntil, isNull);
      expect(after.snoozeCount, 0);
    });

    test('循环任务留下已完成副本且原条目顺延', () async {
      await seed([
        entryData(
            id: 'r1', title: '浇花', dueDate: '2026-11-04',
            dueTime: '09:00', remindAt: 1000, repeatFreq: 'week',
            repeatN: 2)
      ]);
      final before = await db.entryById('r1');
      await db.finishFromNotification(before!,
          nextDue: '2026-11-18', nextTime: '09:00', nextRemindAt: 2000);
      final all = await db.allEntries();
      expect(all.length, 2);
      final history = all.firstWhere((e) => e.id != 'r1');
      final current = all.firstWhere((e) => e.id == 'r1');
      expect(history.done, 1);
      expect(history.dueDate, '2026-11-04');
      expect(current.done, 0);
      expect(current.dueDate, '2026-11-18');
      expect(current.remindAt, 2000);
      expect(current.snoozeUntil, isNull);
    });
  });
}
