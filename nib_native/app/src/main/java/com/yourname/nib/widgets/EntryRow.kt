package com.yourname.nib.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourname.nib.core.db.Entry
import com.yourname.nib.core.model.EntryBundle
import com.yourname.nib.core.time.stampKey
import com.yourname.nib.core.theme.LocalNibTokens
import com.yourname.nib.core.theme.NibTokens
import com.yourname.nib.core.theme.mix
import com.yourname.nib.core.theme.withAlpha
import com.yourname.nib.core.theme.tokenColor

private fun metaDate(e: Entry): String =
    if (e.dueDate == null) "未排期 " + stampKey(e.createdAt)
    else e.dueDate + (e.dueTime?.let { tm -> " $tm" } ?: "")

private fun priorityColor(t: NibTokens, priority: Int): Color? = when (priority) {
    1 -> tokenColor(t.text3)
    2 -> tokenColor(t.warn)
    3 -> tokenColor(t.danger)
    else -> null
}

@Composable
private fun CheckBox(done: Boolean, selected: Boolean, onClick: () -> Unit) {
    val t = LocalNibTokens.current
    val on = done || selected
    when (t.styleId) {
        "v3a" -> Box(
            modifier = Modifier
                .size(17.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(if (on) tokenColor(0xFF29271FL) else Color.Transparent)
                .border(1.5.dp, if (on) tokenColor(0xFF29271FL) else tokenColor(0xFF8C8573L), RoundedCornerShape(1.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (on) Icon(Icons.Default.Check, contentDescription = null, tint = tokenColor(0xFFE9E2D1L), modifier = Modifier.size(10.dp))
        }
        "v3b" -> Box(
            modifier = Modifier
                .size(16.dp)
                .background(if (on) tokenColor(t.accent) else Color.Transparent)
                .border(1.5.dp, if (on) tokenColor(t.accent) else tokenColor(t.text3), RoundedCornerShape(0.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (on) Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(10.dp))
        }
        "v3c" -> Box(
            modifier = Modifier
                .size(22.dp)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = if (on) "[x]" else "[ ]",
                color = tokenColor(t.accent),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = (-1).sp,
            )
        }
        else -> Box(
            modifier = Modifier
                .size(21.dp)
                .clip(CircleShape)
                .background(if (on) tokenColor(t.accent) else Color.Transparent)
                .border(1.6.dp, if (on) tokenColor(t.accent) else tokenColor(t.line2), CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (on) Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
        }
    }
}

@Composable
private fun EntryMeta(bundle: EntryBundle, tint: Long, tagBg: Boolean) {
    val t = LocalNibTokens.current
    val e = bundle.entry
    val family = if (t.mono) FontFamily.Monospace else if (t.serif) FontFamily.Serif else null
    val parts = buildList {
        if (e.pinned == 1) add("置顶")
        add(metaDate(e))
        bundle.listName?.let { add(it) }
    }
    if (parts.isEmpty() && bundle.tagNames.isEmpty()) return
    Row(
        modifier = Modifier.padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (parts.isNotEmpty()) {
            Text(
                text = parts.joinToString(" · "),
                color = tokenColor(tint),
                fontSize = 11.5.sp,
                fontFamily = family,
                maxLines = 1,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        bundle.tagNames.take(3).forEach { name ->
            if (parts.isNotEmpty()) Spacer(Modifier.width(6.dp))
            Text(
                text = "#$name",
                color = tokenColor(t.accent),
                fontSize = 10.5.sp,
                fontWeight = if (tagBg) FontWeight.Medium else FontWeight.Normal,
                fontFamily = family,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(t.radiusS.dp))
                    .background(if (tagBg) tokenColor(withAlpha(t.accent, 26)) else Color.Transparent)
                    .padding(horizontal = if (tagBg) 7.dp else 0.dp, vertical = if (tagBg) 2.dp else 0.dp),
            )
        }
    }
}

@Composable
private fun BadgeRow(
    bundle: EntryBundle,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier,
    selected: Boolean,
    onLongClick: () -> Unit,
    trailing: (@Composable () -> Unit)?,
    contained: Boolean,
) {
    val t = LocalNibTokens.current
    val e = bundle.entry
    val done = e.done == 1
    val shape = RoundedCornerShape(if (contained) 0.dp else t.radiusCard.dp)
    val family = if (t.mono) FontFamily.Monospace else null
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                when {
                    selected -> tokenColor(t.accentWeak)
                    contained -> Color.Transparent
                    else -> tokenColor(t.card)
                },
            )
            .border(
                if (contained || t.outline == 0) 0.dp else 1.dp,
                tokenColor(t.cardBorder),
                shape,
            )
            .combinedClickable(onClick = onOpen, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CheckBox(done, selected, onToggle)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = e.title,
                    color = if (done) tokenColor(t.text3) else tokenColor(t.text),
                    fontSize = 14.5.sp,
                    fontFamily = family,
                    fontWeight = if (t.mono) FontWeight.Medium else FontWeight.Normal,
                    letterSpacing = (-0.1).sp,
                    lineHeight = 21.sp,
                    maxLines = 2,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                )
                EntryMeta(bundle, if (done) withAlpha(t.text3, 170) else t.text3, tagBg = true)
            }
            trailing?.invoke()
        }
    }
}

@Composable
private fun FilmRow(
    bundle: EntryBundle,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier,
    selected: Boolean,
    onLongClick: () -> Unit,
    trailing: (@Composable () -> Unit)?,
    index: Int,
) {
    val t = LocalNibTokens.current
    val e = bundle.entry
    val done = e.done == 1
    val paperColor = mix(t.accent, 0xFFFFFFFFL, if (t.dark) 0.84f else 0.9f)
    val inkColor = tokenColor(if (t.dark) 0xFF211F1AL else 0xFF17150FL)
    val shape = RoundedCornerShape(1.dp)
    val tilt = t.tilt / 10f
    val rotation = when (index % 3) {
        0 -> -tilt
        1 -> tilt * 0.6f
        else -> tilt * 1.2f
    }
    val slide = when (index % 3) {
        1 -> 3.dp
        2 -> (-2).dp
        else -> 0.dp
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp)
            .rotate(rotation)
            .offset(x = slide)
            .shadow(9.dp, shape)
            .clip(shape)
            .background(if (selected) tokenColor(withAlpha(t.accent, 60)) else tokenColor(paperColor))
            .border(1.dp, tokenColor(withAlpha(0xFF000000L, 140)), shape)
            .drawBehind {
                val left = 4.dp.toPx()
                val top = 9.dp.toPx()
                val bottom = size.height - 32.dp.toPx()
                val pitch = 9.dp.toPx()
                val hole = 4.dp.toPx()
                var y = top
                while (y < bottom) {
                    drawRect(
                        color = tokenColor(0xFF2A2822L).copy(alpha = 0.5f),
                        topLeft = Offset(left, y),
                        size = Size(5.dp.toPx(), hole),
                    )
                    y += pitch
                }
                val gap = 13.dp.toPx()
                val lineY = size.height - 24.dp.toPx()
                drawLine(
                    color = tokenColor(0xFF000000L).copy(alpha = 0.15f),
                    start = Offset(gap, lineY),
                    end = Offset(size.width - gap, lineY),
                    strokeWidth = 1f,
                )
            }
            .combinedClickable(onClick = onOpen, onLongClick = onLongClick)
            .padding(start = 16.dp, end = 13.dp, top = 15.dp, bottom = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CheckBox(done, selected, onToggle)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = e.title,
                    color = if (done) inkColor.copy(alpha = 0.5f) else inkColor,
                    fontSize = 13.5.sp,
                    fontFamily = FontFamily.Serif,
                    lineHeight = 20.sp,
                    maxLines = 2,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                )
                Row(modifier = Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    val meta = buildList {
                        if (e.pinned == 1) add("置顶")
                        add(metaDate(e))
                        bundle.listName?.let { add(it) }
                    }
                    if (meta.isNotEmpty()) {
                        Text(
                            text = meta.joinToString(" · "),
                            color = inkColor.copy(alpha = 0.55f),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Serif,
                            maxLines = 1,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    bundle.tagNames.take(2).forEach { name ->
                        Spacer(Modifier.width(6.dp))
                        Text("#$name", color = inkColor.copy(alpha = 0.6f), fontSize = 9.5.sp, fontFamily = FontFamily.Serif)
                    }
                }
            }
            trailing?.invoke()
        }
        Spacer(Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(
                text = t.stamp ?: "NIB 400",
                color = inkColor.copy(alpha = 0.42f),
                fontSize = 8.sp,
                letterSpacing = 1.4.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "FRAME " + (index + 1).toString().padStart(2, '0'),
                color = inkColor.copy(alpha = 0.38f),
                fontSize = 8.sp,
                letterSpacing = 1.4.sp,
            )
        }
    }
}

