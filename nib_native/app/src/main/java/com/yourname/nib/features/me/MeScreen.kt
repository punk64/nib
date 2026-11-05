package com.yourname.nib.features.me

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourname.nib.BuildConfig
import com.yourname.nib.NibViewModel
import com.yourname.nib.core.update.ApkInstall
import com.yourname.nib.core.update.DownloadPhase
import com.yourname.nib.core.update.PROJECT_URL
import com.yourname.nib.core.update.UpdateCenter
import com.yourname.nib.core.update.UpdateChecker
import com.yourname.nib.core.update.UpdateInfo
import com.yourname.nib.core.update.formatSize
import com.yourname.nib.core.update.isNewerVersion
import com.yourname.nib.core.CrashCatcher
import com.yourname.nib.core.model.splitByDone
import com.yourname.nib.core.theme.LocalNibTokens
import com.yourname.nib.core.theme.NibTokens
import com.yourname.nib.core.theme.ThemeConfig
import com.yourname.nib.core.theme.TuneField
import com.yourname.nib.core.theme.nibFontFamily
import com.yourname.nib.core.theme.palettes
import com.yourname.nib.core.theme.themeStyles
import com.yourname.nib.core.theme.tokenColor
import com.yourname.nib.core.theme.tuneFields
import com.yourname.nib.core.theme.withAlpha
import com.yourname.nib.core.time.NibDate
import com.yourname.nib.widgets.NibCard
import com.yourname.nib.widgets.NibHero
import com.yourname.nib.widgets.NibIconBadge
import com.yourname.nib.widgets.NibRowDivider
import com.yourname.nib.widgets.SectionTitle
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
private fun Padded(content: @Composable () -> Unit) {
    Box(Modifier.padding(horizontal = 18.dp)) { content() }
}

private fun hexColor(hex: String, fallback: Color): Color {
    val s = hex.trim().removePrefix("#")
    if (s.length < 6) return fallback
    val v = s.take(6).toLongOrNull(16) ?: return fallback
    return Color((0xFF000000L or v).toInt())
}

@Composable
private fun StatCard(value: String, label: String) {
    val t = LocalNibTokens.current
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(t.radiusCard.dp))
            .background(tokenColor(t.card))
            .border(0.6.dp, tokenColor(t.line), RoundedCornerShape(t.radiusCard.dp))
            .padding(horizontal = 13.dp, vertical = 14.dp),
    ) {
        Text(
            text = value,
            color = tokenColor(t.text),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.8).sp,
            fontFamily = nibFontFamily(),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            color = tokenColor(t.text3),
            fontSize = 10.5.sp,
            fontFamily = nibFontFamily(),
        )
    }
}

@Composable
private fun StatRow(undone: Int, doneWeek: Int, total: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        StatCard(undone.toString(), "待办")
        StatCard(doneWeek.toString(), "本周完成")
        StatCard(total.toString(), "条目总数")
    }
}

@Composable
private fun PanelLine(label: String, onClick: () -> Unit, trailing: @Composable () -> Unit) {
    val t = LocalNibTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = tokenColor(t.text),
            fontSize = 13.sp,
            fontFamily = nibFontFamily(),
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

@Composable
private fun StyleThumb(styleId: String, size: Int = 22) {
    val style = themeStyles.first { it.id == styleId }
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(tokenColor(style.thumbA), tokenColor(style.thumbB))))
            .border(0.8.dp, tokenColor(withAlpha(0xFFFFFFFFL, 46)), CircleShape),
    )
}

