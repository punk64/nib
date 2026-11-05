import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/model/entry_bundle.dart';
import '../core/model/filter.dart';
import '../providers.dart';

class FilterBar extends ConsumerWidget {
  const FilterBar({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final filter = ref.watch(filterProvider);
    final bundle = ref.watch(bundleProvider(''));
    final listChips = <Widget>[
      FilterChip(
        label: const Text('全部'),
        selected: filter.listId == EntryFilter.allLists,
        onSelected: (_) =>
            ref.read(filterProvider.notifier).selectList(EntryFilter.allLists),
      ),
      for (final list in bundle.lists)
        FilterChip(
          label: Text(list.name),
          selected: filter.listId == list.id,
          onSelected: (_) =>
              ref.read(filterProvider.notifier).selectList(list.id),
        ),
      FilterChip(
        label: const Text('未分类'),
        selected: filter.listId == EntryFilter.noList,
        onSelected: (_) =>
            ref.read(filterProvider.notifier).selectList(EntryFilter.noList),
      ),
    ];
    return Padding(
      padding: const EdgeInsets.fromLTRB(12, 4, 12, 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Wrap(spacing: 6, runSpacing: 2, children: listChips),
          if (bundle.tags.isNotEmpty) ...[
            const SizedBox(height: 4),
            Wrap(
              spacing: 6,
              runSpacing: 2,
              crossAxisAlignment: WrapCrossAlignment.center,
              children: [
                const Text('标签', style: TextStyle(fontSize: 12)),
                for (final tag in bundle.tags)
                  FilterChip(
                    label: Text('#${tag.name}'),
                    selected: filter.tagIds.contains(tag.id),
                    onSelected: (_) =>
                        ref.read(filterProvider.notifier).toggleTag(tag.id),
                  ),
                if (filter.tagIds.isNotEmpty)
                  ActionChip(
                    label: const Text('清除'),
                    onPressed: () =>
                        ref.read(filterProvider.notifier).clearTags(),
                  ),
              ],
            ),
          ],
        ],
      ),
    );
  }
}