@Composable
private fun ClipRow(
    bundle: EntryBundle,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier,
    selected: Boolean,
    onLongClick: () -> Unit,
    trailing: (@Composable () -> Unit)?,
    index: Int,
) {
    val t = LocalNibTokens.current
    val e = bundle.entry
    val done = e.done == 1
    val paperColor = if (t.dark) mix(t.bg, t.accent, 0.10f) else mix(t.accent, 0xFFFFFFFFL, 0.90f)
    val inkColor = tokenColor(t.ink)
    val foldColor = tokenColor(mix(paperColor, if (t.dark) 0xFF000000L else 0xFF8A7A58L, 0.16f))
    val castColor = tokenColor(withAlpha(0xFF5A4B32L, if (t.dark) 90 else 42))
    val tilt = t.tilt / 10f
    val rotation = when (index % 3) {
        0 -> -tilt
        1 -> tilt * 0.5f
        else -> tilt
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp)
            .rotate(rotation)
            .offset(y = if (index % 3 == 1) 2.dp else 0.dp)
            .drawBehind {
                drawRect(
                    color = castColor,
                    topLeft = Offset(2.dp.toPx(), 3.dp.toPx()),
                    size = size,
                )
                val leg = 20.dp.toPx()
                val fold = Path().apply {
                    moveTo(size.width - leg, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width, leg)
                    close()
                }
                drawPath(fold, foldColor)
            }
            .heightIn(min = 112.dp)
            .background(if (selected) tokenColor(withAlpha(t.accent, 46)) else tokenColor(paperColor))
            .border(1.dp, tokenColor(t.line2), RoundedCornerShape(0.dp))
            .combinedClickable(onClick = onOpen, onLongClick = onLongClick)
            .padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CheckBox(done, selected, onToggle)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = e.title,
                    color = if (done) inkColor.copy(alpha = 0.45f) else inkColor,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.3.sp,
                    lineHeight = 22.sp,
                    maxLines = 3,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                )
                Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val meta = buildList {
                        if (e.pinned == 1) add("置顶")
                        add(metaDate(e))
                        bundle.listName?.let { add(it) }
                    }
                    if (meta.isNotEmpty()) {
                        Text(
                            text = meta.joinToString(" · "),
                            color = inkColor.copy(alpha = 0.52f),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Serif,
                            maxLines = 1,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    bundle.tagNames.take(2).forEach { name ->
                        Spacer(Modifier.width(6.dp))
                        Text("#$name", color = inkColor.copy(alpha = 0.6f), fontSize = 9.5.sp, fontFamily = FontFamily.Serif)
                    }
                }
            }
            trailing?.invoke()
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Box(Modifier.weight(1f))
            Text(
                text = "第 ${(index + 1).toString().padStart(2, '0')} 则",
                color = inkColor.copy(alpha = 0.38f),
                fontSize = 8.5.sp,
                fontFamily = FontFamily.Serif,
                letterSpacing = 1.5.sp,
            )
        }
    }
}

