package com.yourname.nib.core.theme

data class ThemeStyle(
    val id: String,
    val name: String,
    val desc: String,
    val radius: Int,
    val glow: Int,
    val grain: Int,
    val tilt: Int,
    val density: Int,
    val thumbA: Long,
    val thumbB: Long,
)

val themeStyles = listOf(
    ThemeStyle("v1", "原味", "简洁卡片 · 基础配色", 18, 46, 6, 0, 50, 0xFF222632L, 0xFF0B0C10L),
    ThemeStyle("v2", "极光玻璃", "极光光斑 · 玻璃拟态", 18, 62, 6, 0, 50, 0xFF3B2F6BL, 0xFF0B0C10L),
    ThemeStyle("v3a", "暗房胶片", "安全灯 · 拍立得相纸", 2, 62, 9, 16, 50, 0xFF4A3418L, 0xFF0A0A08L),
    ThemeStyle("v3b", "纸感编辑", "报头分栏 · 剪报便签", 0, 30, 6, 12, 50, 0xFFEFE6D2L, 0xFFC9B894L),
    ThemeStyle("v3c", "终端 HUD", "扫描线 · 命令行日志", 0, 62, 50, 0, 50, 0xFF0C2A17L, 0xFF030806L),
)

data class Palette(
    val key: String,
    val name: String,
    val accent: Long,
    val bg: Long,
    val paper: Long,
)

val palettes = listOf(
    Palette("indigo", "靛蓝", 0xFF7C8CFFL, 0xFF0B0C10L, 0xFFE9EDFFL),
    Palette("violet", "霓虹紫", 0xFFC084FCL, 0xFF100B1CL, 0xFFF2E7FFL),
    Palette("ember", "熔金橙", 0xFFFF8A4CL, 0xFF150D0CL, 0xFFFFE8D9L),
    Palette("mint", "深海青", 0xFF2EE6B0L, 0xFF071412L, 0xFFDCF8EEL),
    Palette("vermilion", "朱砂红", 0xFFE2634AL, 0xFF0D0706L, 0xFFF8E0D7L),
    Palette("amber", "琥珀黄", 0xFFE8B24AL, 0xFF0A0A08L, 0xFFF7EDD2L),
    Palette("ice", "冰川蓝", 0xFF6FD8FFL, 0xFF03080CL, 0xFFDCEFFFL),
    Palette("magenta", "品红", 0xFFFF6AD5L, 0xFF0A0409L, 0xFFFBDFF4L),
    Palette("phosphor", "磷光绿", 0xFF5CFF9DL, 0xFF050806L, 0xFFD9F8E3L),
    Palette("silver", "象牙灰", 0xFFD8D8D0L, 0xFF080808L, 0xFFECECE7L),
    Palette("sage", "草木绿", 0xFF7FAE7AL, 0xFF0A0F0AL, 0xFFE4EFE0L),
    Palette("rose", "玫瑰金", 0xFFD97757L, 0xFF140D0AL, 0xFFF6E2D7L),
)

data class StyleTuning(
    val glow: Int,
    val radius: Int,
    val grain: Int,
    val tilt: Int,
    val density: Int,
) {
    companion object {
        fun of(style: ThemeStyle) = StyleTuning(style.glow, style.radius, style.grain, style.tilt, style.density)
    }
}

data class TuneField(val key: String, val label: String, val min: Int, val max: Int, val suffix: String = "")

fun tuneFields(styleId: String): List<TuneField> = when (styleId) {
    "v1", "v2" -> listOf(
        TuneField("glow", "辉光强度", 0, 100, "%"),
        TuneField("radius", "圆角", 8, 28),
    )
    "v3a" -> listOf(
        TuneField("glow", "安全灯亮度", 0, 100, "%"),
        TuneField("grain", "颗粒", 0, 30),
        TuneField("tilt", "相纸倾斜", 0, 40),
        TuneField("density", "行密度", 30, 80),
    )
    "v3b" -> listOf(
        TuneField("glow", "印色浓度", 0, 100, "%"),
        TuneField("grain", "纸纹", 0, 30),
        TuneField("tilt", "剪报倾斜", 0, 40),
        TuneField("density", "行密度", 30, 80),
    )
    "v3c" -> listOf(
        TuneField("glow", "荧光强度", 0, 100, "%"),
        TuneField("grain", "扫描线", 0, 100),
        TuneField("density", "行密度", 30, 80),
    )
    else -> emptyList()
}

fun StyleTuning.value(key: String): Int = when (key) {
    "glow" -> glow
    "radius" -> radius
    "grain" -> grain
    "tilt" -> tilt
    else -> density
}

fun StyleTuning.withValue(key: String, next: Int): StyleTuning = when (key) {
    "glow" -> copy(glow = next)
    "radius" -> copy(radius = next)
    "grain" -> copy(grain = next)
    "tilt" -> copy(tilt = next)
    else -> copy(density = next)
}

