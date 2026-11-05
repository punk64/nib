import 'package:drift/drift.dart' show Value;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/db/database.dart';
import '../../core/db/ids.dart';
import '../../core/model/date_util.dart';
import '../../core/model/repeat.dart';
import '../../core/parse/parse.dart';
import '../../core/theme/nib_theme.dart';
import '../../providers.dart';
import '../reminder/reminder_plan.dart';
import '../reminder/reminder_scheduler.dart';

class _Draft {
  String? listId;
  String? due;
  String? time;
  String repeatFreq = 'none';
  int repeatN = 1;
  int priority = 0;
  final Set<String> tags = <String>{};
}

const _dateShortcuts = <String, int>{
  '今天': 0,
  '明天': 1,
  '后天': 2,
  '一周后': 7,
};

const _priorityLabels = <String>['无', '低', '中', '高'];

class QuickCapturePage extends ConsumerStatefulWidget {
  const QuickCapturePage({super.key});

  @override
  ConsumerState<QuickCapturePage> createState() => _QuickCapturePageState();
}

class _QuickCapturePageState extends ConsumerState<QuickCapturePage> {
  final _title = TextEditingController();
  final _body = TextEditingController();
  final _draft = _Draft();

  @override
  void initState() {
    super.initState();
    _title.addListener(_refresh);
  }

  void _refresh() {
    final note = parse(_title.text);
    setState(() {
      _draft.due = note.due;
      _draft.time = note.time;
      _draft.priority = note.priority;
      _draft.repeatFreq = note.repeatFreq;
      _draft.repeatN = note.repeatN;
      _draft.tags.addAll(note.tags);
    });
  }

  @override
  void dispose() {
    _title.dispose();
    _body.dispose();
    super.dispose();
  }

  String _dueLabel() {
    final due = _draft.due;
    if (due == null) return '';
    final now = DateTime.now();
    final prefix = due == dateKey(now)
        ? '今天'
        : due == dateKey(addDays(now, 1))
            ? '明天'
            : due;
    return _draft.time == null ? prefix : '$prefix ${_draft.time}';
  }

  Future<void> _save() async {
    final raw = _title.text;
    if (raw.trim().isEmpty) return;
    final note = parse(raw);
    final db = ref.read(dbProvider);
    final tagIds = <String>[];
    for (final name in _draft.tags) {
      tagIds.add(await db.ensureTag(name));
    }
    final now = DateTime.now().millisecondsSinceEpoch;
    await db.createEntry(
      EntriesCompanion.insert(
        id: newUlid(),
        title: note.title.isEmpty ? raw : note.title,
        sort: newSortKey(),
        hlc: nextHlc(),
        deviceId: localDeviceId,
        createdAt: now,
        updatedAt: now,
        body: Value(_body.text),
        listId: Value<String?>(_draft.listId),
        dueDate: Value<String?>(_draft.due),
        dueTime: Value<String?>(_draft.time),
        priority: Value(_draft.priority),
        repeatFreq: Value(_draft.repeatFreq),
        repeatN: Value(_draft.repeatN),
        remindAt: Value<int?>(
            remindAtFor(_draft.due, _draft.time, 30)),
      ),
      tagIds,
    );
    await rescheduleAll(db);
    if (mounted) Navigator.of(context).pop();
  }

