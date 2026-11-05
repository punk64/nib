package com.yourname.nib.core.widget

import android.content.Context
import com.yourname.nib.core.db.Entry
import com.yourname.nib.core.db.NibGraph
import com.yourname.nib.core.time.NibDate

data class WidgetRow(val id: String, val title: String, val done: Boolean)

fun todayTodos(entries: List<Entry>, todayKey: String, limit: Int = 6): List<WidgetRow> =
    entries.filter { it.deletedAt == null && it.done == 0 && it.dueDate == todayKey }
        .take(limit)
        .map { WidgetRow(it.id, it.title, false) }

fun recentEntries(entries: List<Entry>, limit: Int = 8): List<WidgetRow> =
    entries.filter { it.deletedAt == null }
        .takeLast(limit)
        .reversed()
        .map { WidgetRow(it.id, it.title, it.done != 0) }

suspend fun widgetTodos(context: Context): List<WidgetRow> =
    todayTodos(NibGraph.repository(context).allEntries(), NibDate.today().dateKey())

suspend fun widgetRecent(context: Context): List<WidgetRow> =
    recentEntries(NibGraph.repository(context).allEntries())
