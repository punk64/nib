package com.yourname.nib.features.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourname.nib.NibViewModel
import com.yourname.nib.core.calendar.agendaClock
import com.yourname.nib.core.calendar.agendaDayKey
import com.yourname.nib.core.calendar.agendaEndClock
import com.yourname.nib.core.theme.LocalNibTokens
import com.yourname.nib.core.theme.nibFontFamily
import com.yourname.nib.core.theme.tokenColor
import com.yourname.nib.core.theme.withAlpha
import com.yourname.nib.core.time.NibDate
import com.yourname.nib.widgets.EmptyHint
import com.yourname.nib.widgets.EntryList
import com.yourname.nib.widgets.NibCard
import com.yourname.nib.widgets.NibHero
import com.yourname.nib.widgets.NibIconButton
import com.yourname.nib.widgets.SectionTitle

@Composable
private fun Padded(content: @Composable () -> Unit) {
    Box(Modifier.padding(horizontal = 18.dp)) { content() }
}

@Composable
fun CalendarScreen(vm: NibViewModel, onOpen: (String) -> Unit, onPermissions: () -> Unit) {
    val bundles by vm.bundles.collectAsStateWithLifecycle()
    val agenda by vm.agenda.collectAsStateWithLifecycle()
    val agendaError by vm.agendaError.collectAsStateWithLifecycle()
    val calendarCount by vm.calendarCount.collectAsStateWithLifecycle()
    val calendarSync by vm.calendarSync.collectAsStateWithLifecycle()
    val t = LocalNibTokens.current
    val now = NibDate.today()
    var month by remember { mutableStateOf(NibDate(now.year, now.month, 1)) }
    var selected by remember { mutableStateOf(now.dateKey()) }
    val first = NibDate(month.year, month.month, 1)
    val nextFirst = if (month.month == 12) {
        NibDate(month.year + 1, 1, 1)
    } else {
        NibDate(month.year, month.month + 1, 1)
    }
    LaunchedEffect(month) { vm.reloadAgenda(first, nextFirst) }
    val dayCount = (nextFirst.epochDay - first.epochDay).toInt()
    val lead = (first.dayOfWeek + 6) % 7
    val weeks = (lead + dayCount + 6) / 7
    val dueKeys = bundles.mapNotNull { it.entry.dueDate }.toSet()
    val agendaByDay = agenda.groupBy { agendaDayKey(it) }
    val selectedEntries = bundles.filter { it.entry.dueDate == selected }
    val dayAgenda = agendaByDay[selected].orEmpty()
    val scheduled = bundles.count { it.entry.done != 1 && it.entry.dueDate != null }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            NibHero(
                sub = "$scheduled 项已安排 · ${agenda.size} 条系统日程",
                title = "日历",
                actions = {
                    NibIconButton(Icons.AutoMirrored.Filled.ArrowBack) {
                        month = if (month.month == 1) NibDate(month.year - 1, 12, 1) else NibDate(month.year, month.month - 1, 1)
                    }
                    NibIconButton(Icons.AutoMirrored.Filled.ArrowForward) {
                        month = if (month.month == 12) NibDate(month.year + 1, 1, 1) else NibDate(month.year, month.month + 1, 1)
                    }
                },
            )
        }
        val sync = calendarSync
        if (sync != null && (sync.error != null || sync.note != null || sync.created + sync.updated + sync.removed > 0)) {
            item {
                Padded {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
                    ) {
                        Text(
                            text = when {
                                sync.error != null -> "系统日历同步失败：${sync.error}"
                                sync.created + sync.updated + sync.removed > 0 ->
                                    "已写入「${sync.calendarName ?: "系统日历"}」${sync.created + sync.updated + sync.removed} 条"
                                else -> sync.note.orEmpty()
                            },
                            color = tokenColor(
                                when {
                                    sync.error != null -> t.danger
                                    sync.note != null -> t.warn
                                    else -> t.text3
                                },
                            ),
                            fontSize = 11.sp,
                            fontFamily = nibFontFamily(),
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { vm.syncCalendarNow() }) {
                            Text("同步", color = tokenColor(t.accent), fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        item {
            Padded {
                Text(
                    text = "${month.year} 年 ${month.month} 月",
                    color = tokenColor(t.text2),
                    fontSize = 12.sp,
                    fontFamily = nibFontFamily(),
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                )
            }
        }
        item {
            Padded {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                    listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text(
                                text = label,
                                color = tokenColor(t.text3),
                                fontSize = 10.5.sp,
                                fontFamily = nibFontFamily(),
                            )
                        }
                    }
                }
            }
        }
        item {
            Padded {
                Column(modifier = Modifier.fillMaxWidth()) {
                    for (week in 0 until weeks) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            for (column in 0 until 7) {
                                val index = week * 7 + column - lead
                                if (index in 1..dayCount) {
                                    val key = NibDate(month.year, month.month, index).dateKey()
                                    val isSelected = key == selected
                                    val isToday = key == now.dateKey()
                                    val hasLocal = key in dueKeys
                                    val hasEvent = key in agendaByDay
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .padding(2.dp)
                                            .clip(RoundedCornerShape(t.radiusM.dp))
                                            .background(if (isSelected) tokenColor(t.accent) else Color.Transparent)
                                            .border(
                                                if (isToday && !isSelected) 1.dp else 0.dp,
                                                tokenColor(withAlpha(t.accent, 120)),
                                                RoundedCornerShape(t.radiusM.dp),
                                            )
                                            .clickable { selected = key },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = index.toString(),
                                                color = when {
                                                    isSelected -> tokenColor(0xFF0B0C10L)
                                                    isToday -> tokenColor(t.accent)
                                                    else -> tokenColor(t.text)
                                                },
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected || isToday) FontWeight.SemiBold else FontWeight.Normal,
                                                fontFamily = nibFontFamily(),
                                            )
                                            Spacer(Modifier.size(3.dp))
                                            Row(
                                                modifier = Modifier.height(4.dp),
                                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                if (hasLocal) {
                                                    Box(
                                                        Modifier
                                                            .size(4.dp)
                                                            .clip(CircleShape)
                                                            .background(if (isSelected) tokenColor(0xFF0B0C10L) else tokenColor(t.accent)),
                                                    )
                                                }
                                                if (hasEvent) {
                                                    Box(
                                                        Modifier
                                                            .size(4.dp)
                                                            .clip(CircleShape)
                                                            .border(
                                                                1.dp,
                                                                if (isSelected) tokenColor(withAlpha(0xFF0B0C10L, 150)) else tokenColor(withAlpha(t.text3, 170)),
                                                                CircleShape,
                                                            ),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(6.dp)) }
        item { Padded { SectionTitle(selected, "${selectedEntries.size} 项记录") } }
        item {
            Padded {
                if (selectedEntries.isEmpty()) {
                    EmptyHint("这天没有记录")
                } else {
                    EntryList(
                        vm = vm,
                        items = selectedEntries,
                        selected = emptySet(),
                        onOpen = onOpen,
                    )
                }
            }
        }
        item { Padded { SectionTitle("系统日程", if (agendaError != null) "读取失败" else "${dayAgenda.size} 条") } }
        item {
            Padded {
                when {
                    agendaError != null -> NibCard {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "读取系统日历失败：${agendaError.orEmpty()}",
                                color = tokenColor(t.danger),
                                fontSize = 12.sp,
                                fontFamily = nibFontFamily(),
                            )
                            Text(
                                text = "去开启权限 →",
                                color = tokenColor(t.accent),
                                fontSize = 12.sp,
                                fontFamily = nibFontFamily(),
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .clickable { onPermissions() },
                            )
                        }
                    }
                    dayAgenda.isEmpty() -> EmptyHint(
                        if (calendarCount == 0) "系统里没有可读的日历" else "这天系统日历没有日程",
                    )
                    else -> NibCard {
                        dayAgenda.forEachIndexed { index, item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = if (item.allDay) "全天" else "${agendaClock(item)}–${agendaEndClock(item)}",
                                    color = tokenColor(t.text2),
                                    fontSize = 11.sp,
                                    fontFamily = if (t.mono) androidx.compose.ui.text.font.FontFamily.Monospace else null,
                                )
                                Text(
                                    text = item.title.ifBlank { "未命名日程" },
                                    color = tokenColor(t.text),
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontFamily = nibFontFamily(),
                                    modifier = Modifier.padding(start = 10.dp).weight(1f),
                                )
                            }
                            if (index < dayAgenda.lastIndex) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp)
                                        .height(0.6.dp)
                                        .background(tokenColor(withAlpha(t.text3, 40))),
                                )
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(104.dp)) }
    }
}