@Composable
private fun LogRow(
    bundle: EntryBundle,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier,
    selected: Boolean,
    onLongClick: () -> Unit,
    trailing: (@Composable () -> Unit)?,
    contained: Boolean,
) {
    val t = LocalNibTokens.current
    val e = bundle.entry
    val done = e.done == 1
    val family = FontFamily.Monospace
    val ts = e.dueTime ?: e.dueDate ?: stampKey(e.createdAt)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (selected) tokenColor(t.accentWeak) else Color.Transparent)
            .combinedClickable(onClick = onOpen, onLongClick = onLongClick)
            .padding(start = 12.dp, end = 10.dp, top = 9.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(">", color = tokenColor(t.accent), fontSize = 13.sp, fontFamily = family, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(7.dp))
        CheckBox(done, selected, onToggle)
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = e.title,
                color = if (done) tokenColor(t.dim) else tokenColor(t.text),
                fontSize = 13.sp,
                fontFamily = family,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                textDecoration = if (done) TextDecoration.LineThrough else null,
            )
            val meta = buildList {
                add(metaDate(e))
                bundle.listName?.let { add(it) }
                bundle.tagNames.take(2).forEach { add("#$it") }
            }
            if (meta.isNotEmpty()) {
                Text(
                    text = meta.joinToString(" "),
                    color = tokenColor(t.dim),
                    fontSize = 10.sp,
                    fontFamily = family,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        trailing?.invoke()
        Spacer(Modifier.width(8.dp))
        Text(ts, color = tokenColor(t.dim), fontSize = 10.sp, fontFamily = family)
    }
}

