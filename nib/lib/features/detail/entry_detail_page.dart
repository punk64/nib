import 'package:drift/drift.dart' show Value;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/db/database.dart';
import '../../core/db/ids.dart';
import '../../core/model/date_util.dart';
import '../../core/model/entry_bundle.dart';
import '../../core/model/repeat.dart';
import '../../core/theme/nib_theme.dart';
import '../../providers.dart';
import '../me/me_page.dart' show promptText;
import '../reminder/reminder_plan.dart';
import '../reminder/reminder_scheduler.dart';

const leadOptions = <int, String>{
  -1: '不提醒',
  0: '准时',
  5: '提前 5 分钟',
  15: '提前 15 分钟',
  30: '提前 30 分钟',
  60: '提前 1 小时',
};

const _dateShortcuts = <String, int>{
  '今天': 0,
  '明天': 1,
  '后天': 2,
  '一周后': 7,
};

const _priorityLabels = <String>['无', '低', '中', '高'];

class EntryDetailPage extends ConsumerStatefulWidget {
  const EntryDetailPage({
    super.key,
    required this.entry,
    required this.tagIds,
  });

  final Entry entry;
  final Set<String> tagIds;

  @override
  ConsumerState<EntryDetailPage> createState() => _EntryDetailPageState();
}

class _EntryDetailPageState extends ConsumerState<EntryDetailPage> {
  late final TextEditingController _title;
  late final TextEditingController _body;
  late final TextEditingController _gap;
  String? _listId;
  String? _due;
  String? _time;
  late String _repeatFreq;
  late int _priority;
  late int _lead;
  late Set<String> _tagIds;

  @override
  void initState() {
    super.initState();
    final entry = widget.entry;
    _title = TextEditingController(text: entry.title);
    _body = TextEditingController(text: entry.body);
    _gap = TextEditingController(text: '${entry.repeatN}');
    _listId = entry.listId;
    _due = entry.dueDate;
    _time = entry.dueTime;
    _repeatFreq = entry.repeatFreq;
    _priority = entry.priority;
    _lead = _leadOf(entry);
    _tagIds = {...widget.tagIds};
  }

  @override
  void dispose() {
    _title.dispose();
    _body.dispose();
    _gap.dispose();
    super.dispose();
  }

  int _leadOf(Entry entry) {
    if (entry.remindAt == null) return -1;
    final due = remindAtFor(entry.dueDate, entry.dueTime, 0);
    if (due == null) return 30;
    final minutes = (due - entry.remindAt!) ~/ 60000;
    return leadOptions.containsKey(minutes) ? minutes : 30;
  }

  Future<void> _save() async {
    final now = DateTime.now().millisecondsSinceEpoch;
    final db = ref.read(dbProvider);
    await db.updateEntry(
          widget.entry.id,
          EntriesCompanion(
            title: Value(_title.text.trim().isEmpty
                ? widget.entry.title
                : _title.text.trim()),
            body: Value(_body.text),
            listId: Value<String?>(_listId),
            dueDate: Value<String?>(_due),
            dueTime: Value<String?>(_time),
            repeatFreq: Value(_repeatFreq),
            repeatN: Value(clampRepeatN(_gap.text)),
            priority: Value(_priority),
            remindAt: Value<int?>(
                _lead < 0 ? null : remindAtFor(_due, _time, _lead)),
            updatedAt: Value(now),
            hlc: Value(nextHlc()),
          ),
          _tagIds,
        );
    await rescheduleAll(db);
    if (mounted) Navigator.of(context).pop();
  }

  Future<void> _delete() async {
    final db = ref.read(dbProvider);
    await db.softDeleteEntry(widget.entry.id);
    await cancelReminder(widget.entry.id);
    await rescheduleAll(db);
    if (mounted) Navigator.of(context).pop();
  }

  Future<void> _newTag() async {
    final name = await promptText(context, '新建标签');
    if (name == null || name.isEmpty) return;
    final id = await ref.read(dbProvider).ensureTag(name);
    setState(() => _tagIds.add(id));
  }

  Widget _section(String title) => Padding(
        padding: const EdgeInsets.fromLTRB(0, 18, 0, 6),
        child: Text(title,
            style: const TextStyle(fontSize: 12, color: Colors.grey)),
      );

