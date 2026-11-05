package com.yourname.nib

import com.yourname.nib.core.organize.batchTagLinks
import com.yourname.nib.core.organize.normalizeFtsQuery
import com.yourname.nib.core.organize.pinnedOrder
import com.yourname.nib.core.organize.rebalanceKeys
import com.yourname.nib.core.organize.reorderIds
import com.yourname.nib.core.organize.selectedInOrder
import com.yourname.nib.core.organize.shareTextFor
import com.yourname.nib.core.organize.sortKeyBetween
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrganizePlanTest {
    @Test
    fun midpointNoBoundsIsComparable() {
        val key = sortKeyBetween(null, null)
        assertNotNull(key)
        assertTrue(key!!.isNotEmpty())
        assertTrue(sortKeyBetween(null, key)!! < key)
        assertTrue(sortKeyBetween(key, null)!! > key)
    }

    @Test
    fun midpointBetweenTwoKeysIsStrictlyBetween() {
        val mid = sortKeyBetween("A5", "A9")
        assertNotNull(mid)
        assertTrue("A5" < mid!!)
        assertTrue(mid < "A9")
        val second = sortKeyBetween("A5", mid)
        assertNotNull(second)
        assertTrue("A5" < second!!)
        assertTrue(second < mid)
    }

    @Test
    fun midpointWithNoLowerBoundStaysBeforeNext() {
        val mid = sortKeyBetween(null, "A9")
        assertNotNull(mid)
        assertTrue(mid!! < "A9")
        assertTrue(mid.isNotEmpty())
    }

    @Test
    fun midpointWithNoUpperBoundStaysAfterPrev() {
        val mid = sortKeyBetween("A9", null)
        assertNotNull(mid)
        assertTrue(mid!! > "A9")
    }

    @Test
    fun midpointRejectsReversedBounds() {
        assertNull(sortKeyBetween("A9", "A5"))
        assertNull(sortKeyBetween("A5", "A5"))
    }

    @Test
    fun midpointRejectsKeysOutsideAlphabet() {
        assertNull(sortKeyBetween("a5", "A9"))
        assertNull(sortKeyBetween("A5", "a9"))
        assertNull(sortKeyBetween("A5.1", "A9"))
    }

    @Test
    fun midpointHandlesAdjacentKeys() {
        val mid = sortKeyBetween("A5", "A6")
        assertNotNull(mid)
        assertTrue("A5" < mid!!)
        assertTrue(mid < "A6")
    }

    @Test
    fun midpointReturnsNullWhenGapIsExhausted() {
        assertNull(sortKeyBetween("A5", "A5-"))
        assertNull(sortKeyBetween("", "-"))
    }

    @Test
    fun midpointWorksOnRealSortKeys() {
        val prev = "0000000000000001-01ARZ3NDEKTSV4RRFFQ69G5FAV"
        val next = "0000000000000001-01ARZ3NDEKTSV4RRFFQ69G5FAW"
        val mid = sortKeyBetween(prev, next)
        assertNotNull(mid)
        assertTrue(prev < mid!!)
        assertTrue(mid < next)
    }

    @Test
    fun midpointWorksAcrossTimestampPrefix() {
        val prev = "0000000000000001-01ARZ3NDEKTSV4RRFFQ69G5FAV"
        val next = "0000000000000009-01ARZ3NDEKTSV4RRFFQ69G5FAV"
        val mid = sortKeyBetween(prev, next)
        assertNotNull(mid)
        assertTrue(prev < mid!!)
        assertTrue(mid < next)
    }

    @Test
    fun rebalanceKeysAreStrictlyOrderedInsideBounds() {
        val keys = rebalanceKeys("A1", "A9", 7)
        assertEquals(7, keys.size)
        assertEquals(keys, keys.distinct())
        assertEquals(keys, keys.sorted())
        assertTrue(keys.first() > "A1")
        assertTrue(keys.last() < "A9")
    }

    @Test
    fun rebalanceKeysHandlesEmptyAndSingle() {
        assertEquals(emptyList<String>(), rebalanceKeys("A1", "A9", 0))
        assertEquals(1, rebalanceKeys("A1", "A9", 1).size)
    }

    @Test
    fun normalizeFtsQueryEscapesQuotes() {
        assertEquals("\"周报摘要\"", normalizeFtsQuery("  周报摘要  "))
        assertEquals("\"say \"\"hi\"\"\"", normalizeFtsQuery("say \"hi\""))
        assertEquals("\"abc\"", normalizeFtsQuery("abc"))
    }

    @Test
    fun normalizeFtsQuerySkipsShortAndEmptyInput() {
        assertNull(normalizeFtsQuery(""))
        assertNull(normalizeFtsQuery("  "))
        assertNull(normalizeFtsQuery("ab"))
        assertNull(normalizeFtsQuery("中"))
    }

    @Test
    fun shareTextForCombinesSubjectAndText() {
        assertNull(shareTextFor(null, null))
        assertNull(shareTextFor("  ", " "))
        assertEquals("一段文字", shareTextFor(null, "一段文字"))
        assertEquals("标题", shareTextFor("标题", null))
        assertEquals("标题 一段文字", shareTextFor("标题", "一段文字"))
        assertEquals("标题 https://example.com", shareTextFor("标题", "标题 https://example.com"))
    }

    @Test
    fun selectedInOrderKeepsListOrder() {
        assertEquals(
            listOf("b", "c"),
            selectedInOrder(setOf("c", "b", "missing"), listOf("a", "b", "c", "d")),
        )
        assertEquals(emptyList<String>(), selectedInOrder(emptySet(), listOf("a")))
    }

    @Test
    fun batchTagLinksIsIdempotent() {
        val links = mapOf("a" to setOf("t1"), "b" to emptySet())
        val once = batchTagLinks(links, setOf("a", "b"), "t2")
        assertEquals(setOf("t1", "t2"), once["a"])
        assertEquals(setOf("t2"), once["b"])
        assertEquals(once, batchTagLinks(once, setOf("a", "b"), "t2"))
        assertEquals(setOf("t1"), links["a"])
    }

    @Test
    fun reorderIdsMovesWithinBoundsOnly() {
        val ids = listOf("a", "b", "c", "d")
        assertEquals(listOf("a", "c", "b", "d"), reorderIds(ids, 1, 2))
        assertEquals(listOf("c", "a", "b", "d"), reorderIds(ids, 2, 0))
        assertEquals(ids, reorderIds(ids, 1, 1))
        assertEquals(ids, reorderIds(ids, -1, 2))
        assertEquals(ids, reorderIds(ids, 1, 9))
    }

    @Test
    fun pinnedOrderPutsPinnedFirstThenSortKey() {
        data class Row(val id: String, val pinned: Boolean, val sort: String)
        val rows = listOf(
            Row("a", false, "1"),
            Row("b", true, "9"),
            Row("c", false, "0"),
            Row("d", true, "2"),
        )
        assertEquals(
            listOf("d", "b", "c", "a"),
            pinnedOrder(rows, { it.pinned }, { it.sort }).map { it.id },
        )
    }
}
