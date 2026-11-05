class EntryFilter {
  const EntryFilter({
    this.listId = allLists,
    this.tagIds = const <String>{},
  });

  static const allLists = 'all';
  static const noList = '__none';

  final String listId;
  final Set<String> tagIds;

  EntryFilter copyWith({String? listId, Set<String>? tagIds}) => EntryFilter(
        listId: listId ?? this.listId,
        tagIds: tagIds ?? this.tagIds,
      );
}

bool matchesFilter(
  EntryFilter filter,
  String? entryListId,
  Set<String> entryTagIds,
) {
  final byList = filter.listId == EntryFilter.allLists ||
      (filter.listId == EntryFilter.noList
          ? entryListId == null
          : entryListId == filter.listId);
  if (!byList) return false;
  return filter.tagIds.every(entryTagIds.contains);
}

bool matchesSearch(
  String query,
  String title,
  String body,
  Iterable<String> tagNames,
) {
  final q = query.trim().toLowerCase();
  if (q.isEmpty) return true;
  return title.toLowerCase().contains(q) ||
      body.toLowerCase().contains(q) ||
      tagNames.any((name) => name.toLowerCase().contains(q));
}