@Composable
private fun StylePill(styleId: String, name: String, active: Boolean, onClick: () -> Unit) {
    val t = LocalNibTokens.current
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(if (active) tokenColor(t.accentWeak) else tokenColor(t.card))
            .border(if (active) 1.5.dp else 0.8.dp, if (active) tokenColor(t.accent) else tokenColor(t.line), shape)
            .clickable(onClick = onClick)
            .padding(start = 5.dp, end = 9.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StyleThumb(styleId)
        Spacer(Modifier.width(6.dp))
        Text(
            text = name,
            color = if (active) tokenColor(t.text) else tokenColor(t.text2),
            fontSize = 11.sp,
            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
            fontFamily = nibFontFamily(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ColorChip(accent: Long, bg: Long, active: Boolean, onClick: () -> Unit) {
    val t = LocalNibTokens.current
    val shape = RoundedCornerShape(7.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(27.dp)
            .clip(shape)
            .background(Brush.linearGradient(listOf(tokenColor(accent), tokenColor(bg))))
            .border(
                width = if (active) 2.dp else 0.8.dp,
                color = when {
                    active -> tokenColor(t.text)
                    t.dark -> tokenColor(withAlpha(0xFFFFFFFFL, 56))
                    else -> tokenColor(withAlpha(0xFF000000L, 40))
                },
                shape = shape,
            )
            .clickable(onClick = onClick),
    )
}

@Composable
private fun BaseSegment(dark: Boolean, onDark: (Boolean) -> Unit) {
    val t = LocalNibTokens.current
    val shape = RoundedCornerShape(t.radiusM.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(tokenColor(t.card2))
            .padding(2.dp),
    ) {
        listOf(true to "深色", false to "浅色").forEach { (value, label) ->
            val on = dark == value
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape((t.radiusM - 2).coerceAtLeast(1).dp))
                    .background(if (on) tokenColor(t.accentWeak) else Color.Transparent)
                    .clickable { onDark(value) }
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            ) {
                Text(
                    text = label,
                    color = if (on) tokenColor(t.accent) else tokenColor(t.text3),
                    fontSize = 11.sp,
                    fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
                    fontFamily = nibFontFamily(),
                )
            }
        }
    }
}

@Composable
private fun TuneSlider(field: TuneField, value: Int, onChange: (Int) -> Unit) {
    val t = LocalNibTokens.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = field.label,
                color = tokenColor(t.text2),
                fontSize = 11.5.sp,
                fontFamily = nibFontFamily(),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "$value${field.suffix}",
                color = tokenColor(t.accent),
                fontSize = 11.sp,
                fontFamily = nibFontFamily(),
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = field.min.toFloat()..field.max.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = tokenColor(t.accent),
                activeTrackColor = tokenColor(t.accent),
                inactiveTrackColor = tokenColor(t.line2),
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ThemePanel(vm: NibViewModel, config: ThemeConfig, t: NibTokens) {
    var fold by remember { mutableStateOf<String?>(null) }
    var more by remember { mutableStateOf(false) }
    val dirty by vm.themeDirty.collectAsStateWithLifecycle()
    val style = themeStyles.firstOrNull { it.id == t.styleId } ?: themeStyles[0]
    val palette = palettes.firstOrNull { it.key == t.colorKey } ?: palettes[0]
    NibCard {
        PanelLine("主题风格", { fold = if (fold == "style") null else "style" }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StyleThumb(style.id, 18)
                Spacer(Modifier.width(7.dp))
                Text(style.name, color = tokenColor(t.text2), fontSize = 12.sp, fontFamily = nibFontFamily())
                Text("  ▾", color = tokenColor(t.text3), fontSize = 10.sp)
            }
        }
        if (fold == "style") {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                themeStyles.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        row.forEach { item ->
                            Box(Modifier.weight(1f)) {
                                StylePill(item.id, item.name, item.id == t.styleId) { vm.setStyle(item.id) }
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            NibRowDivider()
        }
        PanelLine("主题颜色", { fold = if (fold == "color") null else "color" }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(tokenColor(palette.accent), tokenColor(palette.bg)))),
                )
                Spacer(Modifier.width(7.dp))
                Text(palette.name, color = tokenColor(t.text2), fontSize = 12.sp, fontFamily = nibFontFamily())
                Text("  ▾", color = tokenColor(t.text3), fontSize = 10.sp)
            }
        }
        if (fold == "color") {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(palettes.take(6), palettes.drop(6)).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        row.forEach { item ->
                            Box(Modifier.weight(1f)) {
                                ColorChip(item.accent, item.bg, item.key == t.colorKey) { vm.setColor(item.key) }
                            }
                        }
                    }
                }
            }
            NibRowDivider()
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BaseSegment(t.dark) { vm.setDark(it) }
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .clickable { more = !more }
                    .padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (more) "收起调节" else "更多调节",
                    color = tokenColor(t.accent),
                    fontSize = 11.sp,
                    fontFamily = nibFontFamily(),
                )
                Text(
                    text = if (more) "  ↑" else "  →",
                    color = tokenColor(t.accent),
                    fontSize = 11.sp,
                )
            }
        }
        if (more) {
            NibRowDivider()
            Column(modifier = Modifier.padding(top = 9.dp, bottom = 2.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "风格微调",
                        color = tokenColor(t.text),
                        fontSize = 11.5.sp,
                        fontFamily = nibFontFamily(),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${style.name} 专属参数",
                        color = tokenColor(t.text3),
                        fontSize = 10.sp,
                        fontFamily = nibFontFamily(),
                    )
                }
                tuneFields(t.styleId).forEach { field ->
                    TuneSlider(field, config.tuneValue(field.key)) { vm.setTune(field.key, it) }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "恢复本风格默认",
                        color = tokenColor(t.text3),
                        fontSize = 10.5.sp,
                        fontFamily = nibFontFamily(),
                        modifier = Modifier
                            .clickable { vm.resetTune() }
                            .padding(vertical = 2.dp),
                    )
                }
            }
        }
        if (dirty) {
            NibRowDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "有未保存的更改",
                    color = tokenColor(t.accent),
                    fontSize = 11.sp,
                    fontFamily = nibFontFamily(),
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { vm.revertTheme() },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Text("放弃", color = tokenColor(t.text2), fontSize = 11.sp, fontFamily = nibFontFamily())
                }
                TextButton(
                    onClick = { vm.commitTheme(); vm.setNotice("主题已保存") },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                ) {
                    Text("保存主题", color = tokenColor(t.accent), fontSize = 11.sp, fontWeight = FontWeight.Medium, fontFamily = nibFontFamily())
                }
            }
        }
    }
}

