package com.yourname.nib.features.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourname.nib.NibViewModel
import com.yourname.nib.core.calendar.CHANNEL_CALENDAR
import com.yourname.nib.core.calendar.CHANNEL_NOTIFY
import com.yourname.nib.core.model.clampRepeatN
import com.yourname.nib.core.model.repeatLabel
import com.yourname.nib.core.reminder.leadMinutesFor
import com.yourname.nib.core.reminder.remindAtFor
import com.yourname.nib.core.theme.LocalNibTokens
import com.yourname.nib.core.theme.nibFontFamily
import com.yourname.nib.core.theme.tokenColor
import com.yourname.nib.widgets.NibActionPill
import com.yourname.nib.widgets.NibCard
import com.yourname.nib.widgets.NibDateTimeDialog
import com.yourname.nib.widgets.NibHero
import com.yourname.nib.widgets.NibIconButton
import com.yourname.nib.widgets.SectionTitle

private data class DetailState(
    val title: String,
    val body: String,
    val dueDate: String?,
    val dueTime: String?,
    val listId: String?,
    val tagIds: Set<String>,
    val priority: Int,
    val repeatFreq: String,
    val repeatN: String,
    val leadMinutes: Int,
    val channel: String,
    val durationMin: Int,
)

@Composable
private fun DetailRow(label: String, value: String, onClick: () -> Unit) {
    val t = LocalNibTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = tokenColor(t.text2), fontSize = 13.sp, fontFamily = nibFontFamily(), modifier = Modifier.weight(1f))
        Text(value, color = tokenColor(t.text), fontSize = 13.sp, fontFamily = nibFontFamily())
    }
}

