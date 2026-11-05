const repeatUnits = <String, String>{
  'none': '',
  'day': '天',
  'week': '周',
  'month': '月',
  'year': '年',
};

const repeatLabels = <String, String>{
  'none': '不重复',
  'day': '每天',
  'week': '每周',
  'month': '每月',
  'year': '每年',
};

int clampRepeatN(String raw) {
  final parsed = int.tryParse(raw.trim()) ?? 1;
  if (parsed < 1) return 1;
  if (parsed > 999) return 999;
  return parsed;
}

String repeatLabel(String freq, int n) =>
    freq == 'none' ? '不重复' : '每 $n ${repeatUnits[freq] ?? '天'}';
