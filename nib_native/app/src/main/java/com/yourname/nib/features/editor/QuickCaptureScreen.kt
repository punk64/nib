package com.yourname.nib.features.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourname.nib.NibViewModel
import com.yourname.nib.core.calendar.CHANNEL_CALENDAR
import com.yourname.nib.core.calendar.CHANNEL_NOTIFY
import com.yourname.nib.core.calendar.suggestChannel
import com.yourname.nib.core.model.CaptureDraft
import com.yourname.nib.core.model.clampRepeatN
import com.yourname.nib.core.model.draftFromRaw
import com.yourname.nib.core.model.repeatLabel
import com.yourname.nib.core.theme.LocalNibTokens
import com.yourname.nib.core.theme.nibFontFamily
import com.yourname.nib.core.theme.tokenColor
import com.yourname.nib.core.time.NibDate
import com.yourname.nib.core.time.parseDateKey
import com.yourname.nib.widgets.NibActionPill
import com.yourname.nib.widgets.NibChip
import com.yourname.nib.widgets.NibDateTimeDialog
import com.yourname.nib.widgets.NibHero
import com.yourname.nib.widgets.NibIconButton

private data class CaptureOverrides(
    val dueDate: String? = null,
    val dueTime: String? = null,
    val priority: Int? = null,
    val repeatFreq: String? = null,
    val repeatN: Int? = null,
)

private fun relativeDay(key: String): String {
    val date = parseDateKey(key) ?: return key
    return when (date.epochDay - NibDate.today().epochDay) {
        -3L -> "大前天"
        -2L -> "前天"
        -1L -> "昨天"
        0L -> "今天"
        1L -> "明天"
        2L -> "后天"
        3L -> "大后天"
        else -> key
    }
}

