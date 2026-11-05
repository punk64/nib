class ParsedNote {
  const ParsedNote({
    required this.title,
    required this.raw,
    this.due,
    this.time,
    this.tags = const [],
    this.priority = 0,
    this.repeatFreq = 'none',
    this.repeatN = 1,
  });

  final String title;
  final String raw;
  final String? due;
  final String? time;
  final List<String> tags;
  final int priority;
  final String repeatFreq;
  final int repeatN;
}

const _weekdayNumbers = {
  '一': 1,
  '二': 2,
  '三': 3,
  '四': 4,
  '五': 5,
  '六': 6,
  '日': 0,
  '天': 0,
};

const _repeatFrequencies = {
  '天': 'day',
  '周': 'week',
  '月': 'month',
  '年': 'year',
};

String _two(int value) => value.toString().padLeft(2, '0');

String _ymd(DateTime value) =>
    '${value.year.toString().padLeft(4, '0')}-${_two(value.month)}-${_two(value.day)}';

ParsedNote parse(String raw, {DateTime? today}) {
  final base = today ?? DateTime.now();
  final now = DateTime(base.year, base.month, base.day);
  var s = ' $raw ';
  final tags = <String>[];

  s = s.replaceAllMapped(RegExp(r'#([^\s#]+)'), (match) {
    tags.add(match.group(1)!);
    return ' ';
  });

  var priority = 0;
  if (raw.contains('!!!')) {
    priority = 3;
  } else if (raw.contains('!!')) {
    priority = 2;
  } else if (raw.contains('!')) {
    priority = 1;
  }
  s = s.replaceAll('!!!', ' ').replaceAll('!!', ' ').replaceAll('!', ' ');

  var repeatFreq = 'none';
  var repeatN = 1;
  final repeatMatch =
      RegExp(r'每\s*(\d{1,4})?\s*(天|周|月|年)(?:一次)?').firstMatch(s);
  if (repeatMatch != null) {
    repeatFreq = _repeatFrequencies[repeatMatch.group(2)]!;
    final parsedN = int.tryParse(repeatMatch.group(1) ?? '') ?? 1;
    repeatN = parsedN.clamp(1, 999).toInt();
    s = s.replaceFirst(repeatMatch.group(0)!, ' ');
  }

  DateTime nextWeekday(int target, bool nextWeek) {
    final current = now.weekday % 7;
    if (nextWeek) {
      final monday =
          DateTime(now.year, now.month, now.day - ((current + 6) % 7));
      return DateTime(
          monday.year, monday.month, monday.day + 7 + ((target + 6) % 7));
    }
    var diff = (target - current + 7) % 7;
    if (diff == 0) diff = 7;
    return DateTime(now.year, now.month, now.day + diff);
  }

  var yearShift = 0;
  var yearWord = false;
  if (s.contains('后年')) {
    yearShift = 2;
    yearWord = true;
    s = s.replaceAll('后年', ' ');
  } else if (s.contains('明年')) {
    yearShift = 1;
    yearWord = true;
    s = s.replaceAll('明年', ' ');
  } else if (s.contains('今年')) {
    yearWord = true;
    s = s.replaceAll('今年', ' ');
  }

  DateTime? date;
  var explicitYear = false;
  final relativeToday = RegExp(r'(今天|今晚|今早)').firstMatch(s);
  final relativeTomorrow = RegExp(r'(明天|明早|明晚)').firstMatch(s);
  final nextWeek = RegExp(r'下周([一二三四五六日天])').firstMatch(s);
  final weekday = RegExp(r'(?:周|星期)([一二三四五六日天])').firstMatch(s);
  final absolute =
      RegExp(r'(?:(\d{2,4})年)?(\d{1,2})月(\d{1,2})[日号]').firstMatch(s);

  if (relativeToday != null) {
    date = now;
    s = s.replaceFirst(relativeToday.group(0)!, ' ');
  } else if (relativeTomorrow != null) {
    date = DateTime(now.year, now.month, now.day + 1);
    s = s.replaceFirst(relativeTomorrow.group(0)!, ' ');
  } else if (s.contains('后天')) {
    date = DateTime(now.year, now.month, now.day + 2);
    s = s.replaceFirst('后天', ' ');
  } else if (nextWeek != null) {
    date = nextWeekday(_weekdayNumbers[nextWeek.group(1)]!, true);
    s = s.replaceFirst(nextWeek.group(0)!, ' ');
  } else if (weekday != null) {
    date = nextWeekday(_weekdayNumbers[weekday.group(1)]!, false);
    s = s.replaceFirst(weekday.group(0)!, ' ');
  } else if (absolute != null) {
    final yearText = absolute.group(1);
    final year = yearText != null
        ? (yearText.length == 2
            ? 2000 + int.parse(yearText)
            : int.parse(yearText))
        : base.year + yearShift;
    date = DateTime(
        year, int.parse(absolute.group(2)!), int.parse(absolute.group(3)!));
    explicitYear = yearText != null || yearWord;
    s = s.replaceFirst(absolute.group(0)!, ' ');
  }

  if (date != null && !explicitYear && date.isBefore(now)) {
    date = DateTime(date.year + 1, date.month, date.day);
  }

  String? time;
  final isAfternoon = RegExp(r'下午|晚上|傍晚|今晚|明晚').hasMatch(s);
  final colonTime = RegExp(r'(\d{1,2})[:：](\d{2})').firstMatch(s);
  final hourTime = RegExp(r'(\d{1,2})点(半|\d{1,2}分?)?').firstMatch(s);
  if (colonTime != null) {
    time = '${_two(int.parse(colonTime.group(1)!))}:${colonTime.group(2)}';
    s = s.replaceFirst(colonTime.group(0)!, ' ');
  } else if (hourTime != null) {
    var hour = int.parse(hourTime.group(1)!);
    final suffix = hourTime.group(2);
    final minute = suffix == '半'
        ? 30
        : (suffix != null
            ? int.tryParse(suffix.replaceAll('分', '')) ?? 0
            : 0);
    if (isAfternoon && hour < 12) hour += 12;
    time = '${_two(hour)}:${_two(minute)}';
    s = s.replaceFirst(hourTime.group(0)!, ' ');
  }

  s = s.replaceAll(RegExp(r'上午|下午|晚上|傍晚'), ' ');
  final title = s.replaceAll(RegExp(r'\s+'), ' ').trim();
  var due = date != null ? _ymd(date) : null;
  if (time != null && due == null) due = _ymd(now);

  return ParsedNote(
    title: title,
    raw: raw,
    due: due,
    time: time,
    tags: tags,
    priority: priority,
    repeatFreq: repeatFreq,
    repeatN: repeatN,
  );
}
