package com.yourname.nib.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourname.nib.core.theme.LocalNibTokens
import com.yourname.nib.core.theme.nibFontFamily
import com.yourname.nib.core.theme.tokenColor
import com.yourname.nib.core.theme.withAlpha

@Composable
fun NibCard(
    modifier: Modifier = Modifier,
    padding: Int = 0,
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = LocalNibTokens.current
    val shape = RoundedCornerShape(t.radiusCard.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(tokenColor(t.card))
            .border(if (t.outline == 1) 1.dp else 0.6.dp, tokenColor(t.cardBorder), shape)
            .padding(vertical = padding.dp),
        verticalArrangement = Arrangement.Top,
        content = content,
    )
}

@Composable
fun NibChip(
    text: String,
    selected: Boolean = false,
    onClick: () -> Unit = {},
) {
    val t = LocalNibTokens.current
    val bg = if (selected) tokenColor(t.accentWeak) else tokenColor(t.card2)
    val fg = if (selected) tokenColor(t.accent) else tokenColor(t.text2)
    val shape = RoundedCornerShape(t.radiusM.coerceAtMost(7).dp)
    Text(
        text = text,
        color = fg,
        fontSize = 11.5.sp,
        fontFamily = nibFontFamily(),
        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        modifier = Modifier
            .clip(shape)
            .background(bg)
            .border(if (selected) 1.dp else 0.dp, tokenColor(withAlpha(t.accent, if (selected) 120 else 0)), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 4.dp),
    )
}

@Composable
private fun RuleLine(color: Color, dashed: Boolean, thickness: Float, dash: Float) {
    Canvas(Modifier.fillMaxWidth().height(thickness.dp)) {
        val y = size.height / 2f
        if (dashed) {
            val step = dash.dp.toPx()
            var x = 0f
            while (x < size.width) {
                drawLine(color, Offset(x, y), Offset((x + step).coerceAtMost(size.width), y), strokeWidth = thickness.dp.toPx())
                x += step * 1.8f
            }
        } else {
            drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = thickness.dp.toPx())
        }
    }
}

@Composable
fun SectionTitle(text: String, sub: String? = null) {
    val t = LocalNibTokens.current
    when (t.styleId) {
        "v3a" -> Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, top = 20.dp, bottom = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(6.dp, 8.dp)) {
                    val path = Path().apply {
                        moveTo(0f, 0f)
                        lineTo(size.width, size.height / 2f)
                        lineTo(0f, size.height)
                        close()
                    }
                    drawPath(path, tokenColor(t.accent))
                }
                Spacer(Modifier.width(7.dp))
                Text(
                    text = text,
                    color = tokenColor(t.text3),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 2.4.sp,
                    fontFamily = nibFontFamily(),
                    modifier = Modifier.weight(1f),
                )
                if (sub != null) {
                    Text(
                        text = sub,
                        color = tokenColor(t.text3),
                        fontSize = 9.sp,
                        letterSpacing = 1.sp,
                        fontFamily = nibFontFamily(),
                    )
                }
            }
            Spacer(Modifier.height(7.dp))
            RuleLine(tokenColor(t.line), dashed = true, thickness = 1f, dash = 3f)
        }
        "v3b" -> Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 22.dp, bottom = 2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .rotate(45f)
                        .background(tokenColor(t.accent)),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = text,
                    color = tokenColor(t.text),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 4.sp,
                    fontFamily = nibFontFamily(),
                    modifier = Modifier.weight(1f),
                )
                if (sub != null) {
                    Text(
                        text = sub,
                        color = tokenColor(t.text3),
                        fontSize = 10.sp,
                        letterSpacing = 1.sp,
                        fontFamily = nibFontFamily(),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            RuleLine(tokenColor(t.text), dashed = false, thickness = 2f, dash = 0f)
        }
        "v3c" -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("[ ", color = tokenColor(t.text3), fontSize = 10.sp, letterSpacing = 1.5.sp, fontFamily = nibFontFamily())
            Text(text, color = tokenColor(t.accent), fontSize = 10.sp, letterSpacing = 1.5.sp, fontFamily = nibFontFamily())
            Text(" ]", color = tokenColor(t.text3), fontSize = 10.sp, letterSpacing = 1.5.sp, fontFamily = nibFontFamily())
            Text(
                text = " ─────────────────────────",
                color = tokenColor(t.line2),
                fontSize = 10.sp,
                letterSpacing = (-1).sp,
                maxLines = 1,
                fontFamily = nibFontFamily(),
                modifier = Modifier.weight(1f, fill = false),
            )
            if (sub != null) {
                Text(
                    text = " $sub",
                    color = tokenColor(t.text3),
                    fontSize = 9.sp,
                    fontFamily = nibFontFamily(),
                )
            }
        }
        else -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, top = 20.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(5.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(tokenColor(t.text3)),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = text,
                color = tokenColor(t.text3),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.3.sp,
                fontFamily = nibFontFamily(),
            )
            if (sub != null) {
                Text(
                    text = sub,
                    color = tokenColor(t.text3),
                    fontSize = 11.sp,
                    fontFamily = nibFontFamily(),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
fun EmptyHint(text: String) {
    val t = LocalNibTokens.current
    if (t.styleId == "v3c") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("> ", color = tokenColor(t.accent), fontSize = 11.sp, letterSpacing = 1.sp, fontFamily = nibFontFamily())
            Text(text, color = tokenColor(t.text3), fontSize = 11.sp, letterSpacing = 1.sp, fontFamily = nibFontFamily())
        }
        return
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 30.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = tokenColor(t.text3), fontSize = 13.sp, fontFamily = nibFontFamily())
    }
}

