import 'package:flutter_test/flutter_test.dart';

import 'package:nib/core/parse/parse.dart';

final _today = DateTime(2026, 11, 4);

ParsedNote _p(String raw, {DateTime? today}) =>
    parse(raw, today: today ?? _today);

void main() {
  group('相对日期', () {
    test('今天', () {
      final r = _p('今天 买菜');
      expect(r.due, '2026-11-04');
      expect(r.title, '买菜');
    });
    test('明天带时间与标签优先级', () {
      final r = _p('明天10点 交周报 #工作 !!');
      expect(r.due, '2026-11-05');
      expect(r.time, '10:00');
      expect(r.title, '交周报');
      expect(r.tags, ['工作']);
      expect(r.priority, 2);
    });
    test('后天', () {
      expect(_p('后天 交房租').due, '2026-11-06');
    });
    test('今晚归今天', () {
      final r = _p('今晚 看电影');
      expect(r.due, '2026-11-04');
      expect(r.title, '看电影');
      expect(r.time, isNull);
    });
    test('明早', () {
      expect(_p('明早 跑步').due, '2026-11-05');
    });
    test('只写时间落在今天', () {
      final r = _p('10点');
      expect(r.due, '2026-11-04');
      expect(r.time, '10:00');
      expect(r.title, '');
    });
    test('只写日期不带时间', () {
      final r = _p('明天 开会');
      expect(r.due, '2026-11-05');
      expect(r.time, isNull);
      expect(r.title, '开会');
    });
  });

  group('星期', () {
    test('周几取未来最近', () {
      expect(_p('周三 例会', today: DateTime(2026, 11, 2)).due, '2026-11-04');
    });
    test('今天就是该星期则顺延一周', () {
      expect(_p('周三 例会').due, '2026-11-11');
    });
    test('下周三按下周计算', () {
      expect(_p('下周三 复盘', today: DateTime(2026, 11, 2)).due, '2026-11-11');
    });
    test('星期三写法', () {
      expect(_p('星期三 例会', today: DateTime(2026, 11, 2)).due, '2026-11-04');
    });
  });

  group('绝对日期与年份', () {
    test('无年份且已过滚次年', () {
      expect(_p('3月18号 春游').due, '2027-03-18');
    });
    test('四位显式年份不滚', () {
      expect(_p('2027年3月18号 出差').due, '2027-03-18');
    });
    test('两位年份补 2000', () {
      expect(_p('27年3月18号').due, '2027-03-18');
    });
    test('明年前缀', () {
      expect(_p('明年3月18号').due, '2027-03-18');
    });
    test('后年前缀', () {
      expect(_p('后年1月1号').due, '2028-01-01');
    });
    test('日与号两种写法', () {
      expect(_p('3月18日 回程', today: DateTime(2026, 3, 1)).due, '2026-03-18');
    });
    test('月末溢出按 DateTime 规范化', () {
      expect(_p('2月29号 复查', today: DateTime(2026, 1, 1)).due, '2026-03-01');
    });
  });

  group('时间', () {
    test('点半', () {
      expect(_p('10点半 开会').time, '10:30');
    });
    test('点分', () {
      expect(_p('10点30分 开会').time, '10:30');
    });
    test('冒号', () {
      expect(_p('10:30 开会').time, '10:30');
    });
    test('下午三点', () {
      final r = _p('下午3点 面试');
      expect(r.time, '15:00');
      expect(r.title, '面试');
    });
    test('晚上八点', () {
      expect(_p('晚上8点 直播').time, '20:00');
    });
    test('上午不误加十二小时', () {
      expect(_p('上午9点 站会').time, '09:00');
    });
  });

  group('标签与优先级', () {
    test('多标签', () {
      final r = _p('#工作 #Q3 任务');
      expect(r.tags, ['工作', 'Q3']);
      expect(r.title, '任务');
    });
    test('低优先级', () {
      expect(_p('提醒!').priority, 1);
    });
    test('中优先级', () {
      expect(_p('重要!!').priority, 2);
    });
    test('高优先级', () {
      expect(_p('重要!!!').priority, 3);
    });
    test('感叹号全被剔除', () {
      expect(_p('高!!! 低!').title, '高 低');
    });
  });

  group('循环', () {
    test('每2周', () {
      final r = _p('每2周 浇花');
      expect(r.repeatFreq, 'week');
      expect(r.repeatN, 2);
      expect(r.title, '浇花');
    });
    test('每3周一次', () {
      final r = _p('每3周一次 健身');
      expect(r.repeatFreq, 'week');
      expect(r.repeatN, 3);
    });
    test('每月默认一', () {
      final r = _p('每月 缴费');
      expect(r.repeatFreq, 'month');
      expect(r.repeatN, 1);
    });
    test('每年', () {
      expect(_p('每年 体检').repeatFreq, 'year');
    });
    test('间隔上限 999', () {
      expect(_p('每1000天 检查').repeatN, 999);
    });
  });

  group('解析失败与原文', () {
    test('纯文本按原文保存', () {
      final r = _p('投影仪密码 8842');
      expect(r.title, '投影仪密码 8842');
      expect(r.due, isNull);
      expect(r.time, isNull);
      expect(r.tags, isEmpty);
      expect(r.priority, 0);
      expect(r.repeatFreq, 'none');
    });
    test('带数字的内容不丢', () {
      expect(_p('买牛奶 2瓶').title, '买牛奶 2瓶');
    });
    test('raw 始终保留输入原文', () {
      const raw = '明天10点 交周报 #工作 !!';
      expect(_p(raw).raw, raw);
    });
  });
}
