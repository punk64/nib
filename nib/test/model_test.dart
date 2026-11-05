import 'package:flutter_test/flutter_test.dart';

import 'package:nib/core/model/filter.dart';
import 'package:nib/core/model/repeat.dart';
import 'package:nib/core/parse/parse.dart';

void main() {
  group('双层筛选', () {
    test('全部清单不过滤', () {
      expect(matchesFilter(const EntryFilter(), 'work', {'a'}), isTrue);
    });
    test('清单单选', () {
      const filter = EntryFilter(listId: 'work');
      expect(matchesFilter(filter, 'work', <String>{}), isTrue);
      expect(matchesFilter(filter, 'life', <String>{}), isFalse);
    });
    test('未分类只匹配空 listId', () {
      const filter = EntryFilter(listId: EntryFilter.noList);
      expect(matchesFilter(filter, null, <String>{}), isTrue);
      expect(matchesFilter(filter, 'work', <String>{}), isFalse);
    });
    test('标签多选按 AND 合并', () {
      const filter = EntryFilter(tagIds: {'a', 'b'});
      expect(matchesFilter(filter, 'work', {'a', 'b', 'c'}), isTrue);
      expect(matchesFilter(filter, 'work', {'a'}), isFalse);
      expect(matchesFilter(filter, 'work', <String>{}), isFalse);
    });
  });

  group('搜索', () {
    test('空关键词全部命中', () {
      expect(matchesSearch('', '标题', '正文', ['标签']), isTrue);
    });
    test('标题正文标签都参与', () {
      expect(matchesSearch('周报', '交周报', '', []), isTrue);
      expect(matchesSearch('8842', '投影仪密码', '8842', []), isTrue);
      expect(matchesSearch('工作', '任意', '', ['工作']), isTrue);
      expect(matchesSearch('无关', '任意', '', ['工作']), isFalse);
    });
    test('英文大小写不敏感', () {
      expect(matchesSearch('NIB', 'nib 记录', '', []), isTrue);
    });
  });

  group('循环间隔', () {
    test('边界 1 到 999', () {
      expect(clampRepeatN('1'), 1);
      expect(clampRepeatN('37'), 37);
      expect(clampRepeatN('999'), 999);
    });
    test('越界与非法输入回落', () {
      expect(clampRepeatN('0'), 1);
      expect(clampRepeatN('1000'), 999);
      expect(clampRepeatN(''), 1);
      expect(clampRepeatN('abc'), 1);
    });
    test('标签文案', () {
      expect(repeatLabel('week', 2), '每 2 周');
      expect(repeatLabel('none', 1), '不重复');
      expect(repeatLabel('day', 37), '每 37 天');
    });
  });

  group('M0 验收', () {
    test('明天10点 交周报 #工作 !!', () {
      final note = parse('明天10点 交周报 #工作 !!',
          today: DateTime(2026, 11, 4));
      expect(note.due, '2026-11-05');
      expect(note.time, '10:00');
      expect(note.tags, ['工作']);
      expect(note.priority, 2);
      expect(note.title, '交周报');
    });
    test('每2周 浇花 显示每 2 周', () {
      final note = parse('每2周 浇花', today: DateTime(2026, 11, 4));
      expect(note.repeatFreq, 'week');
      expect(note.repeatN, 2);
      expect(repeatLabel(note.repeatFreq, note.repeatN), '每 2 周');
      expect(note.title, '浇花');
    });
    test('间隔改成 37 后值不丢失', () {
      expect(clampRepeatN('37'), 37);
      expect(repeatLabel('week', clampRepeatN('37')), '每 37 周');
    });
    test('无法解析的纯文本按原文保存', () {
      const raw = '投影仪密码 8842';
      final note = parse(raw, today: DateTime(2026, 11, 4));
      expect(note.title, raw);
      expect(note.due, isNull);
      expect(note.tags, isEmpty);
      expect(note.priority, 0);
      expect(note.repeatFreq, 'none');
    });
  });
}