  Future<void> _openTool(String kind) async {
    final db = ref.read(dbProvider);
    final lists = await db.allLists();
    final tags = await db.allTags();
    if (!mounted) return;
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      builder: (sheetContext) => StatefulBuilder(
        builder: (context, setSheet) {
          void apply(void Function() change) {
            change();
            setSheet(() {});
            setState(() {});
          }

          return SafeArea(
            child: Padding(
              padding: EdgeInsets.only(
                left: 16,
                right: 16,
                top: 16,
                bottom: MediaQuery.of(context).viewInsets.bottom + 16,
              ),
              child: SingleChildScrollView(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: _toolBody(kind, context, lists, tags, apply),
                ),
              ),
            ),
          );
        },
      ),
    );
  }

  List<Widget> _toolBody(
    String kind,
    BuildContext context,
    List<NoteList> lists,
    List<Tag> tags,
    void Function(void Function()) apply,
  ) {
    if (kind == 'list') {
      return [
        const Text('选择清单 · 一条只属于一个'),
        Wrap(
          spacing: 8,
          children: [
            ChoiceChip(
              label: const Text('未分类'),
              selected: _draft.listId == null,
              onSelected: (_) => apply(() => _draft.listId = null),
            ),
            for (final list in lists)
              ChoiceChip(
                label: Text(list.name),
                selected: _draft.listId == list.id,
                onSelected: (_) => apply(() => _draft.listId = list.id),
              ),
          ],
        ),
        TextField(
          decoration: const InputDecoration(hintText: '新建清单，回车添加'),
          onSubmitted: (value) async {
            if (value.trim().isEmpty) return;
            final id = await ref
                .read(dbProvider)
                .createList(value.trim(), '#6C7CF5');
            apply(() => _draft.listId = id);
          },
        ),
      ];
    }
    if (kind == 'date') {
      final now = DateTime.now();
      return [
        const Text('日期与时间'),
        Wrap(
          spacing: 8,
          children: [
            for (final shortcut in _dateShortcuts.entries)
              ChoiceChip(
                label: Text(shortcut.key),
                selected:
                    _draft.due == dateKey(addDays(now, shortcut.value)),
                onSelected: (_) => apply(() =>
                    _draft.due = dateKey(addDays(now, shortcut.value))),
              ),
          ],
        ),
        const SizedBox(height: 8),
        Row(
          children: [
            OutlinedButton(
              onPressed: () async {
                final picked = await showDatePicker(
                  context: context,
                  initialDate: parseDateKey(_draft.due) ?? now,
                  firstDate: DateTime(2020),
                  lastDate: DateTime(2100),
                );
                if (picked != null) {
                  apply(() => _draft.due = dateKey(picked));
                }
              },
              child: Text(_draft.due ?? '选日期'),
            ),
            const SizedBox(width: 8),
            OutlinedButton(
              onPressed: () async {
                final picked = await showTimePicker(
                  context: context,
                  initialTime:
                      parseTimeKey(_draft.time) ?? TimeOfDay.now(),
                );
                if (picked != null) {
                  apply(() => _draft.time = timeKey(picked));
                }
              },
              child: Text(_draft.time ?? '选时间'),
            ),
            IconButton(
              icon: const Icon(Icons.clear),
              onPressed: () => apply(() {
                _draft.due = null;
                _draft.time = null;
              }),
            ),
          ],
        ),
      ];
    }
    if (kind == 'repeat') {
      return [
        const Text('循环任务'),
        Wrap(
          spacing: 8,
          children: [
            for (final option in repeatLabels.entries)
              ChoiceChip(
                label: Text(option.value),
                selected: _draft.repeatFreq == option.key,
                onSelected: (_) =>
                    apply(() => _draft.repeatFreq = option.key),
              ),
          ],
        ),
        if (_draft.repeatFreq != 'none')
          Row(
            children: [
              const Text('每'),
              const SizedBox(width: 8),
              SizedBox(
                width: 90,
                child: TextFormField(
                  initialValue: '${_draft.repeatN}',
                  keyboardType: TextInputType.number,
                  onChanged: (value) =>
                      _draft.repeatN = clampRepeatN(value),
                ),
              ),
              const SizedBox(width: 8),
              Text(repeatUnits[_draft.repeatFreq] ?? '天'),
            ],
          ),
      ];
    }
    if (kind == 'tag') {
      return [
        const Text('标签 · 一条可以有多个'),
        Wrap(
          spacing: 8,
          children: [
            for (final tag in tags)
              FilterChip(
                label: Text('#${tag.name}'),
                selected: _draft.tags.contains(tag.name),
                onSelected: (_) => apply(() {
                  if (!_draft.tags.add(tag.name)) {
                    _draft.tags.remove(tag.name);
                  }
                }),
              ),
          ],
        ),
        TextField(
          decoration: const InputDecoration(hintText: '新建标签，回车添加'),
          onSubmitted: (value) {
            final name = value.trim().replaceFirst('#', '');
            if (name.isNotEmpty) apply(() => _draft.tags.add(name));
          },
        ),
      ];
    }
    return [
      const Text('优先级'),
      Wrap(
        spacing: 8,
        children: [
          for (var index = 0; index < _priorityLabels.length; index++)
            ChoiceChip(
              label: Text(_priorityLabels[index]),
              selected: _draft.priority == index,
              onSelected: (_) => apply(() => _draft.priority = index),
            ),
        ],
      ),
    ];
  }

  @override
  Widget build(BuildContext context) {
    final tokens = Theme.of(context).extension<NibTokens>()!;
    final dueLabel = _dueLabel();
    final chips = <Widget>[
      if (dueLabel.isNotEmpty) Chip(label: Text(dueLabel)),
      if (_draft.repeatFreq != 'none')
        Chip(label: Text(repeatLabel(_draft.repeatFreq, _draft.repeatN))),
      if (_draft.priority > 0)
        Chip(label: Text('${_priorityLabels[_draft.priority]}优先级')),
      for (final tag in _draft.tags) Chip(label: Text('#$tag')),
    ];
    final tools = <MapEntry<String, IconData>>[
      const MapEntry('清单', Icons.folder_outlined),
      const MapEntry('日期', Icons.event_outlined),
      const MapEntry('重复', Icons.repeat),
      const MapEntry('标签', Icons.label_outline),
      const MapEntry('优先级', Icons.flag_outlined),
    ];
    final active = <String, bool>{
      '清单': _draft.listId != null,
      '日期': _draft.due != null,
      '重复': _draft.repeatFreq != 'none',
      '标签': _draft.tags.isNotEmpty,
      '优先级': _draft.priority > 0,
    };
    return Scaffold(
      appBar: AppBar(title: const Text('速记')),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            TextField(
              controller: _title,
              autofocus: true,
              decoration: const InputDecoration(hintText: '想到什么就写什么'),
            ),
            TextField(
              controller: _body,
              maxLines: 3,
              decoration: const InputDecoration(hintText: '正文与备注'),
            ),
            const SizedBox(height: 10),
            Align(
              alignment: Alignment.centerLeft,
              child: Wrap(spacing: 6, runSpacing: 2, children: chips),
            ),
            const Spacer(),
            Row(
              children: [
                for (final tool in tools)
                  Expanded(
                    child: InkWell(
                      onTap: () => _openTool(tool.key),
                      child: Padding(
                        padding: const EdgeInsets.symmetric(vertical: 8),
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Icon(
                              tool.value,
                              size: 19,
                              color: active[tool.key]!
                                  ? tokens.accent
                                  : tokens.textMuted,
                            ),
                            const SizedBox(height: 3),
                            Text(tool.key,
                                style: TextStyle(
                                    fontSize: 10, color: tokens.textMuted)),
                          ],
                        ),
                      ),
                    ),
                  ),
              ],
            ),
            const SizedBox(height: 8),
            SizedBox(
              width: double.infinity,
              child: FilledButton(
                onPressed: _save,
                child: const Text('保存'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