@Composable
private fun ManageRow(
    name: String,
    count: Int,
    icon: ImageVector,
    color: Color,
    onDelete: () -> Unit,
) {
    val t = LocalNibTokens.current
    var confirm by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 6.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NibIconBadge(icon, color)
        Spacer(Modifier.width(11.dp))
        Text(
            text = name,
            color = tokenColor(t.text),
            fontSize = 14.sp,
            fontFamily = nibFontFamily(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = count.toString(),
            color = tokenColor(t.text3),
            fontSize = 11.5.sp,
            fontFamily = nibFontFamily(),
        )
        IconButton(onClick = { confirm = true }, modifier = Modifier.size(32.dp)) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(tokenColor(t.danger).copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Delete, contentDescription = "删除", tint = tokenColor(t.danger), modifier = Modifier.size(14.dp))
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("删除「$name」") },
            text = { Text("条目本身不会被删除，只会解除归属。") },
            confirmButton = {
                TextButton(onClick = { onDelete(); confirm = false }) {
                    Text("删除", color = tokenColor(t.danger))
                }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun AddRow(text: String, sub: String, onClick: () -> Unit) {
    val t = LocalNibTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(27.dp)
                .clip(RoundedCornerShape(9.dp))
                .border(0.8.dp, tokenColor(t.line2), RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("+", color = tokenColor(t.accent), fontSize = 15.sp, fontFamily = nibFontFamily())
        }
        Spacer(Modifier.width(11.dp))
        Text(text, color = tokenColor(t.accent), fontSize = 13.sp, fontFamily = nibFontFamily(), modifier = Modifier.weight(1f))
        Text(sub, color = tokenColor(t.text3), fontSize = 10.5.sp, fontFamily = nibFontFamily())
    }
}

private fun toggleGroup(current: String, key: String): String {
    val keys = current.split(',').filter { it.isNotEmpty() }
    return (if (key in keys) keys - key else keys + key).joinToString(",")
}

@Composable
private fun GroupHeader(title: String, sub: String?, expanded: Boolean, onToggle: () -> Unit) {
    val t = LocalNibTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(t.radiusM.dp))
            .clickable { onToggle() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) { SectionTitle(title, sub) }
        ExpandBadge(expanded, t)
    }
}

