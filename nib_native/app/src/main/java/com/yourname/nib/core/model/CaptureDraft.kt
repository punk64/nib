package com.yourname.nib.core.model

import com.yourname.nib.core.parse.parse

data class CaptureDraft(
    val title: String,
    val raw: String,
    val dueDate: String? = null,
    val dueTime: String? = null,
    val listId: String? = null,
    val tagIds: Set<String> = emptySet(),
    val newTagNames: List<String> = emptyList(),
    val priority: Int = 0,
    val repeatFreq: String = "none",
    val repeatN: Int = 1,
    val leadMinutes: Int = 30,
    val channel: String = "notify",
    val durationMin: Int = 30,
)

fun draftFromRaw(raw: String): CaptureDraft {
    val parsed = parse(raw)
    return CaptureDraft(
        title = parsed.title.ifBlank { raw },
        raw = raw,
        dueDate = parsed.due,
        dueTime = parsed.time,
        newTagNames = parsed.tags,
        priority = parsed.priority,
        repeatFreq = parsed.repeatFreq,
        repeatN = parsed.repeatN,
    )
}
