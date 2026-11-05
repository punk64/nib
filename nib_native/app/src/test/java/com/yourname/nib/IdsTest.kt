package com.yourname.nib

import com.yourname.nib.core.localDeviceId
import com.yourname.nib.core.newSortKey
import com.yourname.nib.core.newUlid
import com.yourname.nib.core.nextHlc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IdsTest {
    @Test
    fun ulidLengthAndAlphabet() {
        val id = newUlid()
        assertEquals(26, id.length)
        assertTrue(id.all { it in "0123456789ABCDEFGHJKMNPQRSTVWXYZ" })
    }

    @Test
    fun ulidsAreUnique() {
        val ids = (0 until 1000).map { newUlid() }.toSet()
        assertEquals(1000, ids.size)
    }

    @Test
    fun hlcNeverGoesBackwards() {
        val first = nextHlc()
        val second = nextHlc()
        assertTrue(second >= first)
        assertTrue(first.endsWith(localDeviceId()))
    }

    @Test
    fun sortKeyIsTimePrefixed() {
        val key = newSortKey()
        assertEquals(16, key.substringBefore('-').length)
        assertTrue(key.substringBefore('-').all { it.isDigit() })
    }
}
