package com.yourname.nib.core.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

val LocalNibTokens = staticCompositionLocalOf { resolveTokens(ThemeConfig()) }

fun tokenColor(value: Long): Color = Color(
    red = ((value shr 16) and 0xFF).toInt() / 255f,
    green = ((value shr 8) and 0xFF).toInt() / 255f,
    blue = (value and 0xFF).toInt() / 255f,
    alpha = ((value shr 24) and 0xFF).toInt() / 255f,
)

@Composable
fun nibFontFamily(): FontFamily? {
    val t = LocalNibTokens.current
    return when {
        t.mono -> FontFamily.Monospace
        t.serif -> FontFamily.Serif
        else -> null
    }
}

@Composable
fun NibTheme(config: ThemeConfig = ThemeConfig(), content: @Composable () -> Unit) {
    val t = resolveTokens(config)
    val scheme = if (t.dark) {
        darkColorScheme(
            primary = tokenColor(t.accent),
            onPrimary = Color.White,
            primaryContainer = tokenColor(t.accentWeak),
            secondary = tokenColor(t.accent2),
            background = tokenColor(t.bg),
            onBackground = tokenColor(t.text),
            surface = tokenColor(t.card),
            onSurface = tokenColor(t.text),
            surfaceVariant = tokenColor(t.card2),
            onSurfaceVariant = tokenColor(t.text2),
            outline = tokenColor(t.line2),
            error = tokenColor(t.danger),
        )
    } else {
        lightColorScheme(
            primary = tokenColor(t.accent),
            onPrimary = Color.White,
            primaryContainer = tokenColor(t.accentWeak),
            secondary = tokenColor(t.accent2),
            background = tokenColor(t.bg),
            onBackground = tokenColor(t.text),
            surface = tokenColor(t.card),
            onSurface = tokenColor(t.text),
            surfaceVariant = tokenColor(t.card2),
            onSurfaceVariant = tokenColor(t.text2),
            outline = tokenColor(t.line2),
            error = tokenColor(t.danger),
        )
    }
    CompositionLocalProvider(LocalNibTokens provides t) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

private fun DrawScope.grain(seed: Int, color: Color, count: Int, radius: Float, alpha: Float) {
    val rnd = Random(seed)
    repeat(count) {
        drawCircle(
            color = color.copy(alpha = alpha * rnd.nextFloat()),
            radius = radius,
            center = Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height),
        )
    }
}

private fun DrawScope.vignette(color: Color, strength: Float) {
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(Color.Transparent, color),
            center = Offset(size.width * 0.5f, size.height * 0.42f),
            radius = size.height * 0.78f,
        ),
        alpha = strength,
    )
}

