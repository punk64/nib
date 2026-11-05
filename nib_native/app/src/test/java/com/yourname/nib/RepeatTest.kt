package com.yourname.nib

import com.yourname.nib.core.model.clampRepeatN
import com.yourname.nib.core.model.repeatLabel
import org.junit.Assert.assertEquals
import org.junit.Test

class RepeatTest {
    @Test
    fun clampBounds() {
        assertEquals(1, clampRepeatN("0"))
        assertEquals(1, clampRepeatN("-5"))
        assertEquals(1, clampRepeatN("abc"))
        assertEquals(37, clampRepeatN(" 37 "))
        assertEquals(999, clampRepeatN("1000"))
    }

    @Test
    fun labels() {
        assertEquals("不重复", repeatLabel("none", 3))
        assertEquals("每 2 周", repeatLabel("week", 2))
        assertEquals("每 1 天", repeatLabel("day", 1))
    }
}
