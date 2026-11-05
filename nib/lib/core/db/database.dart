import 'dart:io';

import 'package:drift/drift.dart';
import 'package:drift/native.dart';
import 'package:path_provider/path_provider.dart';

import 'ids.dart';

part 'database.g.dart';

@DataClassName('NoteList')
class Lists extends Table {
  TextColumn get id => text()();
  TextColumn get name => text()();
  TextColumn get color => text()();
  TextColumn get sort => text()();
  IntColumn get createdAt => integer()();
  IntColumn get updatedAt => integer()();
  IntColumn get deletedAt => integer().nullable()();

  @override
  Set<Column> get primaryKey => {id};
}

@TableIndex(name: 'idx_entries_due', columns: {#dueDate, #done, #deletedAt})
@TableIndex(name: 'idx_entries_list', columns: {#listId, #deletedAt})
class Entries extends Table {
  TextColumn get id => text()();
  TextColumn get type => text().withDefault(const Constant('note'))();
  TextColumn get title => text()();
  TextColumn get body => text().withDefault(const Constant(''))();
  TextColumn get listId => text().nullable().references(Lists, #id)();
  TextColumn get dueDate => text().nullable()();
  TextColumn get dueTime => text().nullable()();
  IntColumn get remindAt => integer().nullable()();
  IntColumn get durationMin => integer().withDefault(const Constant(30))();
  IntColumn get snoozeUntil => integer().nullable()();
  IntColumn get snoozeCount => integer().withDefault(const Constant(0))();
  TextColumn get channel => text().withDefault(const Constant('notify'))();
  TextColumn get repeatFreq => text().withDefault(const Constant('none'))();
  IntColumn get repeatN => integer().withDefault(const Constant(1))();
  IntColumn get priority => integer().withDefault(const Constant(0))();
  IntColumn get done => integer().withDefault(const Constant(0))();
  IntColumn get doneAt => integer().nullable()();
  IntColumn get pinned => integer().withDefault(const Constant(0))();
  TextColumn get sort => text()();
  IntColumn get calendarEventId => integer().nullable()();
  TextColumn get hlc => text()();
  TextColumn get deviceId => text()();
  IntColumn get createdAt => integer()();
  IntColumn get updatedAt => integer()();
  IntColumn get deletedAt => integer().nullable()();

  @override
  Set<Column> get primaryKey => {id};
}

@TableIndex.sql(
    'CREATE UNIQUE INDEX idx_tags_name ON tags(name) WHERE deleted_at IS NULL')
class Tags extends Table {
  TextColumn get id => text()();
  TextColumn get name => text()();
  IntColumn get createdAt => integer()();
  IntColumn get deletedAt => integer().nullable()();

  @override
  Set<Column> get primaryKey => {id};
}

class EntryTags extends Table {
  TextColumn get entryId =>
      text().references(Entries, #id, onDelete: KeyAction.cascade)();
  TextColumn get tagId =>
      text().references(Tags, #id, onDelete: KeyAction.cascade)();

  @override
  Set<Column> get primaryKey => {entryId, tagId};
}

class EntryItems extends Table {
  TextColumn get id => text()();
  TextColumn get entryId =>
      text().references(Entries, #id, onDelete: KeyAction.cascade)();
  TextColumn get content => text().named('text')();
  IntColumn get done => integer().withDefault(const Constant(0))();
  TextColumn get sort => text()();

  @override
  Set<Column> get primaryKey => {id};
}

class OpLog extends Table {
  TextColumn get id => text()();
  TextColumn get entityType => text()();
  TextColumn get entityId => text()();
  TextColumn get field => text()();
  TextColumn get value => text().nullable()();
  TextColumn get hlc => text()();
  TextColumn get deviceId => text()();
  IntColumn get serverSeq => integer().nullable()();

  @override
  Set<Column> get primaryKey => {id};
}

@DriftDatabase(tables: [Lists, Entries, Tags, EntryTags, EntryItems, OpLog])
class AppDatabase extends _$AppDatabase {
  AppDatabase() : super(_open());

  AppDatabase.forTesting(super.executor);

  @override
  int get schemaVersion => 1;

  @override
  MigrationStrategy get migration => MigrationStrategy(
        onCreate: (m) => m.createAll(),
        beforeOpen: (details) async {
          await customStatement('PRAGMA foreign_keys = ON');
        },
      );

  SimpleSelectStatement<Entries, Entry> _visibleEntries({String query = ''}) {
    final statement = select(entries)..where((t) => t.deletedAt.isNull());
    final q = query.trim();
    if (q.isNotEmpty) {
      final pattern = '%$q%';
      statement.where((t) => t.title.like(pattern) | t.body.like(pattern));
    }
    statement.orderBy([(t) => OrderingTerm.asc(t.sort)]);
    return statement;
  }

  Stream<List<Entry>> watchEntries({String query = ''}) =>
      _visibleEntries(query: query).watch();

  Future<List<Entry>> allEntries() => _visibleEntries().get();

  Stream<List<NoteList>> watchLists() => (select(lists)
        ..where((t) => t.deletedAt.isNull())
        ..orderBy([(t) => OrderingTerm.asc(t.sort)]))
      .watch();

  Future<List<NoteList>> allLists() => (select(lists)
        ..where((t) => t.deletedAt.isNull())
        ..orderBy([(t) => OrderingTerm.asc(t.sort)]))
      .get();

  Stream<List<Tag>> watchTags() => (select(tags)
        ..where((t) => t.deletedAt.isNull())
        ..orderBy([(t) => OrderingTerm.asc(t.name)]))
      .watch();

  Future<List<Tag>> allTags() => (select(tags)
        ..where((t) => t.deletedAt.isNull())
        ..orderBy([(t) => OrderingTerm.asc(t.name)]))
      .get();

  Stream<List<EntryTag>> watchEntryTags() => select(entryTags).watch();

  Future<String> createList(String name, String color) async {
    final id = newUlid();
    final now = DateTime.now().millisecondsSinceEpoch;
    await into(lists).insert(ListsCompanion.insert(
      id: id,
      name: name,
      color: color,
      sort: newSortKey(),
      createdAt: now,
      updatedAt: now,
    ));
    return id;
  }

  Future<void> updateList(String id, {String? name, String? color}) async {
    await (update(lists)..where((t) => t.id.equals(id))).write(ListsCompanion(
      name: name == null ? const Value.absent() : Value(name),
      color: color == null ? const Value.absent() : Value(color),
      updatedAt: Value(DateTime.now().millisecondsSinceEpoch),
    ));
  }

  Future<void> deleteList(String id) async {
    final now = DateTime.now().millisecondsSinceEpoch;
    await transaction(() async {
      await (update(entries)..where((t) => t.listId.equals(id))).write(
        EntriesCompanion(
          listId: const Value<String?>(null),
          updatedAt: Value(now),
          hlc: Value(nextHlc()),
        ),
      );
      await (update(lists)..where((t) => t.id.equals(id))).write(
        ListsCompanion(deletedAt: Value(now), updatedAt: Value(now)),
      );
    });
  }

  Future<String> ensureTag(String name) async {
    final existing = await (select(tags)
          ..where((t) => t.name.equals(name) & t.deletedAt.isNull()))
        .get();
    if (existing.isNotEmpty) return existing.first.id;
    final id = newUlid();
    await into(tags).insert(TagsCompanion.insert(
      id: id,
      name: name,
      createdAt: DateTime.now().millisecondsSinceEpoch,
    ));
    return id;
  }

  Future<void> renameTag(String id, String name) async =>
      (update(tags)..where((t) => t.id.equals(id)))
          .write(TagsCompanion(name: Value(name)));

  Future<void> deleteTag(String id) async {
    await transaction(() async {
      await (delete(entryTags)..where((t) => t.tagId.equals(id))).go();
      await (delete(tags)..where((t) => t.id.equals(id))).go();
    });
  }

  Future<void> _replaceTagLinks(
      String entryId, Iterable<String> tagIds) async {
    await (delete(entryTags)..where((t) => t.entryId.equals(entryId))).go();
    for (final tagId in tagIds.toSet()) {
      await into(entryTags).insert(
          EntryTagsCompanion.insert(entryId: entryId, tagId: tagId));
    }
  }

  Future<void> createEntry(
      EntriesCompanion data, Iterable<String> tagIds) async {
    await transaction(() async {
      await into(entries).insert(data);
      await _replaceTagLinks(data.id.value, tagIds);
    });
  }

  Future<void> updateEntry(
    String id,
    EntriesCompanion data, [
    Iterable<String>? tagIds,
  ]) async {
    await transaction(() async {
      await (update(entries)..where((t) => t.id.equals(id))).write(data);
      if (tagIds != null) await _replaceTagLinks(id, tagIds);
    });
  }

  Future<void> toggleDone(String id, bool done) async {
    final now = DateTime.now().millisecondsSinceEpoch;
    await (update(entries)..where((t) => t.id.equals(id))).write(
      EntriesCompanion(
        done: Value(done ? 1 : 0),
        doneAt: Value<int?>(done ? now : null),
        updatedAt: Value(now),
        hlc: Value(nextHlc()),
      ),
    );
  }

  Future<Entry?> entryById(String id) =>
      (select(entries)..where((t) => t.id.equals(id))).getSingleOrNull();

  Future<void> clearExpiredSnoozes(int now) async {
    await (update(entries)
          ..where((t) => t.snoozeUntil.isSmallerOrEqualValue(now)))
        .write(EntriesCompanion(
      snoozeUntil: const Value<int?>(null),
      snoozeCount: const Value(0),
    ));
  }

  Future<void> applySnooze(Entry entry, int until) async {
    final now = DateTime.now().millisecondsSinceEpoch;
    await (update(entries)..where((t) => t.id.equals(entry.id))).write(
      EntriesCompanion(
        snoozeUntil: Value<int?>(until),
        snoozeCount: Value(entry.snoozeCount + 1),
        updatedAt: Value(now),
        hlc: Value(nextHlc()),
      ),
    );
  }

  Future<void> finishFromNotification(
    Entry entry, {
    required String? nextDue,
    required String? nextTime,
    required int? nextRemindAt,
  }) async {
    final now = DateTime.now().millisecondsSinceEpoch;
    await transaction(() async {
      if (entry.repeatFreq == 'none' || entry.dueDate == null) {
        await (update(entries)..where((t) => t.id.equals(entry.id))).write(
          EntriesCompanion(
            done: const Value(1),
            doneAt: Value<int?>(now),
            snoozeUntil: const Value<int?>(null),
            snoozeCount: const Value(0),
            updatedAt: Value(now),
            hlc: Value(nextHlc()),
          ),
        );
        return;
      }
      await into(entries).insert(EntriesCompanion.insert(
        id: newUlid(),
        title: entry.title,
        body: Value(entry.body),
        listId: Value<String?>(entry.listId),
        dueDate: Value<String?>(entry.dueDate),
        dueTime: Value<String?>(entry.dueTime),
        remindAt: Value<int?>(entry.remindAt),
        durationMin: Value(entry.durationMin),
        done: const Value(1),
        doneAt: Value<int?>(now),
        priority: Value(entry.priority),
        sort: newSortKey(),
        channel: Value(entry.channel),
        hlc: nextHlc(),
        deviceId: localDeviceId,
        createdAt: now,
        updatedAt: now,
      ));
      await (update(entries)..where((t) => t.id.equals(entry.id))).write(
        EntriesCompanion(
          dueDate: Value<String?>(nextDue),
          dueTime: Value<String?>(nextTime),
          remindAt: Value<int?>(nextRemindAt),
          snoozeUntil: const Value<int?>(null),
          snoozeCount: const Value(0),
          updatedAt: Value(now),
          hlc: Value(nextHlc()),
        ),
      );
    });
  }

  Future<void> softDeleteEntry(String id) async {
    final now = DateTime.now().millisecondsSinceEpoch;
    await (update(entries)..where((t) => t.id.equals(id))).write(
      EntriesCompanion(
        deletedAt: Value(now),
        updatedAt: Value(now),
        hlc: Value(nextHlc()),
      ),
    );
  }

  Future<void> ensureSeed() async {
    if ((await allLists()).isEmpty) {
      await createList('工作', '#6C7CF5');
      await createList('生活', '#3FBFA8');
    }
    if ((await allTags()).isEmpty) {
      await ensureTag('重要');
      await ensureTag('随手记');
    }
    if ((await allEntries()).isEmpty) {
      final now = DateTime.now().millisecondsSinceEpoch;
      final seedTags = await allTags();
      await createEntry(
        EntriesCompanion.insert(
          id: newUlid(),
          title: '欢迎使用落笔',
          sort: newSortKey(),
          hlc: nextHlc(),
          deviceId: localDeviceId,
          createdAt: now,
          updatedAt: now,
        ),
        seedTags.take(1).map((t) => t.id),
      );
    }
  }
}

LazyDatabase _open() => LazyDatabase(() async {
      final dir = await getApplicationDocumentsDirectory();
      final file = File('${dir.path}/nib.sqlite');
      return NativeDatabase.createInBackground(file);
    });
