import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/model/entry_bundle.dart';
import '../../core/theme/nib_theme.dart';
import '../../providers.dart';
import '../../widgets/entry_row.dart';
import '../../widgets/filter_bar.dart';

class AllPage extends ConsumerStatefulWidget {
  const AllPage({super.key});

  @override
  ConsumerState<AllPage> createState() => _AllPageState();
}

class _AllPageState extends ConsumerState<AllPage> {
  bool _searching = false;

  @override
  Widget build(BuildContext context) {
    final tokens = Theme.of(context).extension<NibTokens>()!;
    final query = ref.watch(searchProvider);
    final filter = ref.watch(filterProvider);
    final bundle = ref.watch(bundleProvider(query));
    final showDone = ref.watch(showDoneProvider);
    final pending = bundle.filtered(filter: filter, query: query, done: false);
    final finished = bundle.filtered(filter: filter, query: query, done: true);
    return Scaffold(
      appBar: AppBar(
        title: _searching
            ? TextField(
                autofocus: true,
                decoration: const InputDecoration(
                  hintText: '搜索标题、正文、标签',
                  border: InputBorder.none,
                ),
                onChanged: (value) =>
                    ref.read(searchProvider.notifier).set(value),
              )
            : Text('全部  ${pending.length} 项待办'),
        actions: [
          IconButton(
            icon: Icon(_searching ? Icons.close : Icons.search),
            onPressed: () {
              setState(() => _searching = !_searching);
              if (!_searching) ref.read(searchProvider.notifier).set('');
            },
          ),
        ],
      ),
      body: Column(
        children: [
          const FilterBar(),
          Expanded(
            child: ListView(
              children: [
                if (pending.isEmpty)
                  Padding(
                    padding: const EdgeInsets.all(32),
                    child: Center(
                      child: Text('这个筛选下没有条目',
                          style: TextStyle(color: tokens.textMuted)),
                    ),
                  ),
                Card(
                  margin: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
                  clipBehavior: Clip.antiAlias,
                  child: Column(
                    children: [
                      for (final entry in pending)
                        EntryRow(entry: entry, bundle: bundle),
                    ],
                  ),
                ),
                if (finished.isNotEmpty) ...[
                  ListTile(
                    title: Text('已完成 ${finished.length}',
                        style: TextStyle(color: tokens.textMuted, fontSize: 13)),
                    trailing: Icon(
                        showDone ? Icons.expand_less : Icons.expand_more,
                        color: tokens.textMuted),
                    onTap: () =>
                        ref.read(showDoneProvider.notifier).toggle(),
                  ),
                  if (showDone)
                    Card(
                      margin: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
                      clipBehavior: Clip.antiAlias,
                      child: Column(
                        children: [
                          for (final entry in finished)
                            EntryRow(entry: entry, bundle: bundle),
                        ],
                      ),
                    ),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }
}
