import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../db/database.dart';
import '../../providers.dart';
import 'filter.dart';

class EntryBundle {
  const EntryBundle({
    required this.entries,
    required this.lists,
    required this.tags,
    required this.links,
  });

  final List<Entry> entries;
  final List<NoteList> lists;
  final List<Tag> tags;
  final List<EntryTag> links;

  Set<String> tagIdsOf(String entryId) {
    final ids = <String>{};
    for (final link in links) {
      if (link.entryId == entryId) ids.add(link.tagId);
    }
    return ids;
  }

  List<String> tagNamesOf(String entryId) {
    final ids = tagIdsOf(entryId);
    return tags.where((t) => ids.contains(t.id)).map((t) => t.name).toList();
  }

  String listName(String? id) {
    if (id == null) return '未分类';
    for (final list in lists) {
      if (list.id == id) return list.name;
    }
    return '未分类';
  }

  String? listColor(String? id) {
    if (id == null) return null;
    for (final list in lists) {
      if (list.id == id) return list.color;
    }
    return null;
  }

  List<Entry> filtered({
    required EntryFilter filter,
    required String query,
    required bool done,
  }) {
    final matched = <Entry>[];
    for (final entry in entries) {
      if ((entry.done == 1) != done) continue;
      if (!matchesFilter(filter, entry.listId, tagIdsOf(entry.id))) continue;
      if (!matchesSearch(query, entry.title, entry.body, tagNamesOf(entry.id))) {
        continue;
      }
      matched.add(entry);
    }
    final undated = matched.where((e) => e.dueDate == null).toList()
      ..sort((a, b) => b.createdAt.compareTo(a.createdAt));
    final dated = matched.where((e) => e.dueDate != null).toList()
      ..sort((a, b) {
        final ka = '${a.dueDate} ${a.dueTime ?? '99:99'}';
        final kb = '${b.dueDate} ${b.dueTime ?? '99:99'}';
        final byDate = ka.compareTo(kb);
        return byDate != 0 ? byDate : b.priority.compareTo(a.priority);
      });
    return [...undated, ...dated];
  }
}

final bundleProvider = Provider.family<EntryBundle, String>((ref, query) {
  return EntryBundle(
    entries: ref.watch(entriesProvider(query)).value ?? <Entry>[],
    lists: ref.watch(listsProvider).value ?? <NoteList>[],
    tags: ref.watch(tagsProvider).value ?? <Tag>[],
    links: ref.watch(entryTagsProvider).value ?? <EntryTag>[],
  );
});