@Composable
fun EntryRow(
    bundle: EntryBundle,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onLongClick: () -> Unit = {},
    trailing: (@Composable () -> Unit)? = null,
    contained: Boolean = false,
    index: Int = 0,
) {
    val t = LocalNibTokens.current
    val priority = bundle.entry.priority
    val bar = priorityColor(t, priority)
    val decorated = modifier.drawBehind {
        when (t.styleId) {
            "v3c" -> {
                val color = bar ?: tokenColor(t.line2)
                drawRect(color = color, size = Size(2.dp.toPx(), size.height))
            }
            "v3b" -> {
                val color = bar ?: tokenColor(t.accent)
                val width = if (priority >= 3) 4.dp.toPx() else 3.dp.toPx()
                val alpha = if (priority == 0) 0.6f else 1f
                drawRect(color = color.copy(alpha = color.alpha * alpha), size = Size(width, size.height))
            }
            "v3a" -> if (bar != null) drawRect(color = bar, size = Size(3.dp.toPx(), size.height))
            else -> if (bar != null) {
                val inset = 16.dp.toPx()
                drawRoundRect(
                    color = bar,
                    topLeft = Offset(0f, inset),
                    size = Size(2.5.dp.toPx(), (size.height - inset * 2).coerceAtLeast(0f)),
                    cornerRadius = CornerRadius(0f, 2.dp.toPx()),
                )
            }
        }
    }
    EntryRowBody(bundle, onToggle, onOpen, decorated, selected, onLongClick, trailing, contained, index)
}

@Composable
private fun EntryRowBody(
    bundle: EntryBundle,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier,
    selected: Boolean,
    onLongClick: () -> Unit,
    trailing: (@Composable () -> Unit)?,
    contained: Boolean,
    index: Int,
) {
    val t = LocalNibTokens.current
    when (t.styleId) {
        "v3a" -> FilmRow(bundle, onToggle, onOpen, modifier, selected, onLongClick, trailing, index)
        "v3b" -> ClipRow(bundle, onToggle, onOpen, modifier, selected, onLongClick, trailing, index)
        "v3c" -> LogRow(bundle, onToggle, onOpen, modifier, selected, onLongClick, trailing, contained)
        else -> BadgeRow(bundle, onToggle, onOpen, modifier, selected, onLongClick, trailing, contained)
    }
}
