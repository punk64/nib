package com.yourname.nib.widgets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourname.nib.core.db.NoteList
import com.yourname.nib.core.db.Tag
import com.yourname.nib.core.theme.LocalNibTokens
import com.yourname.nib.core.theme.tokenColor

@Composable
private fun ChoiceDialog(
    title: String,
    labels: List<String>,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                labels.forEachIndexed { index, label ->
                    TextButton(onClick = { onPick(index) }, modifier = Modifier.fillMaxWidth()) {
                        Text(label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun BatchBar(
    count: Int,
    lists: List<NoteList>,
    tags: List<Tag>,
    onList: (String?) -> Unit,
    onTag: (String) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    val t = LocalNibTokens.current
    var dialog by remember { mutableStateOf<String?>(null) }
    NibCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("已选 $count 条", color = tokenColor(t.text), fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { dialog = "list" }) { Text("改清单", fontSize = 12.sp) }
            TextButton(onClick = { dialog = "tag" }) { Text("加标签", fontSize = 12.sp) }
            TextButton(onClick = onDelete) { Text("删除", fontSize = 12.sp, color = tokenColor(t.danger)) }
            TextButton(onClick = onCancel) { Text("取消", fontSize = 12.sp) }
        }
    }
    when (dialog) {
        "list" -> ChoiceDialog(
            title = "批量改清单",
            labels = listOf("未分类") + lists.map { it.name },
            onPick = { index ->
                onList(if (index == 0) null else lists[index - 1].id)
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        "tag" -> ChoiceDialog(
            title = "批量加标签",
            labels = tags.map { "#${it.name}" },
            onPick = { index ->
                if (index < tags.size) onTag(tags[index].id)
                dialog = null
            },
            onDismiss = { dialog = null },
        )
    }
}
