package com.yourname.nib

import com.yourname.nib.core.model.EntryFilter
import com.yourname.nib.core.model.matchesFilter
import com.yourname.nib.core.model.matchesSearch
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterTest {
    @Test fun allListsDoesNotFilter() {
        assertTrue(matchesFilter(EntryFilter(), "work", setOf("a")))
    }

    @Test fun singleList() {
        val filter = EntryFilter(listId = "work")
        assertTrue(matchesFilter(filter, "work", emptySet()))
        assertFalse(matchesFilter(filter, "life", emptySet()))
    }

    @Test fun noListMatchesNullOnly() {
        val filter = EntryFilter(listId = EntryFilter.NO_LIST)
        assertTrue(matchesFilter(filter, null, emptySet()))
        assertFalse(matchesFilter(filter, "work", emptySet()))
    }

    @Test fun tagsCombineWithAnd() {
        val filter = EntryFilter(tagIds = setOf("a", "b"))
        assertTrue(matchesFilter(filter, "work", setOf("a", "b", "c")))
        assertFalse(matchesFilter(filter, "work", setOf("a")))
        assertFalse(matchesFilter(filter, "work", emptySet()))
    }

    @Test fun emptyQueryMatchesAll() {
        assertTrue(matchesSearch("", "标题", "正文", listOf("标签")))
    }

    @Test fun titleBodyAndTagsSearched() {
        assertTrue(matchesSearch("周报", "交周报", "", emptyList()))
        assertTrue(matchesSearch("8842", "投影仪密码", "8842", emptyList()))
        assertTrue(matchesSearch("工作", "任意", "", listOf("工作")))
        assertFalse(matchesSearch("无关", "任意", "", listOf("工作")))
    }

    @Test fun caseInsensitive() {
        assertTrue(matchesSearch("NIB", "nib 记录", "", emptyList()))
    }
}
