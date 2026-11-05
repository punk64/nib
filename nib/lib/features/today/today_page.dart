import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/db/database.dart';
import '../../core/model/date_util.dart';
import '../../core/model/entry_bundle.dart';
import '../../core/theme/nib_theme.dart';
import '../../providers.dart';
import '../../widgets/entry_row.dart';

class TodayPage extends ConsumerWidget {
  const TodayPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final tokens = Theme.of(context).extension<NibTokens>()!;
    final bundle = ref.watch(bundleProvider(''));
    final today = dateKey(DateTime.now());
    final overdue = bundle.entries
        .where((e) =>
            e.done == 0 && e.dueDate != null && e.dueDate!.compareTo(today) < 0)
        .toList()
      ..sort((a, b) => a.dueDate!.compareTo(b.dueDate!));
    final dueToday = bundle.entries
        .where((e) => e.done == 0 && e.dueDate == today)
        .toList()
      ..sort((a, b) => (a.dueTime ?? '99:99').compareTo(b.dueTime ?? '99:99'));
    final finished = bundle.entries
        .where((e) => e.done == 1 && e.dueDate == today)
        .toList();
    return Scaffold(
      appBar: AppBar(
        title: const Text('今日'),
        actions: [
          IconButton(
            icon: const Icon(Icons.brightness_6_outlined),
            onPressed: () =>
                ref.read(themeConfigProvider.notifier).toggleBrightness(),
          ),
        ],
      ),
      body: ListView(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 16),
            child: Text(
              '${DateTime.now().month} 月 ${DateTime.now().day} 日 · ${dueToday.length} 项待办',
              style: TextStyle(fontSize: 18, color: tokens.text),
            ),
          ),
          if (overdue.isNotEmpty)
            _section('已逾期 ${overdue.length}', overdue, bundle, tokens),
          _section('今天 ${dueToday.length}', dueToday, bundle, tokens),
          if (finished.isNotEmpty)
            _section('已完成 ${finished.length}', finished, bundle, tokens),
          if (overdue.isEmpty && dueToday.isEmpty && finished.isEmpty)
            Padding(
              padding: const EdgeInsets.all(32),
              child: Center(
                child: Text('今天没有安排', style: TextStyle(color: tokens.textMuted)),
              ),
            ),
        ],
      ),
    );
  }

  Widget _section(
      String title, List<Entry> items, EntryBundle bundle, NibTokens tokens) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 6),
          child: Text(title, style: TextStyle(color: tokens.textMuted, fontSize: 12)),
        ),
        Card(
          margin: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
          clipBehavior: Clip.antiAlias,
          child: Column(
            children: [
              for (final entry in items) EntryRow(entry: entry, bundle: bundle),
            ],
          ),
        ),
      ],
    );
  }
}
