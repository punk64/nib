import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/model/date_util.dart';
import '../../core/model/entry_bundle.dart';
import '../../core/theme/nib_theme.dart';
import '../../widgets/entry_row.dart';

class CalendarPage extends ConsumerStatefulWidget {
  const CalendarPage({super.key});

  @override
  ConsumerState<CalendarPage> createState() => _CalendarPageState();
}

class _CalendarPageState extends ConsumerState<CalendarPage> {
  late DateTime _month;
  late DateTime _selected;

  @override
  void initState() {
    super.initState();
    final now = DateTime.now();
    _month = DateTime(now.year, now.month, 1);
    _selected = DateTime(now.year, now.month, now.day);
  }

  void _shiftMonth(int delta) => setState(() {
        _month = DateTime(_month.year, _month.month + delta, 1);
      });

  @override
  Widget build(BuildContext context) {
    final tokens = Theme.of(context).extension<NibTokens>()!;
    final bundle = ref.watch(bundleProvider(''));
    final dueDates = <String>{};
    for (final entry in bundle.entries) {
      if (entry.dueDate != null) dueDates.add(entry.dueDate!);
    }
    final selectedKey = dateKey(_selected);
    final dayItems = bundle.entries
        .where((e) => e.dueDate == selectedKey)
        .toList()
      ..sort((a, b) => (a.dueTime ?? '99:99').compareTo(b.dueTime ?? '99:99'));
    final firstWeekday = (_month.weekday + 6) % 7;
    final daysInMonth = DateTime(_month.year, _month.month + 1, 0).day;
    final cells = <Widget>[];
    for (var i = 0; i < firstWeekday; i++) {
      cells.add(const SizedBox.shrink());
    }
    for (var day = 1; day <= daysInMonth; day++) {
      final date = DateTime(_month.year, _month.month, day);
      final key = dateKey(date);
      final selected = key == selectedKey;
      cells.add(GestureDetector(
        onTap: () => setState(() => _selected = date),
        child: Container(
          margin: const EdgeInsets.all(3),
          decoration: BoxDecoration(
            color: selected ? tokens.accent.withValues(alpha: .22) : null,
            border: selected ? Border.all(color: tokens.accent) : null,
            borderRadius: BorderRadius.circular(8),
          ),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Text('$day',
                  style: TextStyle(color: tokens.text, fontSize: 13)),
              if (dueDates.contains(key))
                Container(
                  width: 4,
                  height: 4,
                  margin: const EdgeInsets.only(top: 2),
                  decoration: BoxDecoration(
                    color: tokens.accent,
                    shape: BoxShape.circle,
                  ),
                ),
            ],
          ),
        ),
      ));
    }
    return Scaffold(
      appBar: AppBar(
        title: Text('${_month.year} 年 ${_month.month} 月'),
        actions: [
          IconButton(
              icon: const Icon(Icons.chevron_left),
              onPressed: () => _shiftMonth(-1)),
          IconButton(
              icon: const Icon(Icons.chevron_right),
              onPressed: () => _shiftMonth(1)),
        ],
      ),
      body: ListView(
        children: [
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            child: Row(
              children: [
                for (final label in const ['一', '二', '三', '四', '五', '六', '日'])
                  Expanded(
                    child: Center(
                      child: Text(label,
                          style: TextStyle(color: tokens.textMuted, fontSize: 12)),
                    ),
                  ),
              ],
            ),
          ),
          GridView.count(
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            crossAxisCount: 7,
            childAspectRatio: 1,
            children: cells,
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
            child: Text('$selectedKey · ${dayItems.length} 项',
                style: TextStyle(color: tokens.textMuted, fontSize: 12)),
          ),
          if (dayItems.isEmpty)
            Padding(
              padding: const EdgeInsets.all(24),
              child: Center(
                child: Text('这天没有安排',
                    style: TextStyle(color: tokens.textMuted)),
              ),
            )
          else
            Card(
              margin: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
              clipBehavior: Clip.antiAlias,
              child: Column(
                children: [
                  for (final entry in dayItems)
                    EntryRow(entry: entry, bundle: bundle),
                ],
              ),
            ),
        ],
      ),
    );
  }
}
