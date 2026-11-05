package com.yourname.nib

import com.yourname.nib.core.db.Entry
import com.yourname.nib.core.model.EntryBundle
import com.yourname.nib.core.model.EntryFilter
import com.yourname.nib.core.model.draftFromRaw
import com.yourname.nib.core.model.filterBundles
import com.yourname.nib.core.model.splitByDone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun entry(
    id: String,
    listId: String? = null,
    title: String = "条目",
    body: String = "",
    done: Int = 0,
): Entry = Entry(
    id = id,
    title = title,
    body = body,
    listId = listId,
    done = done,
    sort = id,
    hlc = "h",
    deviceId = "d",
    createdAt = 0,
    updatedAt = 0,
)

private fun bundle(
    id: String,
    listId: String? = null,
    tagIds: Set<String> = emptySet(),
    tagNames: List<String> = emptyList(),
    title: String = "条目",
    body: String = "",
    done: Int = 0,
): EntryBundle = EntryBundle(
    entry = entry(id, listId, title, body, done),
    tagIds = tagIds,
    tagNames = tagNames,
)

class BundleTest {
    @Test
    fun filterByListIncludingNoList() {
        val all = listOf(bundle("a", "work"), bundle("b", null), bundle("c", "home"))
        assertEquals(3, filterBundles(all, EntryFilter(), "").size)
        assertEquals(listOf("a"), filterBundles(all, EntryFilter(listId = "work"), "").map { it.entry.id })
        assertEquals(listOf("b"), filterBundles(all, EntryFilter(listId = EntryFilter.NO_LIST), "").map { it.entry.id })
    }

    @Test
    fun filterByTagsUsesAnd() {
        val all = listOf(
            bundle("a", tagIds = setOf("t1", "t2")),
            bundle("b", tagIds = setOf("t1")),
            bundle("c", tagIds = setOf("t2")),
        )
        val result = filterBundles(all, EntryFilter(tagIds = setOf("t1", "t2")), "")
        assertEquals(listOf("a"), result.map { it.entry.id })
    }

    @Test
    fun searchMatchesTitleBodyAndTags() {
        val all = listOf(
            bundle("a", title = "交周报"),
            bundle("b", body = "周报归档"),
            bundle("c", tagNames = listOf("周报")),
            bundle("d", title = "买菜"),
        )
        assertEquals(setOf("a", "b", "c"), filterBundles(all, EntryFilter(), "周报").map { it.entry.id }.toSet())
        assertEquals(0, filterBundles(all, EntryFilter(listId = "work"), "周报").size)
    }

    @Test
    fun filterAndSearchCombineWithAnd() {
        val all = listOf(
            bundle("a", "work", title = "周报"),
            bundle("b", "home", title = "周报"),
            bundle("c", "work", title = "买菜"),
        )
        assertEquals(
            listOf("a"),
            filterBundles(all, EntryFilter(listId = "work"), "周报").map { it.entry.id },
        )
    }

    @Test
    fun splitByDoneSeparates() {
        val all = listOf(bundle("a"), bundle("b", done = 1), bundle("c"))
        val (undone, done) = splitByDone(all)
        assertEquals(listOf("a", "c"), undone.map { it.entry.id })
        assertEquals(listOf("b"), done.map { it.entry.id })
    }

    @Test
    fun draftKeepsRawAndMapsParsedFields() {
        val raw = "明天10点 交周报 #工作 !!"
        val draft = draftFromRaw(raw)
        assertEquals(raw, draft.raw)
        assertFalse(draft.title.contains("#工作"))
        assertFalse(draft.title.contains("!!"))
        assertEquals(listOf("工作"), draft.newTagNames)
        assertEquals(2, draft.priority)
        assertEquals("10:00", draft.dueTime)
        assertNotNull(draft.dueDate)
        assertEquals("none", draft.repeatFreq)
    }

    @Test
    fun draftKeepsPlainTextUnchanged() {
        val raw = "投影仪密码 8842"
        val draft = draftFromRaw(raw)
        assertEquals(raw, draft.raw)
        assertEquals(raw, draft.title)
        assertNull(draft.dueDate)
        assertNull(draft.dueTime)
        assertEquals(0, draft.priority)
        assertTrue(draft.newTagNames.isEmpty())
    }

    @Test
    fun draftMapsRepeatInterval() {
        val draft = draftFromRaw("每2周 浇花")
        assertEquals("week", draft.repeatFreq)
        assertEquals(2, draft.repeatN)
        assertEquals("浇花", draft.title)
    }
}
