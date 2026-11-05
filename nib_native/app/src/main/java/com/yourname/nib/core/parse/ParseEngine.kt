package com.yourname.nib.core.parse

import com.yourname.nib.core.time.NibDate
import com.yourname.nib.core.time.twoDigits

data class ParsedNote(
    val title: String,
    val raw: String,
    val due: String? = null,
    val time: String? = null,
    val tags: List<String> = emptyList(),
    val priority: Int = 0,
    val repeatFreq: String = "none",
    val repeatN: Int = 1,
)

private val weekdayNumbers = mapOf(
    '一' to 1,
    '二' to 2,
    '三' to 3,
    '四' to 4,
    '五' to 5,
    '六' to 6,
    '日' to 0,
    '天' to 0,
)

private val repeatFrequencies = mapOf(
    "天" to "day",
    "周" to "week",
    "月" to "month",
    "年" to "year",
)

private val tagPattern = Regex("#([^\\s#]+)")
private val repeatPattern = Regex("每\\s*(\\d{1,4})?\\s*(天|周|月|年)(?:一次)?")
private val relativeDays = listOf(
    "大后天" to 3L,
    "大前天" to -3L,
    "后天" to 2L,
    "前天" to -2L,
    "明天" to 1L,
    "明早" to 1L,
    "明晚" to 1L,
    "明日" to 1L,
    "今天" to 0L,
    "今早" to 0L,
    "今晚" to 0L,
    "今日" to 0L,
    "昨天" to -1L,
    "昨早" to -1L,
    "昨晚" to -1L,
    "昨日" to -1L,
)
private val daysLaterPattern = Regex("(\\d{1,3}|[一二两三四五六七八九十]{1,3})\\s*天后")
private val nextWeekPattern = Regex("下周([一二三四五六日天])")
private val weekdayPattern = Regex("(?:周|星期)([一二三四五六日天])")
private val absolutePattern = Regex("(?:(\\d{2,4})年)?(\\d{1,2})月(\\d{1,2})[日号]")
private val periodWords = listOf(
    "凌晨" to 0,
    "早上" to 1,
    "早晨" to 1,
    "清晨" to 1,
    "上午" to 1,
    "中午" to 2,
    "下午" to 3,
    "傍晚" to 4,
    "晚上" to 5,
    "晚间" to 5,
)
private val colonTimePattern = Regex("(\\d{1,2})[:：](\\d{2})")
private val hourTimePattern = Regex("(\\d{1,2})点(半|\\d{1,2}分?)?")
private val whitespacePattern = Regex("\\s+")

private val chineseDigits = mapOf(
    '一' to 1,
    '二' to 2,
    '两' to 2,
    '三' to 3,
    '四' to 4,
    '五' to 5,
    '六' to 6,
    '七' to 7,
    '八' to 8,
    '九' to 9,
)

private fun chineseNumber(text: String): Int? {
    if (text.isEmpty()) return null
    if (text == "十") return 10
    val tenIndex = text.indexOf('十')
    if (tenIndex >= 0) {
        val tens = if (tenIndex == 0) 1 else chineseDigits[text[0]] ?: return null
        val ones = if (tenIndex == text.lastIndex) 0 else chineseDigits[text[tenIndex + 1]] ?: return null
        return tens * 10 + ones
    }
    return chineseDigits[text[0]]
}

private fun adjustHour(hour: Int, period: Int?): Int = when (period) {
    0 -> if (hour == 12) 0 else hour
    2 -> if (hour in 1..5) hour + 12 else hour
    3, 4, 5 -> when {
        hour == 12 -> if (period == 5) 0 else hour
        hour < 12 -> hour + 12
        else -> hour
    }
    else -> hour
}