@Composable
private fun HeroRule(caption: String) {
    val t = LocalNibTokens.current
    val double = t.styleId == "v3b"
    val dashed = t.styleId == "v3c"
    Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Canvas(Modifier.fillMaxWidth().height(if (double) 4.dp else 1.dp)) {
            val color = if (double) tokenColor(t.text) else tokenColor(t.line)
            if (double) {
                val thickness = size.height * 0.3f
                drawLine(color, Offset(0f, thickness / 2f), Offset(size.width, thickness / 2f), strokeWidth = thickness)
                drawLine(color, Offset(0f, size.height - thickness / 2f), Offset(size.width, size.height - thickness / 2f), strokeWidth = thickness)
            } else if (dashed) {
                val dash = 4.dp.toPx()
                var x = 0f
                while (x < size.width) {
                    drawLine(color, Offset(x, 0.5f), Offset((x + dash).coerceAtMost(size.width), 0.5f), strokeWidth = 1f)
                    x += dash * 2f
                }
            } else {
                drawLine(color, Offset(0f, 0.5f), Offset(size.width, 0.5f), strokeWidth = 1f)
            }
        }
        if (caption.isNotEmpty()) {
            Text(
                text = caption,
                color = tokenColor(t.text3),
                fontSize = if (double) 9.sp else 7.5.sp,
                letterSpacing = if (double) 2.sp else 1.1.sp,
                fontFamily = nibFontFamily(),
                modifier = Modifier.align(Alignment.End).padding(top = 5.dp),
            )
        }
    }
}

@Composable
fun NibHero(
    sub: String?,
    title: String,
    actions: (@Composable RowScope.() -> Unit)? = null,
    below: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val t = LocalNibTokens.current
    val decorated = t.styleId == "v3a" || t.styleId == "v3b" || t.styleId == "v3c"
    val caption = when (t.styleId) {
        "v3a" -> "NIB / CONTACT SHEET   400 ISO   f2.8"
        "v3b" -> "第 一 版 · 本 埠 琐 记"
        "v3c" -> "SESSION 01  ·  LOCAL DB"
        else -> ""
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = if (decorated) 2.dp else 10.dp),
    ) {
        if (!sub.isNullOrBlank()) {
            Text(
                text = if (t.styleId == "v3c") "// $sub" else sub,
                color = tokenColor(t.accent),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp,
                fontFamily = nibFontFamily(),
            )
            Spacer(Modifier.height(5.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (t.styleId == "v3c") "${title}_" else title,
                color = tokenColor(t.text),
                fontSize = 31.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1.2).sp,
                fontFamily = nibFontFamily(),
                modifier = Modifier.weight(1f),
            )
            if (actions != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions,
                )
            }
        }
        if (below != null) {
            Spacer(Modifier.height(10.dp))
            below()
        }
        if (decorated) HeroRule(caption) else Spacer(Modifier.height(2.dp))
    }
}

@Composable
fun NibIconButton(icon: ImageVector, onClick: () -> Unit) {
    val t = LocalNibTokens.current
    val shape = RoundedCornerShape(t.radiusM.dp)
    Box(
        modifier = Modifier
            .size(31.dp)
            .clip(shape)
            .background(tokenColor(t.card2))
            .border(0.6.dp, tokenColor(t.line), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tokenColor(t.text2), modifier = Modifier.size(15.dp))
    }
}

@Composable
fun NibActionPill(label: String, color: Long, onClick: () -> Unit) {
    val t = LocalNibTokens.current
    val shape = RoundedCornerShape(t.radiusM.dp)
    Text(
        text = label,
        color = tokenColor(color),
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = nibFontFamily(),
        modifier = Modifier
            .clip(shape)
            .background(tokenColor(withAlpha(color, if (t.dark) 26 else 20)))
            .border(0.6.dp, tokenColor(withAlpha(color, if (t.dark) 82 else 70)), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp),
    )
}

@Composable
fun NibIconBadge(icon: ImageVector, color: Color, size: Int = 27) {
    val t = LocalNibTokens.current
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size * 0.33f).toInt().dp))
            .background(color.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size((size * 0.55f).dp))
    }
}

@Composable
fun NibRowDivider() {
    val t = LocalNibTokens.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(0.7.dp)
            .background(tokenColor(t.line)),
    )
}
