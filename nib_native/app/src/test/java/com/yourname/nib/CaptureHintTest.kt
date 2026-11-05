package com.yourname.nib

import com.yourname.nib.core.capture.captureHint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureHintTest {
    @Test
    fun emptyRawFallsBackToPlainNotice() {
        assertEquals("只存原文，回到应用后解析", captureHint(""))
        assertEquals("只存原文，回到应用后解析", captureHint("   "))
    }

    @Test
    fun detectsTagsOnly() {
        assertEquals("已识别到 #工作", captureHint("明天交周报 #工作"))
    }

    @Test
    fun detectsTimeOnly() {
        assertEquals("时间 10:30", captureHint("10:30 牙科复诊"))
    }

    @Test
    fun detectsTagsAndTimeTogether() {
        val hint = captureHint("9:00 交周报 #工作 #紧急")
        assertTrue(hint.contains("已识别到 #工作 #紧急"))
        assertTrue(hint.contains("时间 9:00"))
    }

    @Test
    fun deduplicatesRepeatedTags() {
        assertEquals("已识别到 #工作", captureHint("#工作 开会 #工作"))
    }

    @Test
    fun ignoresOutOfRangeTime() {
        assertEquals("只存原文，回到应用后解析", captureHint("25:99 不合法"))
        assertEquals("只存原文，回到应用后解析", captureHint("12:60 不合法"))
    }

    @Test
    fun doesNotParseFullSemantics() {
        val hint = captureHint("明天10点 交周报 #工作 !!")
        assertTrue(hint.contains("#工作"))
        assertTrue(!hint.contains("明天"))
    }
}
