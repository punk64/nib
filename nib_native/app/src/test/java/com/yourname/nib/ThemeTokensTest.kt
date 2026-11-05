package com.yourname.nib

import com.yourname.nib.core.theme.ThemeConfig
import com.yourname.nib.core.theme.darken
import com.yourname.nib.core.theme.lift
import com.yourname.nib.core.theme.mix
import com.yourname.nib.core.theme.palettes
import com.yourname.nib.core.theme.resolveTokens
import com.yourname.nib.core.theme.themeStyles
import com.yourname.nib.core.theme.withAlpha
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun luminance(value: Long): Int {
    val r = ((value shr 16) and 0xFF).toInt()
    val g = ((value shr 8) and 0xFF).toInt()
    val b = (value and 0xFF).toInt()
    return (r * 299 + g * 587 + b * 114) / 1000
}

class ThemeTokensTest {
    @Test
    fun palettesAreCompleteAndUnique() {
        assertEquals(12, palettes.size)
        assertEquals(12, palettes.map { it.key }.toSet().size)
        assertEquals(12, palettes.map { it.name }.toSet().size)
        assertTrue(palettes.all { (it.accent shr 24) and 0xFFL == 0xFFL })
    }

    @Test
    fun stylesAreCompleteAndUnique() {
        assertEquals(5, themeStyles.size)
        assertEquals(5, themeStyles.map { it.id }.toSet().size)
        assertEquals(5, themeStyles.map { it.name }.toSet().size)
    }

    @Test
    fun allCombinationsResolve() {
        var count = 0
        for (style in themeStyles) {
            for (palette in palettes) {
                for (dark in listOf(true, false)) {
                    val t = resolveTokens(ThemeConfig(style.id, palette.key, dark))
                    assertEquals(style.id, t.styleId)
                    assertEquals(palette.key, t.colorKey)
                    assertEquals(dark, t.dark)
                    assertEquals(0xFFL, (t.paper shr 24) and 0xFFL)
                    assertTrue((t.accent shr 24) and 0xFFL == 0xFFL)
                    assertTrue((t.bg shr 24) and 0xFFL == 0xFFL)
                    assertTrue(t.radiusCard >= 0)
                    assertTrue(t.radiusChip >= 0)
                    assertTrue(t.radiusSheet >= 0)
                    assertTrue(t.radiusS <= t.radiusM)
                    count++
                }
            }
        }
        assertEquals(120, count)
    }

    @Test
    fun lightAccentIsDarkerThanDarkAccent() {
        for (palette in palettes) {
            val dark = resolveTokens(ThemeConfig("v1", palette.key, true))
            val light = resolveTokens(ThemeConfig("v1", palette.key, false))
            assertTrue(
                palette.key,
                luminance(light.accent) <= luminance(dark.accent),
            )
        }
    }

    @Test
    fun styleChangesShapeTokensWithoutChangingPalette() {
        val v1 = resolveTokens(ThemeConfig("v1", "indigo", true))
        val v2 = resolveTokens(ThemeConfig("v2", "indigo", true))
        val v3a = resolveTokens(ThemeConfig("v3a", "indigo", true))
        assertEquals(v1.accent, v2.accent)
        assertEquals(v1.bg, v2.bg)
        assertEquals(18, v1.radiusCard)
        assertEquals(2, v3a.radiusCard)
        assertNotEquals(v1.grain, v3a.grain)
        assertEquals(0xFFL, (v1.card shr 24) and 0xFFL)
        assertTrue((v2.card shr 24) and 0xFFL < 0xFFL)
        assertNotEquals(v1.text, v3a.text)
        assertTrue(resolveTokens(ThemeConfig("v3c", "indigo", true)).mono)
        assertTrue(v3a.stamp != null)
    }

    @Test
    fun darkModeUsesPaletteBackground() {
        val indigo = resolveTokens(ThemeConfig("v1", "indigo", true))
        val mint = resolveTokens(ThemeConfig("v1", "mint", true))
        assertEquals(0xFF0B0C10L, indigo.bg)
        assertEquals(0xFF071412L, mint.bg)
        val lightMint = resolveTokens(ThemeConfig("v1", "mint", false))
        assertTrue(luminance(lightMint.bg) > 220)
        assertNotEquals(0xFFFAFBFDL, lightMint.bg)
        assertNotEquals(lightMint.bg, resolveTokens(ThemeConfig("v1", "amber", false)).bg)
    }

    @Test
    fun colorHelpersKeepArgbShape() {
        val c = 0xFF7C8CFFL
        assertEquals(0x217C8CFFL, withAlpha(c, 0x21))
        assertEquals(0xFFFFFFFFL, lift(c, 1f))
        assertEquals(c, lift(c, 0f))
        assertEquals(0xFF000000L, darken(c, 0f))
        assertEquals(c, darken(c, 1f))
        assertEquals(0xFF000000L, mix(0xFF000000L, 0xFFFFFFFFL, 0f))
        assertEquals(0xFFFFFFFFL, mix(0xFF000000L, 0xFFFFFFFFL, 1f))
        assertEquals(0xFF7C8CFFL, withAlpha(withAlpha(c, 10), 0xFF))
    }

    @Test
    fun unknownIdsFallBackToDefaults() {
        val t = resolveTokens(ThemeConfig("nope", "nope", false))
        assertEquals("v1", t.styleId)
        assertEquals("indigo", t.colorKey)
        assertEquals(false, t.dark)
    }
}