  @override
  Widget build(BuildContext context) {
    final tokens = Theme.of(context).extension<NibTokens>()!;
    final bundle = ref.watch(bundleProvider(''));
    final now = DateTime.now();
    return Scaffold(
      appBar: AppBar(
        title: const Text('编辑条目'),
        actions: [
          IconButton(icon: const Icon(Icons.delete_outline), onPressed: _delete),
          Padding(
            padding: const EdgeInsets.only(right: 12),
            child: FilledButton(onPressed: _save, child: const Text('保存')),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          TextField(
            controller: _title,
            style: TextStyle(fontSize: 20, color: tokens.text),
            decoration: const InputDecoration(hintText: '标题'),
          ),
          _section('清单'),
          DropdownButton<String>(
            value: _listId ?? '__none',
            isExpanded: true,
            items: [
              const DropdownMenuItem(
                  value: '__none', child: Text('未分类')),
              for (final list in bundle.lists)
                DropdownMenuItem(value: list.id, child: Text(list.name)),
            ],
            onChanged: (value) =>
                setState(() => _listId = value == '__none' ? null : value),
          ),
          _section('日期'),
          Wrap(
            spacing: 6,
            crossAxisAlignment: WrapCrossAlignment.center,
            children: [
              OutlinedButton(
                onPressed: () async {
                  final picked = await showDatePicker(
                    context: context,
                    initialDate: parseDateKey(_due) ?? now,
                    firstDate: DateTime(2020),
                    lastDate: DateTime(2100),
                  );
                  if (picked != null) {
                    setState(() => _due = dateKey(picked));
                  }
                },
                child: Text(_due ?? '选日期'),
              ),
              for (final shortcut in _dateShortcuts.entries)
                TextButton(
                  onPressed: () => setState(() =>
                      _due = dateKey(addDays(now, shortcut.value))),
                  child: Text(shortcut.key,
                      style: const TextStyle(fontSize: 12)),
                ),
              IconButton(
                icon: const Icon(Icons.clear, size: 18),
                onPressed: () => setState(() => _due = null),
              ),
            ],
          ),
          _section('时间'),
          Row(
            children: [
              OutlinedButton(
                onPressed: () async {
                  final picked = await showTimePicker(
                    context: context,
                    initialTime: parseTimeKey(_time) ?? TimeOfDay.now(),
                  );
                  if (picked != null) {
                    setState(() => _time = timeKey(picked));
                  }
                },
                child: Text(_time ?? '选时间'),
              ),
              IconButton(
                icon: const Icon(Icons.clear, size: 18),
                onPressed: () => setState(() => _time = null),
              ),
            ],
          ),
          _section('提醒'),
          DropdownButton<int>(
            value: _lead,
            isExpanded: true,
            items: [
              for (final option in leadOptions.entries)
                DropdownMenuItem(value: option.key, child: Text(option.value)),
            ],
            onChanged: (value) => setState(() => _lead = value ?? -1),
          ),
          _section('重复'),
          Wrap(
            spacing: 8,
            children: [
              for (final option in repeatLabels.entries)
                ChoiceChip(
                  label: Text(option.value),
                  selected: _repeatFreq == option.key,
                  onSelected: (_) =>
                      setState(() => _repeatFreq = option.key),
                ),
            ],
          ),
          if (_repeatFreq != 'none')
            Row(
              children: [
                Text('每', style: TextStyle(color: tokens.textMuted)),
                const SizedBox(width: 8),
                SizedBox(
                  width: 90,
                  child: TextField(
                    controller: _gap,
                    keyboardType: TextInputType.number,
                    decoration: const InputDecoration(hintText: '1-999'),
                  ),
                ),
                const SizedBox(width: 8),
                Text(repeatUnits[_repeatFreq] ?? '天'),
              ],
            ),
          _section('标签'),
          Wrap(
            spacing: 8,
            children: [
              for (final tag in bundle.tags)
                FilterChip(
                  label: Text('#${tag.name}'),
                  selected: _tagIds.contains(tag.id),
                  onSelected: (_) => setState(() {
                    if (!_tagIds.add(tag.id)) _tagIds.remove(tag.id);
                  }),
                ),
              ActionChip(
                label: const Text('+ 新建'),
                onPressed: _newTag,
              ),
            ],
          ),
          _section('优先级'),
          Wrap(
            spacing: 8,
            children: [
              for (var index = 0; index < _priorityLabels.length; index++)
                ChoiceChip(
                  label: Text(_priorityLabels[index]),
                  selected: _priority == index,
                  onSelected: (_) => setState(() => _priority = index),
                ),
            ],
          ),
          _section('备注'),
          TextField(
            controller: _body,
            maxLines: 4,
            decoration: const InputDecoration(
              hintText: '正文与备注',
              border: OutlineInputBorder(),
            ),
          ),
          const SizedBox(height: 24),
          FilledButton(onPressed: _save, child: const Text('保存')),
          const SizedBox(height: 8),
          OutlinedButton(onPressed: _delete, child: const Text('删除条目')),
        ],
      ),
    );
  }
}