@Composable
private fun ExpandBadge(expanded: Boolean, t: NibTokens) {
    val accent = tokenColor(t.accent)
    val angle by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(220),
        label = "expandAngle",
    )
    Row(
        modifier = Modifier
            .offset(y = if (t.styleId == "v3b") 10.dp else 5.dp)
            .clip(RoundedCornerShape(50))
            .background(accent.copy(alpha = 0.16f))
            .border(0.8.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(50))
            .padding(start = 9.dp, end = 5.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (expanded) "收起" else "展开",
            color = accent,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = nibFontFamily(),
        )
        Spacer(Modifier.width(2.dp))
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = if (expanded) "收起" else "展开",
            tint = accent,
            modifier = Modifier
                .size(15.dp)
                .rotate(angle),
        )
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, value: String, onClick: () -> Unit) {
    val t = LocalNibTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NibIconBadge(icon, tokenColor(t.accent))
        Spacer(Modifier.width(11.dp))
        Text(label, color = tokenColor(t.text), fontSize = 13.5.sp, fontFamily = nibFontFamily(), modifier = Modifier.weight(1f))
        Text(value, color = tokenColor(t.text3), fontSize = 11.5.sp, fontFamily = nibFontFamily())
    }
}

@Composable
private fun SpinningRefresh(spinning: Boolean, tint: Color, size: Dp) {
    if (!spinning) {
        Icon(Icons.Default.Refresh, contentDescription = null, tint = tint, modifier = Modifier.size(size))
        return
    }
    val transition = rememberInfiniteTransition(label = "updateSpin")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "updateAngle",
    )
    Icon(
        Icons.Default.Refresh,
        contentDescription = null,
        tint = tint,
        modifier = Modifier
            .size(size)
            .rotate(angle),
    )
}

@Composable
private fun UpdateRow(hint: String, spinning: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val t = LocalNibTokens.current
    val accent = tokenColor(t.accent)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(27.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            SpinningRefresh(spinning, accent, 15.dp)
        }
        Spacer(Modifier.width(11.dp))
        Text(
            text = "检查更新",
            color = tokenColor(t.text),
            fontSize = 13.5.sp,
            fontFamily = nibFontFamily(),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = hint,
            color = if (spinning) accent else tokenColor(t.text3),
            fontSize = 11.5.sp,
            fontFamily = nibFontFamily(),
        )
    }
}