data class ThemeConfig(
    val styleId: String = "v2",
    val colorKey: String = "indigo",
    val dark: Boolean = true,
    val tuning: Map<String, StyleTuning> = emptyMap(),
) {
    val style: ThemeStyle get() = themeStyles.firstOrNull { it.id == styleId } ?: themeStyles[0]
    val palette: Palette get() = palettes.firstOrNull { it.key == colorKey } ?: palettes[0]
    val tune: StyleTuning get() = tuning[styleId] ?: StyleTuning.of(style)

    fun tuneValue(key: String): Int = tune.value(key)

    fun withTune(key: String, next: Int): ThemeConfig =
        copy(tuning = tuning + (styleId to tune.withValue(key, next)))

    fun resetTune(): ThemeConfig = copy(tuning = tuning - styleId)
}

data class NibTokens(
    val dark: Boolean,
    val bg: Long,
    val card: Long,
    val card2: Long,
    val card3: Long,
    val glass: Long,
    val line: Long,
    val line2: Long,
    val text: Long,
    val text2: Long,
    val text3: Long,
    val accent: Long,
    val accent2: Long,
    val accentWeak: Long,
    val danger: Long,
    val dangerWeak: Long,
    val ok: Long,
    val warn: Long,
    val paper: Long,
    val ink: Long,
    val dim: Long,
    val panel: Long,
    val aurora2: Long,
    val aurora3: Long,
    val ambient: Int,
    val radiusS: Int,
    val radiusM: Int,
    val radiusCard: Int,
    val radiusXL: Int,
    val radiusChip: Int,
    val radiusSheet: Int,
    val glow: Int,
    val grain: Int,
    val tilt: Int,
    val density: Int,
    val styleId: String,
    val colorKey: String,
    val mono: Boolean = false,
    val serif: Boolean = false,
    val outline: Int = 0,
    val cardBorder: Long = line,
    val stamp: String? = null,
)

private fun channel(c: Long, shift: Int): Int = ((c shr shift) and 0xFF).toInt()

private fun composeColor(a: Int, r: Int, g: Int, b: Int): Long =
    ((a.toLong() and 0xFF) shl 24) or
        ((r.toLong() and 0xFF) shl 16) or
        ((g.toLong() and 0xFF) shl 8) or
        (b.toLong() and 0xFF)

fun withAlpha(c: Long, a: Int): Long =
    composeColor(a.coerceIn(0, 255), channel(c, 16), channel(c, 8), channel(c, 0))

fun mix(a: Long, b: Long, f: Float): Long {
    val t = f.coerceIn(0f, 1f)
    return composeColor(
        channel(a, 24) + ((channel(b, 24) - channel(a, 24)) * t).toInt(),
        channel(a, 16) + ((channel(b, 16) - channel(a, 16)) * t).toInt(),
        channel(a, 8) + ((channel(b, 8) - channel(a, 8)) * t).toInt(),
        channel(a, 0) + ((channel(b, 0) - channel(a, 0)) * t).toInt(),
    )
}

fun lift(c: Long, f: Float): Long = mix(c, 0xFFFFFFFFL, f)

fun darken(c: Long, f: Float): Long = mix(c, 0xFF000000L, 1f - f)

fun alphaOf(c: Long): Int = channel(c, 24)

