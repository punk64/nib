import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/db/database.dart';
import '../../core/model/entry_bundle.dart';
import '../../core/theme/nib_theme.dart';
import '../../providers.dart';
import '../reminder/permission_page.dart';

const listColors = <String>[
  '#6C7CF5',
  '#E8A33D',
  '#3FBFA8',
  '#E2634A',
  '#B06CE8',
  '#8B93A5',
];

Color colorOf(String hex) => Color(int.parse(hex.replaceFirst('#', '0xFF')));

Future<String?> promptText(BuildContext context, String title,
    [String initial = '']) {
  final controller = TextEditingController(text: initial);
  return showDialog<String>(
    context: context,
    builder: (dialogContext) => AlertDialog(
      title: Text(title),
      content: TextField(controller: controller, autofocus: true),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(dialogContext).pop(),
          child: const Text('取消'),
        ),
        FilledButton(
          onPressed: () =>
              Navigator.of(dialogContext).pop(controller.text.trim()),
          child: const Text('确定'),
        ),
      ],
    ),
  );
}

class MePage extends ConsumerWidget {
  const MePage({super.key});

  Future<void> _listDialog(BuildContext context, WidgetRef ref,
      {NoteList? existing}) async {
    final controller = TextEditingController(text: existing?.name ?? '');
    var color = existing?.color ?? listColors.first;
    final result = await showDialog<Map<String, String>>(
      context: context,
      builder: (dialogContext) => StatefulBuilder(
        builder: (sheetContext, setDialog) => AlertDialog(
          title: Text(existing == null ? '新建清单' : '编辑清单'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              TextField(
                controller: controller,
                decoration: const InputDecoration(hintText: '名称'),
              ),
              const SizedBox(height: 14),
              Wrap(
                spacing: 10,
                children: [
                  for (final option in listColors)
                    GestureDetector(
                      onTap: () => setDialog(() => color = option),
                      child: Container(
                        width: 28,
                        height: 28,
                        decoration: BoxDecoration(
                          color: colorOf(option),
                          shape: BoxShape.circle,
                          border: color == option
                              ? Border.all(width: 2, color: Colors.white)
                              : null,
                        ),
                      ),
                    ),
                ],
              ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(dialogContext).pop(),
              child: const Text('取消'),
            ),
            FilledButton(
              onPressed: () => Navigator.of(dialogContext).pop(
                  <String, String>{'name': controller.text.trim(), 'color': color}),
              child: const Text('保存'),
            ),
          ],
        ),
      ),
    );
    if (result == null || result['name']!.isEmpty) return;
    final db = ref.read(dbProvider);
    if (existing == null) {
      await db.createList(result['name']!, result['color']!);
    } else {
      await db.updateList(existing.id,
          name: result['name'], color: result['color']);
    }
  }

  Future<void> _newTag(BuildContext context, WidgetRef ref) async {
    final name = await promptText(context, '新建标签');
    if (name == null || name.isEmpty) return;
    await ref.read(dbProvider).ensureTag(name);
  }

  Future<void> _renameTag(
      BuildContext context, WidgetRef ref, Tag tag) async {
    final name = await promptText(context, '重命名标签', tag.name);
    if (name == null || name.isEmpty) return;
    await ref.read(dbProvider).renameTag(tag.id, name);
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final tokens = Theme.of(context).extension<NibTokens>()!;
    final bundle = ref.watch(bundleProvider(''));
    final config = ref.watch(themeConfigProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('我的')),
      body: ListView(
        children: [
          Padding(
            padding: const EdgeInsets.all(16),
            child: Text(
              '${bundle.entries.length} 条记录 · ${bundle.lists.length} 个清单 · ${bundle.tags.length} 个标签',
              style: TextStyle(color: tokens.textMuted, fontSize: 13),
            ),
          ),
          _heading('清单'),
          for (final list in bundle.lists)
            ListTile(
              leading: CircleAvatar(
                radius: 8,
                backgroundColor: colorOf(list.color),
              ),
              title: Text(list.name),
              trailing: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  IconButton(
                    icon: const Icon(Icons.edit_outlined, size: 18),
                    onPressed: () =>
                        _listDialog(context, ref, existing: list),
                  ),
                  IconButton(
                    icon: const Icon(Icons.delete_outline, size: 18),
                    onPressed: () =>
                        ref.read(dbProvider).deleteList(list.id),
                  ),
                ],
              ),
            ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: OutlinedButton.icon(
              onPressed: () => _listDialog(context, ref),
              icon: const Icon(Icons.add, size: 16),
              label: const Text('新建清单'),
            ),
          ),
          _heading('标签'),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Wrap(
              spacing: 8,
              children: [
                for (final tag in bundle.tags)
                  InputChip(
                    label: Text('#${tag.name}'),
                    onPressed: () => _renameTag(context, ref, tag),
                    onDeleted: () => ref.read(dbProvider).deleteTag(tag.id),
                  ),
                ActionChip(
                  label: const Text('+ 新建'),
                  onPressed: () => _newTag(context, ref),
                ),
              ],
            ),
          ),
          _heading('外观'),
          SwitchListTile(
            title: const Text('深色模式'),
            value: config.brightness == Brightness.dark,
            onChanged: (_) =>
                ref.read(themeConfigProvider.notifier).toggleBrightness(),
          ),
          ListTile(
            title: const Text('主题风格'),
            trailing: Text(config.styleId,
                style: TextStyle(color: tokens.textMuted, fontSize: 12)),
          ),
          ListTile(
            title: const Text('主题颜色'),
            trailing: Text(config.paletteId,
                style: TextStyle(color: tokens.textMuted, fontSize: 12)),
          ),
          _heading('提醒'),
          ListTile(
            leading: const Icon(Icons.notifications_outlined),
            title: const Text('权限与精确闹钟'),
            trailing: const Icon(Icons.chevron_right, size: 18),
            onTap: () => Navigator.of(context).push(
              MaterialPageRoute<void>(builder: (_) => const PermissionPage()),
            ),
          ),
        ],
      ),
    );
  }

  Widget _heading(String text) => Padding(
        padding: const EdgeInsets.fromLTRB(16, 20, 16, 6),
        child: Text(text,
            style: const TextStyle(fontSize: 12, color: Colors.grey)),
      );
}