@Composable
fun ToolDialog(
    title: String,
    options: List<Pair<String, () -> Unit>>,
    onDismiss: () -> Unit,
    closeOnSelect: Boolean = true,
    extra: @Composable () -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                extra()
                options.forEach { (label, action) ->
                    TextButton(
                        onClick = {
                            action()
                            if (closeOnSelect) onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(label) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun QuickCaptureScreen(vm: NibViewModel, onClose: () -> Unit) {
    val lists by vm.lists.collectAsStateWithLifecycle()
    val tags by vm.tags.collectAsStateWithLifecycle()
    val t = LocalNibTokens.current
    var raw by rememberSaveable { mutableStateOf("") }
    var listId by rememberSaveable { mutableStateOf<String?>(null) }
    var tagIds by remember { mutableStateOf(setOf<String>()) }
    var overrides by remember { mutableStateOf(CaptureOverrides()) }
    var tool by remember { mutableStateOf<String?>(null) }
    var interval by rememberSaveable { mutableStateOf("1") }
    var leadMinutes by rememberSaveable { mutableStateOf(30) }
    var channel by rememberSaveable { mutableStateOf<String?>(null) }

    val base = draftFromRaw(raw)
    val overriddenDate = overrides.dueDate
    val overriddenTime = overrides.dueTime
    val resolvedDate = when {
        overriddenDate == null -> base.dueDate
        overriddenDate.isEmpty() -> null
        else -> overriddenDate
    }
    val resolvedTime = if (overriddenTime == null) base.dueTime else overriddenTime.ifEmpty { null }
    val resolvedChannel = channel ?: suggestChannel(resolvedTime, 30)
    val draft = base.copy(
        dueDate = resolvedDate,
        dueTime = resolvedTime,
        listId = listId,
        tagIds = tagIds,
        priority = overrides.priority ?: base.priority,
        repeatFreq = overrides.repeatFreq ?: base.repeatFreq,
        repeatN = overrides.repeatN ?: base.repeatN,
        leadMinutes = leadMinutes,
        channel = resolvedChannel,
        durationMin = if (resolvedChannel == CHANNEL_CALENDAR) 30 else 0,
    )
    val chips = buildList {
        add(if (draft.channel == CHANNEL_CALENDAR) "系统日程" else "应用提醒")
        if (draft.dueDate == null) add("未排期") else {
            add(relativeDay(draft.dueDate) + (draft.dueTime?.let { time -> " $time" } ?: ""))
        }
        if (draft.repeatFreq != "none") add(repeatLabel(draft.repeatFreq, draft.repeatN))
        if (draft.priority > 0) add(listOf("", "低", "中", "高")[draft.priority] + "优先级")
        listId?.let { id -> lists.firstOrNull { it.id == id }?.name?.let { add(it) } }
        base.newTagNames.forEach { add("#$it") }
        tagIds.forEach { id -> tags.firstOrNull { it.id == id }?.name?.let { add("#$it") } }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 8.dp),
    ) {
        NibHero(
            sub = if (draft.raw.isBlank()) "一句话，解析器替你拆好" else "${draft.raw.length} 字 · 自动解析",
            title = "速记",
            actions = {
                NibIconButton(Icons.AutoMirrored.Filled.ArrowBack) { onClose() }
                NibActionPill("保存", t.accent) {
                    if (draft.raw.isNotBlank()) vm.saveDraft(draft)
                    onClose()
                }
            },
        )
        Column(modifier = Modifier.padding(horizontal = 18.dp).weight(1f)) {
        OutlinedTextField(
            value = raw,
            onValueChange = { raw = it },
            placeholder = { Text("随手记一句，例如：明天10点 交周报 #工作 !!", color = tokenColor(t.text3)) },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            minLines = 4,
        )
        Spacer(Modifier.padding(vertical = 6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            chips.forEach { NibChip(it, selected = true) }
        }
        Spacer(Modifier.padding(vertical = 6.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("清单", "日期", "重复", "标签", "优先级", "提醒", "通道").forEach { label ->
                TextButton(
                    onClick = { tool = label },
                    modifier = Modifier.weight(1f),
                ) { Text(label, color = tokenColor(t.text2), fontSize = 11.sp, fontFamily = nibFontFamily()) }
            }
        }
        }
    }

    when (tool) {
        "清单" -> ToolDialog(
            title = "清单",
            options = listOf("未分类" to { listId = null }) + lists.map { list ->
                list.name to { listId = list.id }
            },
            onDismiss = { tool = null },
        )
        "日期" -> NibDateTimeDialog(
            initialDate = draft.dueDate,
            initialTime = draft.dueTime,
            onPick = { date, time ->
                overrides = overrides.copy(dueDate = date, dueTime = time)
                tool = null
            },
            onClear = {
                overrides = overrides.copy(dueDate = "", dueTime = "")
                tool = null
            },
            onDismiss = { tool = null },
        )
        "重复" -> ToolDialog(
            title = "重复",
            options = listOf(
                "不重复" to { overrides = overrides.copy(repeatFreq = "none") },
                "每天" to { overrides = overrides.copy(repeatFreq = "day", repeatN = clampRepeatN(interval)) },
                "每周" to { overrides = overrides.copy(repeatFreq = "week", repeatN = clampRepeatN(interval)) },
                "每月" to { overrides = overrides.copy(repeatFreq = "month", repeatN = clampRepeatN(interval)) },
                "每年" to { overrides = overrides.copy(repeatFreq = "year", repeatN = clampRepeatN(interval)) },
            ),
            onDismiss = { tool = null },
            extra = {
                OutlinedTextField(
                    value = interval,
                    onValueChange = {
                        interval = it
                        overrides = overrides.copy(repeatN = clampRepeatN(it))
                    },
                    label = { Text("间隔 1–999") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
        )
        "标签" -> ToolDialog(
            title = "标签（可多选）",
            options = tags.map { tag ->
                val mark = if (tag.id in tagIds) "✓ " else ""
                "$mark#${tag.name}" to {
                    tagIds = if (tag.id in tagIds) tagIds - tag.id else tagIds + tag.id
                }
            },
            onDismiss = { tool = null },
            closeOnSelect = false,
        )
        "优先级" -> ToolDialog(
            title = "优先级",
            options = listOf(
                "无" to { overrides = overrides.copy(priority = 0) },
                "低" to { overrides = overrides.copy(priority = 1) },
                "中" to { overrides = overrides.copy(priority = 2) },
                "高" to { overrides = overrides.copy(priority = 3) },
            ),
            onDismiss = { tool = null },
        )
        "提醒" -> ToolDialog(
            title = "提前提醒",
            options = listOf(
                "延后 1 分钟" to { leadMinutes = -1 },
                "准点" to { leadMinutes = 0 },
                "提前 5 分钟" to { leadMinutes = 5 },
                "提前 15 分钟" to { leadMinutes = 15 },
                "提前 30 分钟" to { leadMinutes = 30 },
                "提前 60 分钟" to { leadMinutes = 60 },
            ),
            onDismiss = { tool = null },
        )
        "通道" -> ToolDialog(
            title = "提醒通道",
            options = listOf(
                "自动" to { channel = null },
                "应用提醒" to { channel = CHANNEL_NOTIFY },
                "系统日程" to { channel = CHANNEL_CALENDAR },
            ),
            onDismiss = { tool = null },
        )
    }
}