fun resolveTokens(config: ThemeConfig): NibTokens {
    val p = config.palette
    val s = config.style
    val tune = config.tune
    val style = s.id
    val dark = config.dark

    val accent = if (dark) p.accent else mix(p.accent, 0xFF101526L, 0.16f)
    val bg = if (dark) p.bg else mix(accent, 0xFFFFFFFFL, 0.94f)
    val paper = if (dark) mix(bg, accent, 0.10f) else mix(accent, 0xFFFFFFFFL, 0.90f)
    val frame = mix(bg, if (dark) 0xFFFFFFFFL else 0xFF101010L, if (dark) 0.07f else 0.05f)
    val baseText = if (dark) 0xFFF1F3F8L else 0xFF101526L

    var card = withAlpha(frame, if (dark) 219 else 242)
    var card2 = mix(frame, if (dark) 0xFFFFFFFFL else 0xFF000000L, 0.08f)
    var card3 = mix(frame, if (dark) 0xFFFFFFFFL else 0xFF000000L, 0.15f)
    var glass = withAlpha(frame, 230)
    var line = if (dark) withAlpha(0xFFFFFFFFL, 23) else withAlpha(0xFF11182CL, 26)
    var line2 = if (dark) withAlpha(0xFFFFFFFFL, 43) else withAlpha(0xFF11182CL, 46)
    var text = baseText
    var text2 = mix(baseText, bg, if (dark) 0.32f else 0.42f)
    var text3 = mix(baseText, bg, if (dark) 0.54f else 0.58f)
    var accent2 = if (dark) mix(accent, 0xFFFFFFFFL, 0.20f) else mix(accent, 0xFF101526L, 0.18f)
    var danger = 0xFFE2634AL
    var ok = if (dark) 0xFF34D399L else 0xFF1D8A4BL
    var warn = if (dark) 0xFFE8B24AL else 0xFFA96B00L
    var aurora2 = mix(accent, 0xFF34D399L, 0.52f)
    var aurora3 = mix(accent, 0xFFFF5FA2L, 0.46f)
    var ambient = if (dark) 128 else 77
    var ink = if (dark) 0xFF100F0CL else 0xFF211F1AL
    var panel = mix(bg, accent, if (dark) 0.05f else 0.02f)
    var radiusS = (tune.radius * 0.5f).toInt()
    var radiusM = (tune.radius * 0.74f).toInt()
    var radiusCard = tune.radius
    var radiusXL = (tune.radius * 1.35f).toInt()
    var mono = false
    var serif = false
    var outline = 0
    var stamp: String? = null

    when (style) {
        "v1" -> {
            card = frame
            card2 = mix(frame, if (dark) 0xFFFFFFFFL else 0xFF000000L, 0.07f)
            glass = withAlpha(frame, 209)
        }
        "v2" -> {
            card = withAlpha(frame, if (dark) 214 else 235)
        }
        "v3a" -> {
            card = frame
            glass = withAlpha(frame, 235)
            text = if (dark) 0xFFE9E4D6L else 0xFF211F1AL
            text2 = mix(text, bg, 0.32f)
            text3 = mix(text, bg, 0.55f)
            line = if (dark) withAlpha(0xFFE9E4D6L, 31) else withAlpha(0xFF211F1AL, 41)
            line2 = if (dark) withAlpha(0xFFE9E4D6L, 66) else withAlpha(0xFF211F1AL, 77)
            radiusS = 1
            radiusM = 2
            radiusCard = tune.radius
            radiusXL = 4
            danger = 0xFFE2634AL
            ok = 0xFF7FD1C0L
            warn = 0xFFE8B24AL
            aurora2 = accent
            aurora3 = accent
            ambient = 77
            ink = if (dark) 0xFF100F0CL else 0xFF211F1AL
            stamp = "NIB 400"
        }
        "v3b" -> {
            card = frame
            glass = withAlpha(frame, 235)
            text = if (dark) 0xFFE8E0CDL else 0xFF1A1815L
            text2 = mix(text, bg, 0.32f)
            text3 = mix(text, bg, 0.55f)
            line = if (dark) withAlpha(0xFFE8E0CDL, 36) else withAlpha(0xFF1A1815L, 41)
            line2 = if (dark) withAlpha(0xFFE8E0CDL, 36) else withAlpha(0xFF1A1815L, 77)
            radiusS = 1
            radiusM = 2
            radiusCard = tune.radius
            radiusXL = 4
            danger = 0xFFA83226L
            ok = 0xFF4A6B52L
            warn = 0xFFB07A2AL
            aurora2 = accent
            aurora3 = accent
            ambient = 77
            ink = if (dark) 0xFFE8E0CDL else 0xFF1A1815L
            serif = true
            stamp = "落笔"
        }
        "v3c" -> {
            card = frame
            glass = withAlpha(frame, 235)
            text = if (dark) mix(accent, 0xFFFFFFFFL, 0.72f) else 0xFF152018L
            radiusS = 1
            radiusM = 2
            radiusCard = tune.radius
            radiusXL = 4
            danger = 0xFFFF5F56L
            ok = accent
            warn = 0xFFFFB84DL
            line = withAlpha(text, 41)
            line2 = withAlpha(text, 66)
            text2 = mix(text, bg, 0.32f)
            text3 = mix(text, bg, 0.55f)
            aurora2 = accent
            aurora3 = accent
            ambient = 77
            ink = if (dark) 0xFF100F0CL else 0xFF211F1AL
            panel = mix(bg, accent, if (dark) 0.05f else 0.02f)
            mono = true
            outline = 1
        }
    }

    val dim = if (style == "v3c") mix(text, bg, 0.45f) else text3

    return NibTokens(
        dark = dark,
        bg = bg,
        card = card,
        card2 = card2,
        card3 = card3,
        glass = glass,
        line = line,
        line2 = line2,
        text = text,
        text2 = text2,
        text3 = text3,
        accent = accent,
        accent2 = accent2,
        accentWeak = withAlpha(accent, if (dark) 41 else 26),
        danger = danger,
        dangerWeak = withAlpha(danger, if (dark) 33 else 26),
        ok = ok,
        warn = warn,
        paper = paper,
        ink = ink,
        dim = dim,
        panel = panel,
        aurora2 = aurora2,
        aurora3 = aurora3,
        ambient = ambient,
        radiusS = radiusS,
        radiusM = radiusM,
        radiusCard = radiusCard,
        radiusXL = radiusXL,
        radiusChip = radiusM,
        radiusSheet = radiusXL,
        glow = tune.glow,
        grain = tune.grain,
        tilt = tune.tilt,
        density = tune.density,
        styleId = style,
        colorKey = p.key,
        mono = mono,
        serif = serif,
        outline = outline,
        cardBorder = if (outline == 1) withAlpha(accent, 46) else line,
        stamp = stamp,
    )
}
