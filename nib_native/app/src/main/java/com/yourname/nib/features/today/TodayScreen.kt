package com.yourname.nib.features.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourname.nib.NibViewModel
import com.yourname.nib.core.model.splitByDone
import com.yourname.nib.core.theme.LocalNibTokens
import com.yourname.nib.core.theme.nibFontFamily
import com.yourname.nib.core.theme.tokenColor
import com.yourname.nib.core.theme.withAlpha
import com.yourname.nib.core.time.NibDate
import com.yourname.nib.widgets.BatchBar
import com.yourname.nib.widgets.EmptyHint
import com.yourname.nib.widgets.EntryList
import com.yourname.nib.widgets.NibCard
import com.yourname.nib.widgets.NibHero
import com.yourname.nib.widgets.SectionTitle

private val weekLabels = listOf("星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六")

private fun weekdayLabel(date: NibDate): String {
    val index = (((date.epochDay + 4) % 7 + 7) % 7).toInt()
    return weekLabels[index]
}

@Composable
private fun ProgressRing(percent: Int, size: Int = 46) {
    val t = LocalNibTokens.current
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size.dp)) {
            val stroke = 3.6.dp.toPx()
            drawArc(
                color = tokenColor(withAlpha(t.text3, 46)),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = tokenColor(t.accent),
                startAngle = -90f,
                sweepAngle = 360f * (percent / 100f),
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Text(
            text = "$percent%",
            color = tokenColor(t.text),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = nibFontFamily(),
        )
    }
}

@Composable
private fun TodayHero(overdue: Int, today: Int, done: Int) {
    val t = LocalNibTokens.current
    val date = NibDate.today()
    val total = overdue + today
    val percent = if (total + done == 0) 0 else (done * 100 / (total + done))
    NibHero(
        sub = weekdayLabel(date),
        title = "${date.month} 月 ${date.day} 日",
    ) {
        NibCard {
            Row(
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProgressRing(percent, 42)
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (total == 0) "今天已清空" else "$total 项待办",
                        color = tokenColor(t.text),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = nibFontFamily(),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = buildList {
                            if (done > 0) add("已完成 $done")
                            if (overdue > 0) add("$overdue 项已逾期")
                            if (done == 0 && overdue == 0) add("保持这个节奏")
                        }.joinToString(" · "),
                        color = tokenColor(t.text3),
                        fontSize = 11.5.sp,
                        fontFamily = nibFontFamily(),
                    )
                }
            }
        }
    }
}

@Composable
private fun Padded(content: @Composable () -> Unit) {
    Box(Modifier.padding(horizontal = 18.dp)) { content() }
}

@Composable
fun TodayScreen(vm: NibViewModel, onOpen: (String) -> Unit) {
    val bundles by vm.bundles.collectAsStateWithLifecycle()
    val lists by vm.lists.collectAsStateWithLifecycle()
    val tags by vm.tags.collectAsStateWithLifecycle()
    val selected by vm.selectedIds.collectAsStateWithLifecycle()
    val todayKey = NibDate.today().dateKey()
    val (undone, done) = splitByDone(bundles)
    val overdue = undone.filter { it.entry.dueDate?.let { d -> d < todayKey } ?: false }
    val todayItems = undone.filter { it.entry.dueDate == todayKey }
    val noDue = undone.filter { it.entry.dueDate == null }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { TodayHero(overdue.size, todayItems.size, done.size) }
        if (selected.isNotEmpty()) {
            item {
                Padded {
                    BatchBar(
                        count = selected.size,
                        lists = lists,
                        tags = tags,
                        onList = { vm.batchSetList(it) },
                        onTag = { vm.batchAddTag(it) },
                        onDelete = { vm.batchDelete() },
                        onCancel = { vm.clearSelection() },
                    )
                }
            }
        }
        item { Padded { SectionTitle("逾期", "${overdue.size} 项") } }
        item {
            Padded {
                if (overdue.isEmpty()) {
                    EmptyHint("没有逾期条目")
                } else {
                    EntryList(vm = vm, items = overdue, selected = selected, onOpen = onOpen)
                }
            }
        }
        item { Padded { SectionTitle("今天", "${todayItems.size} 项") } }
        item {
            Padded {
                if (todayItems.isEmpty()) {
                    EmptyHint("今天没有安排")
                } else {
                    EntryList(vm = vm, items = todayItems, selected = selected, onOpen = onOpen)
                }
            }
        }
        item { Padded { SectionTitle("未排期", "${noDue.size} 项") } }
        item {
            Padded {
                if (noDue.isEmpty()) {
                    EmptyHint("没有未排期的条目")
                } else {
                    EntryList(vm = vm, items = noDue, selected = selected, onOpen = onOpen)
                }
            }
        }
        if (done.isNotEmpty()) {
            item { Padded { SectionTitle("已完成", "${done.size} 项") } }
            item {
                Padded {
                    EntryList(vm = vm, items = done, selected = selected, onOpen = onOpen)
                }
            }
        }
        item { Spacer(Modifier.height(104.dp)) }
    }
}
