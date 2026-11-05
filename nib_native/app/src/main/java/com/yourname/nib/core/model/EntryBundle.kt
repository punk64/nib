package com.yourname.nib.core.model

import com.yourname.nib.core.db.Entry

data class EntryBundle(
    val entry: Entry,
    val tagIds: Set<String> = emptySet(),
    val tagNames: List<String> = emptyList(),
    val listName: String? = null,
)

fun filterBundles(
    all: List<EntryBundle>,
    filter: EntryFilter,
    query: String,
    ftsIds: Set<String>? = null,
): List<EntryBundle> = all.filter { bundle ->
    matchesFilter(filter, bundle.entry.listId, bundle.tagIds) &&
        if (ftsIds != null) bundle.entry.id in ftsIds
        else matchesSearch(query, bundle.entry.title, bundle.entry.body, bundle.tagNames)
}

fun splitByDone(all: List<EntryBundle>): Pair<List<EntryBundle>, List<EntryBundle>> =
    all.partition { it.entry.done == 0 }
