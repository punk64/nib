package com.yourname.nib.core.time

data class ClockTime(val hour: Int, val minute: Int)

fun twoDigits(value: Int): String = value.toString().padStart(2, '0')

fun timeKey(value: ClockTime): String = "${twoDigits(value.hour)}:${twoDigits(value.minute)}"

fun parseDateKey(value: String?): NibDate? {
    if (value.isNullOrEmpty()) return null
    val parts = value.split('-')
    if (parts.size != 3) return null
    val year = parts[0].toIntOrNull() ?: return null
    val month = parts[1].toIntOrNull() ?: return null
    val day = parts[2].toIntOrNull() ?: return null
    return NibDate(year, month, day)
}

fun parseTimeKey(value: String?): ClockTime? {
    if (value.isNullOrEmpty()) return null
    val parts = value.split(':')
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    return ClockTime(hour, minute)
}

fun isLeapYear(year: Int): Boolean = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0

fun daysInMonth(year: Int, month: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    2 -> if (isLeapYear(year)) 29 else 28
    else -> 30
}

fun clampDay(year: Int, month: Int, day: Int): Int = day.coerceIn(1, daysInMonth(year, month))

fun stampKey(millis: Long): String {
    val cal = java.util.Calendar.getInstance()
    cal.timeInMillis = millis
    return "${twoDigits(cal.get(java.util.Calendar.MONTH) + 1)}-${twoDigits(cal.get(java.util.Calendar.DAY_OF_MONTH))} " +
        "${twoDigits(cal.get(java.util.Calendar.HOUR_OF_DAY))}:${twoDigits(cal.get(java.util.Calendar.MINUTE))}"
}