@Composable
fun NibBackdrop() {
    val t = LocalNibTokens.current
    val accent = tokenColor(t.accent)
    when (t.styleId) {
        "v1" -> Canvas(Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(tokenColor(withAlpha(t.accent, (t.glow * 0.26f).toInt())), Color.Transparent),
                    startY = 0f,
                    endY = size.height * 0.4f,
                ),
            )
        }
        "v2" -> Canvas(Modifier.fillMaxSize()) {
            val a1 = tokenColor(t.accent)
            val a2 = tokenColor(t.aurora2)
            val a3 = tokenColor(t.aurora3)
            val amb = t.ambient / 255f
            val lit = amb * (t.glow / 62f)
            val c1 = Offset(size.width * 0.08f, size.height * -0.02f)
            val c2 = Offset(size.width * 0.96f, size.height * 0.30f)
            val c3 = Offset(size.width * 0.32f, size.height * 0.92f)
            drawCircle(
                brush = Brush.radialGradient(listOf(a1.copy(alpha = 0.55f * lit), Color.Transparent), center = c1, radius = size.width * 1.05f),
                radius = size.width * 1.05f,
                center = c1,
            )
            drawCircle(
                brush = Brush.radialGradient(listOf(a2.copy(alpha = 0.38f * lit), Color.Transparent), center = c2, radius = size.width * 0.9f),
                radius = size.width * 0.9f,
                center = c2,
            )
            drawCircle(
                brush = Brush.radialGradient(listOf(a3.copy(alpha = 0.26f * lit), Color.Transparent), center = c3, radius = size.width * 0.85f),
                radius = size.width * 0.85f,
                center = c3,
            )
        }
        "v3a" -> Canvas(Modifier.fillMaxSize()) {
            val lamp = tokenColor(t.accent)
            val lit = (t.glow / 100f * 0.8f).coerceIn(0f, 0.9f)
            val lc = Offset(size.width * 0.88f, size.height * -0.10f)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(lamp.copy(alpha = lit), Color.Transparent),
                    center = lc,
                    radius = size.width * 1.1f,
                ),
                radius = size.width * 1.1f,
                center = lc,
            )
            val grit = (t.grain / 9f).coerceIn(0f, 3.4f)
            if (grit > 0f) {
                grain(seed = 7, color = tokenColor(0xFFFFFFFFL), count = (1200 * grit).toInt(), radius = 0.7f, alpha = 0.11f)
            }
            vignette(tokenColor(0xFF000000L), 0.55f)
        }
        "v3b" -> Canvas(Modifier.fillMaxSize()) {
            val ink = tokenColor(t.ink)
            val fiber = 0.05f * (t.grain / 6f).coerceIn(0f, 3f) * if (t.dark) 0.4f else 1f
            val step = 4.dp.toPx()
            var y = 0f
            while (y < size.height) {
                drawLine(
                    color = ink.copy(alpha = fiber),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f,
                )
                y += step
            }
            drawLine(
                color = ink.copy(alpha = (0.14f * (t.glow / 30f)).coerceIn(0f, 0.4f)),
                start = Offset(24.dp.toPx(), 0f),
                end = Offset(24.dp.toPx(), size.height),
                strokeWidth = 1f,
            )
            vignette(ink, 0.16f)
        }
        "v3c" -> Canvas(Modifier.fillMaxSize()) {
            val scanAlpha = 0.28f * (t.grain / 100f)
            var y = 0f
            val step = 3.dp.toPx()
            while (y < size.height) {
                drawLine(
                    color = tokenColor(0xFF000000L).copy(alpha = scanAlpha),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f,
                )
                y += step
            }
            val band = tokenColor(t.accent)
            val top = size.height * 0.14f
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color.Transparent, band.copy(alpha = 0.14f), Color.Transparent),
                    startY = top,
                    endY = top + 70.dp.toPx(),
                ),
                topLeft = Offset(0f, top),
                size = Size(size.width, 70.dp.toPx()),
            )
            vignette(tokenColor(0xFF000000L), 0.55f)
        }
        else -> Unit
    }
}

@Composable
fun NibBackdropStamp() {
    val t = LocalNibTokens.current
    val label = t.stamp ?: return
    Canvas(Modifier.fillMaxSize()) {
        val accent = tokenColor(t.accent)
        val radius = 26.dp.toPx()
        val cx = size.width - 14.dp.toPx() - radius
        val top = 56.dp.toPx()
        val cy = top + radius
        rotate(degrees = -14f, pivot = Offset(cx, cy)) {
            drawCircle(
                color = accent.copy(alpha = 0.16f),
                radius = radius,
                center = Offset(cx, cy),
                style = Stroke(width = 2.dp.toPx()),
            )
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                textSize = 11.sp.toPx()
                color = accent.copy(alpha = 0.5f).toArgb()
                textAlign = android.graphics.Paint.Align.CENTER
            }
            val step = 13.dp.toPx()
            val startY = cy - step * (label.length - 1) / 2f + 4.dp.toPx()
            label.forEachIndexed { index, ch ->
                drawContext.canvas.nativeCanvas.drawText(ch.toString(), cx, startY + step * index, paint)
            }
        }
    }
}

@Composable
fun NibBeam() {
    val t = LocalNibTokens.current
    if (t.styleId != "v2") return
    Canvas(Modifier.fillMaxSize()) {
        val a = tokenColor(t.accent)
        val lit = (t.glow / 62f).coerceIn(0f, 1.6f)
        val rnd = Random(3)
        repeat(4) {
            val angle = (rnd.nextFloat() * 60f - 30f) * (Math.PI / 180f)
            val origin = Offset(size.width * (0.1f + rnd.nextFloat() * 0.8f), size.height * (0.1f + rnd.nextFloat() * 0.5f))
            val len = size.height * (0.5f + rnd.nextFloat() * 0.6f)
            drawLine(
                brush = Brush.linearGradient(
                    listOf(Color.Transparent, a.copy(alpha = 0.07f * lit), Color.Transparent),
                    start = origin,
                    end = origin + Offset(cos(angle).toFloat() * len, sin(angle).toFloat() * len),
                ),
                start = origin,
                end = origin + Offset(cos(angle).toFloat() * len, sin(angle).toFloat() * len),
                strokeWidth = 22.dp.toPx(),
            )
        }
    }
}
