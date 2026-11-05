package com.yourname.nib.core.capture

private val tagPattern = Regex("#[^\\s#]+")
private val timePattern = Regex("\\b([01]?\\d|2[0-3]):[0-5]\\d\\b")

fun captureHint(raw: String): String {
    val tags = tagPattern.findAll(raw).map { it.value }.distinct().toList()
    val time = timePattern.find(raw)?.value
    val parts = buildList {
        if (tags.isNotEmpty()) add("已识别到 ${tags.joinToString(" ")}")
        if (time != null) add("时间 $time")
    }
    return parts.joinToString(" · ").ifEmpty { "只存原文，回到应用后解析" }
}
