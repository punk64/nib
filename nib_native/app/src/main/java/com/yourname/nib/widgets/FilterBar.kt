package com.yourname.nib.widgets

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yourname.nib.core.db.NoteList
import com.yourname.nib.core.db.Tag
import com.yourname.nib.core.model.EntryFilter
import com.yourname.nib.core.theme.LocalNibTokens
import com.yourname.nib.core.theme.tokenColor

@Composable
fun FilterBar(
    lists: List<NoteList>,
    tags: List<Tag>,
    filter: EntryFilter,
    query: String,
    onList: (String) -> Unit,
    onTag: (String) -> Unit,
    onQuery: (String) -> Unit,
) {
    val t = LocalNibTokens.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            placeholder = { Text("搜索标题、备注、标签", color = tokenColor(t.text3)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            NibChip("全部", filter.listId == EntryFilter.ALL_LISTS) { onList(EntryFilter.ALL_LISTS) }
            NibChip("未分类", filter.listId == EntryFilter.NO_LIST) { onList(EntryFilter.NO_LIST) }
            lists.forEach { list ->
                NibChip(list.name, filter.listId == list.id) { onList(list.id) }
            }
        }
        if (tags.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                tags.forEach { tag ->
                    NibChip("#${tag.name}", tag.id in filter.tagIds) { onTag(tag.id) }
                }
            }
        }
    }
}
