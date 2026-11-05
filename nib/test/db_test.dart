import 'dart:io';

import 'package:drift/drift.dart' show Value;
import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:nib/core/db/database.dart';
import 'package:nib/core/db/ids.dart';

void main() {
  late AppDatabase db;

  setUp(() => db = AppDatabase.forTesting(NativeDatabase.memory()));
  tearDown(() => db.close());

  test('条目与标签写入后可重新读出', () async {
    await db.ensureSeed();
    final tagId = await db.ensureTag('工作');
    final now = DateTime.now().millisecondsSinceEpoch;
    await db.createEntry(
      EntriesCompanion.insert(
        id: newUlid(),
        title: '投影仪密码 8842',
        sort: newSortKey(),
        hlc: nextHlc(),
        deviceId: localDeviceId,
        createdAt: now,
        updatedAt: now,
        dueDate: const Value('2026-11-05'),
        dueTime: const Value('10:00'),
        repeatFreq: const Value('week'),
        repeatN: const Value(37),
      ),
      [tagId],
    );
    final entries = await db.allEntries();
    final saved = entries.firstWhere((e) => e.title == '投影仪密码 8842');
    expect(saved.dueDate, '2026-11-05');
    expect(saved.dueTime, '10:00');
    expect(saved.repeatN, 37);
    expect(saved.repeatFreq, 'week');
    expect((await (db.select(db.entryTags)..where((t) => t.entryId.equals(saved.id))).get()).single.tagId, tagId);
  });

  test('软删除后不再出现在列表', () async {
    await db.ensureSeed();
    final seeded = await db.allEntries();
    await db.softDeleteEntry(seeded.first.id);
    expect((await db.allEntries()).any((e) => e.id == seeded.first.id), isFalse);
  });

  test('删除清单后条目回落未分类', () async {
    await db.ensureSeed();
    final listId = await db.createList('临时', '#6C7CF5');
    final now = DateTime.now().millisecondsSinceEpoch;
    await db.createEntry(
      EntriesCompanion.insert(
        id: newUlid(),
        title: '待迁移',
        sort: newSortKey(),
        hlc: nextHlc(),
        deviceId: localDeviceId,
        createdAt: now,
        updatedAt: now,
        listId: Value(listId),
      ),
      const [],
    );
    await db.deleteList(listId);
    final moved = (await db.allEntries()).firstWhere((e) => e.title == '待迁移');
    expect(moved.listId, isNull);
  });

  test('关闭后重新打开数据库文件记录仍在', () async {
    final dir = await Directory.systemTemp.createTemp('nib_test');
    final file = File('${dir.path}/nib.sqlite');
    final first = AppDatabase.forTesting(NativeDatabase(file));
    await first.ensureSeed();
    final now = DateTime.now().millisecondsSinceEpoch;
    await first.createEntry(
      EntriesCompanion.insert(
        id: newUlid(),
        title: '重启后仍在',
        sort: newSortKey(),
        hlc: nextHlc(),
        deviceId: localDeviceId,
        createdAt: now,
        updatedAt: now,
      ),
      const [],
    );
    await first.close();
    final second = AppDatabase.forTesting(NativeDatabase(file));
    final restored = await second.allEntries();
    expect(restored.any((e) => e.title == '重启后仍在'), isTrue);
    await second.close();
    await dir.delete(recursive: true);
  });
}