fun parse(raw: String, today: NibDate = NibDate.today()): ParsedNote {
    val now = today.normalized()
    var s = " $raw "
    val tags = mutableListOf<String>()
    tagPattern.findAll(s).forEach { tags.add(it.groupValues[1]) }
    s = tagPattern.replace(s, " ")

    val priority = when {
        raw.contains("!!!") -> 3
        raw.contains("!!") -> 2
        raw.contains("!") -> 1
        else -> 0
    }
    s = s.replace("!!!", " ").replace("!!", " ").replace("!", " ")

    var repeatFreq = "none"
    var repeatN = 1
    val repeatMatch = repeatPattern.find(s)
    if (repeatMatch != null) {
        repeatFreq = repeatFrequencies[repeatMatch.groupValues[2]] ?: "none"
        repeatN = (repeatMatch.groupValues[1].toIntOrNull() ?: 1).coerceIn(1, 999)
        s = s.replaceFirst(repeatMatch.value, " ")
    }

    fun nextWeekday(target: Int, nextWeek: Boolean): NibDate {
        val current = now.dayOfWeek
        if (nextWeek) {
            val monday = now.plusDays(-(((current + 6) % 7).toLong()))
            return monday.plusDays(7L + ((target + 6) % 7))
        }
        var diff = (target - current + 7) % 7
        if (diff == 0) diff = 7
        return now.plusDays(diff.toLong())
    }

    var yearShift = 0
    var yearWord = false
    when {
        s.contains("后年") -> {
            yearShift = 2
            yearWord = true
            s = s.replace("后年", " ")
        }
        s.contains("明年") -> {
            yearShift = 1
            yearWord = true
            s = s.replace("明年", " ")
        }
        s.contains("今年") -> {
            yearWord = true
            s = s.replace("今年", " ")
        }
    }

    var date: NibDate? = null
    var explicitYear = false
    var rollover = false
    var period: Int? = null
    val relative = relativeDays.firstOrNull { s.contains(it.first) }
    val daysLater = daysLaterPattern.find(s)
    val nextWeek = nextWeekPattern.find(s)
    val weekday = weekdayPattern.find(s)
    val absolute = absolutePattern.find(s)

    when {
        daysLater != null -> {
            val count = (daysLater.groupValues[1].toIntOrNull() ?: chineseNumber(daysLater.groupValues[1]) ?: 0).coerceIn(0, 999)
            date = now.plusDays(count.toLong())
            s = s.replaceFirst(daysLater.value, " ")
        }
        relative != null -> {
            date = now.plusDays(relative.second)
            period = when (relative.first) {
                "今晚", "明晚", "昨晚" -> 5
                "今早", "明早", "昨早" -> 1
                else -> null
            }
            s = s.replaceFirst(relative.first, " ")
        }
        nextWeek != null -> {
            date = nextWeekday(weekdayNumbers[nextWeek.groupValues[1][0]] ?: 0, true)
            s = s.replaceFirst(nextWeek.value, " ")
        }
        weekday != null -> {
            date = nextWeekday(weekdayNumbers[weekday.groupValues[1][0]] ?: 0, false)
            s = s.replaceFirst(weekday.value, " ")
        }
        absolute != null -> {
            val yearText = absolute.groupValues[1].ifEmpty { null }
            val year = if (yearText != null) {
                if (yearText.length == 2) 2000 + yearText.toInt() else yearText.toInt()
            } else {
                now.year + yearShift
            }
            date = NibDate(
                year,
                absolute.groupValues[2].toInt(),
                absolute.groupValues[3].toInt(),
            ).normalized()
            explicitYear = yearText != null || yearWord
            rollover = !explicitYear
            s = s.replaceFirst(absolute.value, " ")
        }
    }

    if (rollover && date != null && date.epochDay < now.epochDay) {
        date = NibDate(date.year + 1, date.month, date.day).normalized()
    }

    if (period == null) period = periodWords.firstOrNull { s.contains(it.first) }?.second
    val colonTime = colonTimePattern.find(s)
    val hourTime = hourTimePattern.find(s)
    var time: String? = null
    when {
        colonTime != null -> {
            val hour = adjustHour(colonTime.groupValues[1].toInt(), period)
            time = "${twoDigits(hour)}:${colonTime.groupValues[2]}"
            s = s.replaceFirst(colonTime.value, " ")
        }
        hourTime != null -> {
            val hour = adjustHour(hourTime.groupValues[1].toInt(), period)
            val suffix = hourTime.groupValues[2].ifEmpty { null }
            val minute = when {
                suffix == "半" -> 30
                suffix != null -> suffix.replace("分", "").toIntOrNull() ?: 0
                else -> 0
            }
            time = "${twoDigits(hour)}:${twoDigits(minute)}"
            s = s.replaceFirst(hourTime.value, " ")
        }
    }

    periodWords.forEach { s = s.replace(it.first, " ") }
    val title = whitespacePattern.replace(s, " ").trim()
    var due = date?.dateKey()
    if (time != null && due == null) due = now.dateKey()

    return ParsedNote(
        title = title,
        raw = raw,
        due = due,
        time = time,
        tags = tags,
        priority = priority,
        repeatFreq = repeatFreq,
        repeatN = repeatN,
    )
}
