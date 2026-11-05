import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/db/database.dart';
import '../core/model/entry_bundle.dart';
import '../core/theme/nib_theme.dart';
import '../features/detail/entry_detail_page.dart';
import '../providers.dart';

const priorityColors = <Color>[
  Color(0xFF8B93A5),
  Color(0xFF5FA8D3),
  Color(0xFFE8A33D),
  Color(0xFFE2634A),
];

class EntryRow extends ConsumerWidget {
  const EntryRow({super.key, required this.entry, required this.bundle});

  final Entry entry;
  final EntryBundle bundle;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final tokens = Theme.of(context).extension<NibTokens>()!;
    final done = entry.done == 1;
    final due = entry.dueDate == null
        ? ''
        : '${entry.dueDate}${entry.dueTime == null ? '' : ' ${entry.dueTime}'}';
    final tagNames = bundle.tagNamesOf(entry.id);
    final meta = <String>[
      if (due.isNotEmpty) due,
      bundle.listName(entry.listId),
      ...tagNames.map((name) => '#$name'),
    ].join('  ·  ');
    return InkWell(
      onTap: () => Navigator.of(context).push(MaterialPageRoute<void>(
        builder: (_) => EntryDetailPage(
          entry: entry,
          tagIds: bundle.tagIdsOf(entry.id),
        ),
      )),
      child: Container(
        decoration: BoxDecoration(
          color: tokens.card,
          border: Border(
            left: BorderSide(
              color: priorityColors[entry.priority.clamp(0, 3)],
              width: 2.5,
            ),
          ),
        ),
        child: ListTile(
          leading: IconButton(
            icon: Icon(
              done ? Icons.check_circle : Icons.radio_button_unchecked,
              color: done ? tokens.textMuted : tokens.accent,
            ),
            onPressed: () =>
                ref.read(dbProvider).toggleDone(entry.id, !done),
          ),
          title: Text(
            entry.title,
            style: TextStyle(
              color: done ? tokens.textMuted : tokens.text,
              decoration: done ? TextDecoration.lineThrough : null,
            ),
          ),
          subtitle: meta.isEmpty
              ? null
              : Text(meta, style: TextStyle(color: tokens.textMuted, fontSize: 12)),
        ),
      ),
    );
  }
}
