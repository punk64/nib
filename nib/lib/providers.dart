import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'core/db/database.dart';
import 'core/model/filter.dart';
import 'core/theme/nib_theme.dart';

final dbProvider = Provider<AppDatabase>((ref) => AppDatabase());

final entriesProvider = StreamProvider.family<List<Entry>, String>(
    (ref, query) => ref.watch(dbProvider).watchEntries(query: query));

final listsProvider = StreamProvider<List<NoteList>>(
    (ref) => ref.watch(dbProvider).watchLists());

final tagsProvider =
    StreamProvider<List<Tag>>((ref) => ref.watch(dbProvider).watchTags());

final entryTagsProvider = StreamProvider<List<EntryTag>>(
    (ref) => ref.watch(dbProvider).watchEntryTags());

class FilterNotifier extends Notifier<EntryFilter> {
  @override
  EntryFilter build() => const EntryFilter();

  void selectList(String id) => state = state.copyWith(listId: id);

  void toggleTag(String id) {
    final next = Set<String>.of(state.tagIds);
    if (!next.add(id)) next.remove(id);
    state = state.copyWith(tagIds: next);
  }

  void clearTags() => state = state.copyWith(tagIds: const <String>{});
}

final filterProvider =
    NotifierProvider<FilterNotifier, EntryFilter>(FilterNotifier.new);

class SearchNotifier extends Notifier<String> {
  @override
  String build() => '';

  void set(String value) => state = value;
}

final searchProvider =
    NotifierProvider<SearchNotifier, String>(SearchNotifier.new);

class ShowDoneNotifier extends Notifier<bool> {
  @override
  bool build() => false;

  void toggle() => state = !state;
}

final showDoneProvider =
    NotifierProvider<ShowDoneNotifier, bool>(ShowDoneNotifier.new);

class ThemeConfigNotifier extends Notifier<ThemeConfig> {
  @override
  ThemeConfig build() => const ThemeConfig();

  void toggleBrightness() => state = state.copyWith(
        brightness: state.brightness == Brightness.dark
            ? Brightness.light
            : Brightness.dark,
      );
}

final themeConfigProvider =
    NotifierProvider<ThemeConfigNotifier, ThemeConfig>(ThemeConfigNotifier.new);
