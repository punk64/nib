package com.yourname.nib.core.model

data class EntryFilter(
    val listId: String = ALL_LISTS,
    val tagIds: Set<String> = emptySet(),
) {
    companion object {
        const val ALL_LISTS = "all"
        const val NO_LIST = "__none"
    }
}

fun matchesFilter(
    filter: EntryFilter,
    entryListId: String?,
    entryTagIds: Set<String>,
): Boolean {
    val byList = when (filter.listId) {
        EntryFilter.ALL_LISTS -> true
        EntryFilter.NO_LIST -> entryListId == null
        else -> entryListId == filter.listId
    }
    if (!byList) return false
    return filter.tagIds.all { it in entryTagIds }
}

fun matchesSearch(
    query: String,
    title: String,
    body: String,
    tagNames: Iterable<String>,
): Boolean {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return true
    return title.lowercase().contains(q) ||
        body.lowercase().contains(q) ||
        tagNames.any { it.lowercase().contains(q) }
}
