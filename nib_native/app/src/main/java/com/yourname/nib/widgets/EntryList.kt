package com.yourname.nib.widgets

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.yourname.nib.NibViewModel
import com.yourname.nib.core.model.EntryBundle
import com.yourname.nib.core.theme.LocalNibTokens
import com.yourname.nib.core.theme.tokenColor
import kotlin.math.roundToInt

@Composable
private fun DragHandle(onDragEnd: (Float) -> Unit) {
    val t = LocalNibTokens.current
    var offset by remember { mutableFloatStateOf(0f) }
    Box(
        modifier = Modifier
            .size(34.dp)
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset = 0f },
                    onDragCancel = { offset = 0f },
                    onDragEnd = {
                        onDragEnd(offset)
                        offset = 0f
                    },
                ) { change, dragAmount ->
                    change.consume()
                    offset += dragAmount.y
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.Menu, contentDescription = null, tint = tokenColor(t.text3), modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun RowItem(
    vm: NibViewModel,
    bundle: EntryBundle,
    index: Int,
    selected: Set<String>,
    onOpen: (String) -> Unit,
    draggable: Boolean,
    onMove: (String, Int) -> Unit,
    contained: Boolean,
    rowHeight: Float,
) {
    val id = bundle.entry.id
    val handle: (@Composable () -> Unit)? = if (draggable) {
        {
            DragHandle { offset -> onMove(id, (offset / rowHeight).roundToInt()) }
        }
    } else {
        null
    }
    EntryRow(
        bundle = bundle,
        onToggle = { vm.setDone(id, bundle.entry.done == 0) },
        onOpen = { if (selected.isNotEmpty()) vm.toggleSelect(id) else onOpen(id) },
        selected = id in selected,
        onLongClick = { vm.toggleSelect(id) },
        trailing = handle,
        contained = contained,
        index = index,
    )
}

@Composable
private fun Rows(
    vm: NibViewModel,
    items: List<EntryBundle>,
    selected: Set<String>,
    onOpen: (String) -> Unit,
    draggable: Boolean,
    onMove: (String, Int) -> Unit,
    contained: Boolean,
    rowHeight: Float,
) {
    items.forEachIndexed { index, bundle ->
        RowItem(vm, bundle, index, selected, onOpen, draggable, onMove, contained, rowHeight)
        if (contained && index < items.lastIndex) NibRowDivider()
    }
}

@Composable
fun EntryList(
    vm: NibViewModel,
    items: List<EntryBundle>,
    selected: Set<String>,
    onOpen: (String) -> Unit,
    draggable: Boolean = false,
    onMove: (String, Int) -> Unit = { _, _ -> },
) {
    val t = LocalNibTokens.current
    val rowHeight = with(LocalDensity.current) { 52.dp.toPx() }
    when (t.styleId) {
        "v3b" -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items.chunked(2).forEachIndexed { rowIndex, pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    pair.forEachIndexed { column, bundle ->
                        Box(Modifier.weight(1f)) {
                            RowItem(vm, bundle, rowIndex * 2 + column, selected, onOpen, draggable, onMove, false, rowHeight)
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        "v3a", "v3c" -> Column(verticalArrangement = Arrangement.spacedBy(if (t.styleId == "v3a") 16.dp else 3.dp)) {
            Rows(vm, items, selected, onOpen, draggable, onMove, contained = false, rowHeight = rowHeight)
        }
        else -> NibCard {
            Rows(vm, items, selected, onOpen, draggable, onMove, contained = true, rowHeight = rowHeight)
        }
    }
}
