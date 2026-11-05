package com.yourname.nib

import com.yourname.nib.core.db.Entry
import com.yourname.nib.core.widget.recentEntries
import com.yourname.nib.core.widget.todayTodos
import org.junit.Assert.assertEquals
import org.junit.Test

private fun widgetEntry(
    id: String,
    dueDate: String? = "2026-03-01",
    done: Int = 0,
    deletedAt: Long? = null,
): Entry = Entry(
    id = id,
    title = "条目$id",
    dueDate = dueDate,
    done = done,
    deletedAt = deletedAt,
    sort = id,
    hlc = "h",
    deviceId = "d",
    createdAt = 0,
    updatedAt = 0,
)

class WidgetDataTest {
    private val today = "2026-03-01"

    @Test
    fun todayTodosKeepsOnlyVisibleUndoneEntriesForToday() {
        val entries = listOf(
            widgetEntry("a"),
            widgetEntry("b", done = 1),
            widgetEntry("c", deletedAt = 1L),
            widgetEntry("d", dueDate = "2026-03-02"),
            widgetEntry("e", dueDate = null),
        )
        assertEquals(listOf("a"), todayTodos(entries, today).map { it.id })
    }

    @Test
    fun todayTodosRespectsLimit() {
        val entries = (1..10).map { widgetEntry("e$it") }
        assertEquals(4, todayTodos(entries, today, 4).size)
        assertEquals(listOf("e1", "e2", "e3", "e4"), todayTodos(entries, today, 4).map { it.id })
    }

    @Test
    fun recentEntriesSkipsDeletedAndReturnsNewestFirst() {
        val entries = listOf(
            widgetEntry("old"),
            widgetEntry("gone", deletedAt = 1L),
            widgetEntry("mid"),
            widgetEntry("new"),
        )
        assertEquals(listOf("new", "mid", "old"), recentEntries(entries).map { it.id })
    }

    @Test
    fun recentEntriesRespectsLimitAndDoneFlag() {
        val entries = (1..12).map { widgetEntry("e$it", done = if (it == 12) 1 else 0) }
        val rows = recentEntries(entries, 3)
        assertEquals(listOf("e12", "e11", "e10"), rows.map { it.id })
        assertEquals(listOf(true, false, false), rows.map { it.done })
    }
}