@Composable
private fun DownloadBar(fraction: Float) {
    val t = LocalNibTokens.current
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(shape)
            .background(tokenColor(t.line2)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(shape)
                .background(tokenColor(t.accent)),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MeScreen(vm: NibViewModel, onOpenPermissions: () -> Unit) {
    val theme by vm.theme.collectAsStateWithLifecycle()
    val lists by vm.lists.collectAsStateWithLifecycle()
    val tags by vm.tags.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()
    val conflicts by vm.conflicts.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val bundles by vm.bundles.collectAsStateWithLifecycle()
    val t = LocalNibTokens.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf("") }
    var pendingWrite by remember { mutableStateOf<String?>(null) }
    var crashText by remember { mutableStateOf<String?>(null) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var updateHint by remember { mutableStateOf("检查") }
    var checkingUpdate by remember { mutableStateOf(false) }
    var installReady by remember { mutableStateOf<File?>(null) }
    val download by UpdateCenter.download.collectAsStateWithLifecycle()
    var openGroups by rememberSaveable { mutableStateOf("") }
    val openKeys = openGroups.split(',').filter { it.isNotEmpty() }.toSet()

    val (undone, done) = splitByDone(bundles)
    val weekStart = System.currentTimeMillis() - 7L * 24L * 3600L * 1000L
    val doneWeek = bundles.count { it.entry.done == 1 && (it.entry.doneAt ?: 0L) >= weekStart }

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val text = pendingWrite ?: return@rememberLauncherForActivityResult
        pendingWrite = null
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            withContext(Dispatchers.Main) { vm.setNotice("导出完成") }
        }
    }

    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            }
            vm.setNotice(vm.importBackup(text))
        }
    }

    fun requestExport(format: String) {
        scope.launch {
            val (name, text) = vm.buildExport(format)
            pendingWrite = text
            createDocument.launch(name)
        }
    }

    LaunchedEffect(dialog) { input = "" }

    LaunchedEffect(Unit) {
        crashText = withContext(Dispatchers.IO) { CrashCatcher.latest(context) }
    }

    val installGate = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val file = installReady ?: return@rememberLauncherForActivityResult
        if (!ApkInstall.canInstall(context)) {
            vm.setNotice("尚未允许安装未知应用")
            return@rememberLauncherForActivityResult
        }
        if (ApkInstall.install(context, file)) {
            installReady = null
            UpdateCenter.reset()
        } else {
            vm.setNotice("无法启动安装器")
        }
    }

    LaunchedEffect(download.phase) {
        if (download.phase != DownloadPhase.Done) return@LaunchedEffect
        val file = download.file ?: return@LaunchedEffect
        if (ApkInstall.canInstall(context)) {
            if (ApkInstall.install(context, file)) UpdateCenter.reset() else vm.setNotice("无法启动安装器")
            return@LaunchedEffect
        }
        installReady = file
        val intent = ApkInstall.permissionIntent(context)
        if (intent == null) {
            if (ApkInstall.install(context, file)) {
                installReady = null
                UpdateCenter.reset()
            }
        } else {
            runCatching { installGate.launch(intent) }.onFailure {
                installReady = null
                vm.setNotice("请在系统设置中允许安装未知应用")
            }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            NibHero(
                sub = "全部数据存在本机",
                title = "我的",
                actions = {
                    Box(
                        modifier = Modifier
                            .size(31.dp)
                            .clip(RoundedCornerShape(t.radiusM.dp))
                            .background(tokenColor(t.card2))
                            .border(0.6.dp, tokenColor(t.line), RoundedCornerShape(t.radiusM.dp))
                            .clickable { onOpenPermissions() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Build, contentDescription = null, tint = tokenColor(t.text2), modifier = Modifier.size(15.dp))
                    }
                },
            )
        }
        item {
            Padded {
                StatRow(undone.size, doneWeek, bundles.size)
            }
        }
        item { Padded { ThemePanel(vm, theme, t) } }

        item {
            Padded {
                GroupHeader("清单", "一条只属于一个", "lists" in openKeys) {
                    openGroups = toggleGroup(openGroups, "lists")
                }
            }
        }
        if ("lists" in openKeys) item {
            Padded {
                NibCard {
                    lists.forEach { list ->
                        val count = bundles.count { it.entry.listId == list.id && it.entry.done != 1 }
                        ManageRow(list.name, count, Icons.AutoMirrored.Filled.List, hexColor(list.color, tokenColor(t.accent))) {
                            vm.deleteList(list.id)
                        }
                        NibRowDivider()
                    }
                    val uncategorized = bundles.count { it.entry.listId == null && it.entry.done != 1 }
                    AddRow("新建清单", if (uncategorized > 0) "$uncategorized 条未分类" else "") { dialog = "addList" }
                }
            }
        }

        item {
            Padded {
                GroupHeader("标签", "一条可以有多个，跨清单", "tags" in openKeys) {
                    openGroups = toggleGroup(openGroups, "tags")
                }
            }
        }
        if ("tags" in openKeys) item {
            Padded {
                NibCard {
                    tags.forEach { tag ->
                        val count = bundles.count { tag.id in it.tagIds && it.entry.done != 1 }
                        ManageRow(tag.name, count, Icons.Default.Star, tokenColor(t.accent2)) {
                            vm.deleteTag(tag.id)
                        }
                        NibRowDivider()
                    }
                    AddRow("新建标签", "速记里输 # 也能建") { dialog = "addTag" }
                }
            }
        }

        item {
            Padded {
                GroupHeader("同步与导出", null, "sync" in openKeys) {
                    openGroups = toggleGroup(openGroups, "sync")
                }
            }
        }
        if ("sync" in openKeys) item {
            Padded {
                NibCard {
                    ActionRow(Icons.Default.Refresh, "待推送变更", "${sync.pending} 条") { vm.refreshSyncState() }
                    ActionRow(Icons.Default.Refresh, "断点游标", "${sync.cursor}") { vm.refreshSyncState() }
                    ActionRow(Icons.Default.Info, "冲突记录", "${sync.conflicts} 条") { dialog = "conflicts" }
                    ActionRow(Icons.AutoMirrored.Filled.List, "导出 Markdown", "保存") { requestExport("md") }
                    ActionRow(Icons.Default.DateRange, "导出 ICS 日程", "保存") { requestExport("ics") }
                    ActionRow(Icons.Default.Info, "整库备份 JSON", "保存") { requestExport("json") }
                    ActionRow(Icons.Default.Build, "导入备份", "选择文件") {
                        openDocument.launch(arrayOf("application/json", "text/plain", "*/*"))
                    }
                    if (notice != null) {
                        Text(
                            text = notice.orEmpty(),
                            color = tokenColor(t.text3),
                            fontSize = 11.5.sp,
                            fontFamily = nibFontFamily(),
                            modifier = Modifier
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                                .clickable { vm.clearNotice() },
                        )
                    }
                }
            }
        }

        item { Padded { SectionTitle("系统能力") } }
        item {
            Padded {
                NibCard {
                    ActionRow(Icons.Default.Notifications, "通知与日历权限", "查看") { onOpenPermissions() }
                }
            }
        }

        item { Padded { SectionTitle("诊断") } }
        item {
            Padded {
                NibCard {
                    ActionRow(Icons.Default.Build, "最近崩溃", if (crashText == null) "无" else "查看") {
                        if (crashText != null) dialog = "crash"
                    }
                    ActionRow(Icons.Default.Build, "崩溃日志路径", "查看") {
                        vm.setNotice(CrashCatcher.exportPath(context))
                    }
                }
            }
        }

        item { Padded { SectionTitle("关于") } }
        item {
            Padded {
                NibCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NibIconBadge(Icons.Default.Info, tokenColor(t.text3))
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text("落笔 Nib", color = tokenColor(t.text), fontSize = 13.5.sp, fontFamily = nibFontFamily())
                            Text(
                                "数据仅存本机",
                                color = tokenColor(t.text3),
                                fontSize = 10.5.sp,
                                fontFamily = nibFontFamily(),
                            )
                        }
                        Text("v${BuildConfig.VERSION_NAME}", color = tokenColor(t.text3), fontSize = 11.sp, fontFamily = nibFontFamily())
                    }
                    NibRowDivider()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { openUrl(context, PROJECT_URL) },
                                onLongClick = { copyText(context, "项目地址", PROJECT_URL) },
                            )
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                    ) {
                        Text("项目地址 · 长按复制", color = tokenColor(t.text3), fontSize = 10.5.sp, fontFamily = nibFontFamily())
                        Text(
                            text = PROJECT_URL.removePrefix("https://"),
                            color = tokenColor(t.accent),
                            fontSize = 12.5.sp,
                            fontFamily = nibFontFamily(),
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    NibRowDivider()
                    UpdateRow(
                        hint = when {
                            checkingUpdate -> "检查中"
                            download.phase == DownloadPhase.Running -> "${(download.fraction * 100).roundToInt()}%"
                            download.phase == DownloadPhase.Done -> "待安装"
                            download.phase == DownloadPhase.Failed -> "下载失败"
                            else -> updateHint
                        },
                        spinning = checkingUpdate || download.phase == DownloadPhase.Running,
                        enabled = !checkingUpdate && download.phase != DownloadPhase.Running,
                        onClick = {
                            if (!checkingUpdate && download.phase != DownloadPhase.Running) {
                                if (download.phase == DownloadPhase.Done || download.phase == DownloadPhase.Failed) {
                                    UpdateCenter.reset()
                                }
                                checkingUpdate = true
                                updateHint = "检查中"
                                scope.launch {
                                    val result = UpdateChecker.latest()
                                    checkingUpdate = false
                                    result.onSuccess { info ->
                                        if (isNewerVersion(info.version, BuildConfig.VERSION_NAME)) {
                                            updateHint = "发现 v${info.version}"
                                            updateInfo = info
                                        } else {
                                            updateHint = "已是最新"
                                        }
                                    }.onFailure {
                                        updateHint = "检查失败"
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }
        item { Spacer(Modifier.height(104.dp)) }
    }

    when (dialog) {
        "conflicts" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("冲突记录 ${conflicts.size}") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (conflicts.isEmpty()) Text("暂无冲突", fontSize = 13.sp)
                    conflicts.forEach { conflict ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${conflict.field} · ${conflict.entityId}", fontSize = 13.sp)
                                Text("败者原文：${conflict.loserValue ?: "空"}", fontSize = 12.sp)
                                Text("另一台设备改过 · ${conflict.deviceId}", fontSize = 11.sp)
                            }
                            IconButton(onClick = { vm.deleteConflict(conflict.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = null)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("关闭") } },
        )
        "crash" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("最近崩溃") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(crashText.orEmpty(), fontSize = 11.sp)
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("关闭") } },
        )
        "addList", "addTag" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(if (dialog == "addList") "新建清单" else "新建标签") },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    label = { Text("名称") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = input.trim()
                    if (name.isNotEmpty()) {
                        if (dialog == "addList") vm.addList(name) else vm.addTag(name)
                    }
                    dialog = null
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("取消") } },
        )
    }

    val pending = updateInfo
    if (pending != null) {
        AlertDialog(
            onDismissRequest = { updateInfo = null },
            title = { Text("发现新版本 v${pending.version}") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(pending.notes, fontSize = 12.5.sp, fontFamily = nibFontFamily())
                }
            },
            confirmButton = {
                TextButton(
                    enabled = pending.downloadUrl != null,
                    onClick = {
                        val url = pending.downloadUrl
                        if (url != null) {
                            updateInfo = null
                            UpdateCenter.start(context, pending.version, url)
                        }
                    },
                ) {
                    Text(
                        text = if (pending.downloadUrl != null) "下载并安装" else "暂无安装包",
                        color = if (pending.downloadUrl != null) tokenColor(t.accent) else tokenColor(t.text3),
                        fontFamily = nibFontFamily(),
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { updateInfo = null }) {
                    Text("稍后", color = tokenColor(t.text2), fontFamily = nibFontFamily())
                }
            },
        )
    }

    if (download.phase == DownloadPhase.Running) {
        AlertDialog(
            onDismissRequest = {},
            title = {
                Text(
                    text = if (download.version.isBlank()) "正在下载安装包" else "正在下载 v${download.version}",
                    fontFamily = nibFontFamily(),
                )
            },
            text = {
                Column {
                    DownloadBar(download.fraction)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${(download.fraction * 100).roundToInt()}%",
                            color = tokenColor(t.accent),
                            fontSize = 12.sp,
                            fontFamily = nibFontFamily(),
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = if (download.total > 0) {
                                "${formatSize(download.received)} / ${formatSize(download.total)}"
                            } else {
                                formatSize(download.received)
                            },
                            color = tokenColor(t.text3),
                            fontSize = 11.sp,
                            fontFamily = nibFontFamily(),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "下载完成后自动打开系统安装器",
                        color = tokenColor(t.text3),
                        fontSize = 11.sp,
                        fontFamily = nibFontFamily(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { UpdateCenter.cancel() }) {
                    Text("取消", color = tokenColor(t.text2), fontSize = 12.sp, fontFamily = nibFontFamily())
                }
            },
            dismissButton = {},
        )
    }

    if (download.phase == DownloadPhase.Failed) {
        AlertDialog(
            onDismissRequest = { UpdateCenter.reset() },
            title = { Text("下载失败", fontFamily = nibFontFamily()) },
            text = {
                Text(
                    text = download.message.orEmpty(),
                    fontSize = 12.5.sp,
                    fontFamily = nibFontFamily(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = download.url.isNotBlank(),
                    onClick = { UpdateCenter.start(context, download.version, download.url) },
                ) {
                    Text("重试", color = tokenColor(t.accent), fontFamily = nibFontFamily())
                }
            },
            dismissButton = {
                TextButton(onClick = { UpdateCenter.reset() }) {
                    Text("关闭", color = tokenColor(t.text2), fontFamily = nibFontFamily())
                }
            },
        )
    }
}

private fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

private fun copyText(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "已复制$label", Toast.LENGTH_SHORT).show()
}
