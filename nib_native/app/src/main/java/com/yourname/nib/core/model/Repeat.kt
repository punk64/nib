package com.yourname.nib.core.model

val repeatUnits = mapOf(
    "none" to "",
    "day" to "天",
    "week" to "周",
    "month" to "月",
    "year" to "年",
)

val repeatLabels = mapOf(
    "none" to "不重复",
    "day" to "每天",
    "week" to "每周",
    "month" to "每月",
    "year" to "每年",
)

fun clampRepeatN(raw: String): Int {
    val parsed = raw.trim().toIntOrNull() ?: 1
    return parsed.coerceIn(1, 999)
}

fun repeatLabel(freq: String, n: Int): String =
    if (freq == "none") "不重复" else "每 $n ${repeatUnits[freq] ?: "天"}"
