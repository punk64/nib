package com.yourname.nib.features.all

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourname.nib.NibViewModel
import com.yourname.nib.core.model.EntryFilter
import com.yourname.nib.core.model.splitByDone
import com.yourname.nib.core.organize.reorderIds
import com.yourname.nib.core.theme.LocalNibTokens
import com.yourname.nib.core.theme.tokenColor
import com.yourname.nib.widgets.BatchBar
import com.yourname.nib.widgets.EmptyHint
import com.yourname.nib.widgets.EntryList
import com.yourname.nib.widgets.FilterBar
import com.yourname.nib.widgets.NibHero
import com.yourname.nib.widgets.SectionTitle

@Composable
private fun Padded(content: @Composable () -> Unit) {
    Box(Modifier.padding(horizontal = 18.dp)) { content() }
}

@Composable
fun AllScreen(vm: NibViewModel, onOpen: (String) -> Unit) {
    val bundles by vm.bundles.collectAsStateWithLifecycle()
    val lists by vm.lists.collectAsStateWithLifecycle()
    val tags by vm.tags.collectAsStateWithLifecycle()
    val filter by vm.filterState.collectAsStateWithLifecycle()
    val query by vm.searchState.collectAsStateWithLifecycle()
    val showDone by vm.doneVisibleState.collectAsStateWithLifecycle()
    val selected by vm.selectedIds.collectAsStateWithLifecycle()
    val t = LocalNibTokens.current
    val (undone, done) = splitByDone(bundles)
    val byId = bundles.associateBy { it.entry.id }
    val orderedIds = undone.map { it.entry.id }

    fun move(id: String, delta: Int) {
        val from = orderedIds.indexOf(id)
        if (from < 0 || delta == 0) return
        val to = (from + delta).coerceIn(0, orderedIds.lastIndex)
        val target = reorderIds(orderedIds, from, to)
        val index = target.indexOf(id)
        val prev = if (index > 0) byId[target[index - 1]]?.entry?.sort else null
        val next = if (index < target.lastIndex) byId[target[index + 1]]?.entry?.sort else null
        vm.reorder(id, prev, next, target)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            NibHero(
                sub = "${undone.size} 项待办 · ${lists.size} 个清单",
                title = "全部",
            )
        }
        item {
            Padded {
                FilterBar(
                    lists = lists,
                    tags = tags,
                    filter = filter,
                    query = query,
                    onList = { vm.setFilter(filter.copy(listId = it)) },
                    onTag = { vm.toggleTag(it) },
                    onQuery = { vm.setSearch(it) },
                )
            }
        }
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
        item { Padded { SectionTitle("未完成", "${undone.size} 项") } }
        item {
            Padded {
                if (undone.isEmpty()) {
                    EmptyHint("这个筛选下没有条目")
                } else {
                    EntryList(
                        vm = vm,
                        items = undone,
                        selected = selected,
                        onOpen = onOpen,
                        draggable = true,
                        onMove = { id, delta -> move(id, delta) },
                    )
                }
            }
        }
        item {
            Padded {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { vm.toggleDoneVisible() }
                        .padding(start = 4.dp, top = 16.dp, bottom = 9.dp),
                ) {
                    Text(
                        text = "已完成 ${done.size}  ${if (showDone) "收起" else "展开"}",
                        color = tokenColor(t.text3),
                        fontSize = 11.sp,
                    )
                }
            }
        }
        if (showDone) {
            item {
                Padded {
                    EntryList(
                        vm = vm,
                        items = done,
                        selected = selected,
                        onOpen = onOpen,
                    )
                }
            }
        }
        item { Spacer(Modifier.height(104.dp)) }
    }
}