@Composable
fun EntryDetailScreen(vm: NibViewModel, entryId: String, onClose: () -> Unit) {
    val all by vm.allBundles.collectAsStateWithLifecycle()
    val lists by vm.lists.collectAsStateWithLifecycle()
    val tags by vm.tags.collectAsStateWithLifecycle()
    val t = LocalNibTokens.current
    val bundle = all.firstOrNull { it.entry.id == entryId }
    var state by remember(entryId) { mutableStateOf<DetailState?>(null) }
    var tool by remember { mutableStateOf<String?>(null) }
    val itemFlow = remember(entryId) { vm.items(entryId) }
    val items by itemFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var newItem by remember(entryId) { mutableStateOf("") }

    LaunchedEffect(bundle) {
        if (bundle != null && state == null) {
            val e = bundle.entry
            state = DetailState(
                title = e.title,
                body = e.body,
                dueDate = e.dueDate,
                dueTime = e.dueTime,
                listId = e.listId,
                tagIds = bundle.tagIds,
                priority = e.priority,
                repeatFreq = e.repeatFreq,
                repeatN = e.repeatN.toString(),
                leadMinutes = leadMinutesFor(e.dueDate, e.dueTime, e.remindAt),
                channel = e.channel,
                durationMin = e.durationMin,
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        NibHero(
            sub = bundle?.listName ?: "未分类",
            title = "详情",
            actions = {
                NibIconButton(Icons.AutoMirrored.Filled.ArrowBack) { onClose() }
                NibActionPill("删除", t.danger) {
                    vm.requestDelete(listOf(entryId))
                    onClose()
                }
                NibActionPill("保存", t.accent) {
                    val s = state
                    if (s != null && bundle != null) {
                        vm.saveEntry(
                            bundle.entry.copy(
                                title = s.title,
                                body = s.body,
                                dueDate = s.dueDate,
                                dueTime = s.dueTime,
                                listId = s.listId,
                                priority = s.priority,
                                repeatFreq = s.repeatFreq,
                                repeatN = clampRepeatN(s.repeatN),
                                remindAt = remindAtFor(s.dueDate, s.dueTime, s.leadMinutes),
                                channel = s.channel,
                                durationMin = s.durationMin,
                            ),
                            s.tagIds,
                        )
                    }
                    onClose()
                }
            },
        )
        val s = state
        if (s == null) return@Column
        Column(modifier = Modifier.padding(horizontal = 18.dp)) {
            OutlinedTextField(
                value = s.title,
                onValueChange = { state = s.copy(title = it) },
                label = { Text("标题") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = s.body,
                onValueChange = { state = s.copy(body = it) },
                label = { Text("备注与原文") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            SectionTitle("字段")
            Column(modifier = Modifier.fillMaxWidth()) {
                DetailRow("清单", s.listId?.let { id -> lists.firstOrNull { it.id == id }?.name } ?: "未分类") { tool = "list" }
                DetailRow(
                    "日期与时间",
                    s.dueDate?.let { date -> date + (s.dueTime?.let { time -> " $time" } ?: "") } ?: "未排期 · 按记录时间归档",
                ) { tool = "datetime" }
                DetailRow("重复", repeatLabel(s.repeatFreq, clampRepeatN(s.repeatN))) { tool = "repeat" }
                DetailRow("标签", s.tagIds.mapNotNull { id -> tags.firstOrNull { it.id == id }?.name }.joinToString(" ") { "#$it" }.ifEmpty { "无" }) { tool = "tag" }
                DetailRow("优先级", listOf("无", "低", "中", "高")[s.priority]) { tool = "priority" }
                DetailRow("提前提醒", if (s.leadMinutes < 0) "延后 1 分钟" else "${s.leadMinutes} 分钟") { tool = "lead" }
                DetailRow("提醒通道", if (s.channel == CHANNEL_CALENDAR) "系统日程" else "应用提醒") { tool = "channel" }
                DetailRow("时长", "${s.durationMin} 分钟") { tool = "duration" }
                DetailRow("置顶", if (bundle?.entry?.pinned == 1) "已置顶" else "否") { tool = "pinned" }
            }
            SectionTitle("子项 ${items.count { it.done == 1 }}/${items.size}")
            NibCard {
                items.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (item.done == 1) "☑" else "☐",
                            color = tokenColor(if (item.done == 1) t.accent else t.text3),
                            fontSize = 16.sp,
                            modifier = Modifier.clickable { vm.setItemDone(item.id, item.done == 0) },
                        )
                        Text(
                            text = item.content,
                            color = tokenColor(if (item.done == 1) t.text3 else t.text),
                            fontSize = 13.sp,
                            fontFamily = nibFontFamily(),
                            textDecoration = if (item.done == 1) TextDecoration.LineThrough else null,
                            modifier = Modifier.weight(1f).padding(start = 8.dp),
                        )
                        TextButton(onClick = {
                            val other = items.getOrNull(index - 1)
                            if (other != null) vm.swapItems(item.id, item.sort, other.id, other.sort)
                        }) { Text("上移", fontSize = 11.sp) }
                        TextButton(onClick = {
                            val other = items.getOrNull(index + 1)
                            if (other != null) vm.swapItems(item.id, item.sort, other.id, other.sort)
                        }) { Text("下移", fontSize = 11.sp) }
                        TextButton(onClick = { vm.deleteItem(item.id) }) {
                            Text("删除", fontSize = 11.sp, color = tokenColor(t.danger))
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = newItem,
                    onValueChange = { newItem = it },
                    label = { Text("添加子项") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    val text = newItem.trim()
                    if (text.isNotEmpty()) {
                        vm.addItem(entryId, text)
                        newItem = ""
                    }
                }) { Text("添加", color = tokenColor(t.accent)) }
            }
            if (bundle != null) {
                TextButton(onClick = { vm.setDone(entryId, bundle.entry.done == 0) }) {
                    Text(
                        if (bundle.entry.done == 0) "标记完成" else "恢复未完成",
                        color = tokenColor(t.accent),
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }

    val s = state ?: return
    when (tool) {
        "list" -> ToolDialog(
            title = "清单",
            options = listOf("未分类" to { state = s.copy(listId = null) }) + lists.map { list ->
                list.name to { state = s.copy(listId = list.id) }
            },
            onDismiss = { tool = null },
        )
        "datetime" -> NibDateTimeDialog(
            initialDate = s.dueDate,
            initialTime = s.dueTime,
            onPick = { date, time ->
                state = s.copy(dueDate = date, dueTime = time)
                tool = null
            },
            onClear = {
                state = s.copy(dueDate = null, dueTime = null, channel = CHANNEL_NOTIFY)
                tool = null
            },
            onDismiss = { tool = null },
        )
        "repeat" -> ToolDialog(
            title = "重复",
            options = listOf(
                "不重复" to { state = s.copy(repeatFreq = "none") },
                "每天" to { state = s.copy(repeatFreq = "day") },
                "每周" to { state = s.copy(repeatFreq = "week") },
                "每月" to { state = s.copy(repeatFreq = "month") },
                "每年" to { state = s.copy(repeatFreq = "year") },
            ),
            onDismiss = { tool = null },
            extra = {
                OutlinedTextField(
                    value = s.repeatN,
                    onValueChange = { state = s.copy(repeatN = it) },
                    label = { Text("间隔 1–999") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
        )
        "tag" -> ToolDialog(
            title = "标签（可多选）",
            options = tags.map { tag ->
                val mark = if (tag.id in s.tagIds) "✓ " else ""
                "$mark#${tag.name}" to {
                    state = s.copy(tagIds = if (tag.id in s.tagIds) s.tagIds - tag.id else s.tagIds + tag.id)
                }
            },
            onDismiss = { tool = null },
            closeOnSelect = false,
        )
        "priority" -> ToolDialog(
            title = "优先级",
            options = listOf(
                "无" to { state = s.copy(priority = 0) },
                "低" to { state = s.copy(priority = 1) },
                "中" to { state = s.copy(priority = 2) },
                "高" to { state = s.copy(priority = 3) },
            ),
            onDismiss = { tool = null },
        )
        "lead" -> ToolDialog(
            title = "提前提醒",
            options = listOf(
                "延后 1 分钟" to { state = s.copy(leadMinutes = -1) },
                "准点" to { state = s.copy(leadMinutes = 0) },
                "提前 5 分钟" to { state = s.copy(leadMinutes = 5) },
                "提前 15 分钟" to { state = s.copy(leadMinutes = 15) },
                "提前 30 分钟" to { state = s.copy(leadMinutes = 30) },
                "提前 60 分钟" to { state = s.copy(leadMinutes = 60) },
            ),
            onDismiss = { tool = null },
        )
        "channel" -> ToolDialog(
            title = "提醒通道",
            options = listOf(
                "应用提醒" to { state = s.copy(channel = CHANNEL_NOTIFY) },
                "系统日程" to {
                    state = s.copy(channel = CHANNEL_CALENDAR, durationMin = if (s.durationMin > 0) s.durationMin else 30)
                },
            ),
            onDismiss = { tool = null },
        )
        "duration" -> ToolDialog(
            title = "时长",
            options = listOf(15, 30, 60, 90, 120).map { minutes ->
                "$minutes 分钟" to { state = s.copy(durationMin = minutes) }
            },
            onDismiss = { tool = null },
        )
        "pinned" -> ToolDialog(
            title = "置顶",
            options = listOf(
                "置顶" to { vm.setPinned(entryId, true) },
                "取消置顶" to { vm.setPinned(entryId, false) },
            ),
            onDismiss = { tool = null },
        )
    }
}
