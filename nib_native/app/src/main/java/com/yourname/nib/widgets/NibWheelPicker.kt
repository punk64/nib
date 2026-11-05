package com.yourname.nib.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yourname.nib.core.theme.LocalNibTokens
import com.yourname.nib.core.theme.nibFontFamily
import com.yourname.nib.core.theme.tokenColor
import com.yourname.nib.core.theme.withAlpha
import com.yourname.nib.core.time.ClockTime
import com.yourname.nib.core.time.NibDate
import com.yourname.nib.core.time.clampDay
import com.yourname.nib.core.time.daysInMonth
import com.yourname.nib.core.time.parseDateKey
import com.yourname.nib.core.time.parseTimeKey
import com.yourname.nib.core.time.twoDigits
import kotlinx.coroutines.launch
import java.util.Calendar

private val wheelItemHeight = 38.dp
private const val yearSpan = 26

private fun currentClock(): ClockTime {
    val cal = Calendar.getInstance()
    return ClockTime(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
}

@Composable
private fun WheelColumn(
    count: Int,
    state: LazyListState,
    label: (Int) -> String,
    modifier: Modifier = Modifier,
) {
    val t = LocalNibTokens.current
    val scope = rememberCoroutineScope()
    val selected by remember { derivedStateOf { state.firstVisibleItemIndex } }
    LazyColumn(
        state = state,
        modifier = modifier.height(wheelItemHeight * 5),
        contentPadding = PaddingValues(vertical = wheelItemHeight * 2),
        flingBehavior = rememberSnapFlingBehavior(state),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items(count) { index ->
            val active = index == selected
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(wheelItemHeight)
                    .clickable { scope.launch { state.animateScrollToItem(index) } },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(index),
                    color = tokenColor(if (active) t.text else t.text3),
                    fontSize = if (active) 19.sp else 14.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    fontFamily = nibFontFamily(),
                )
            }
        }
    }
}

@Composable
fun NibDateTimeDialog(
    initialDate: String?,
    initialTime: String?,
    onPick: (dateKey: String, timeKey: String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val t = LocalNibTokens.current
    val today = NibDate.today()
    val startYear = today.year - 5
    val seed = parseDateKey(initialDate) ?: today
    val clock = parseTimeKey(initialTime) ?: currentClock()

    val yearState = rememberLazyListState(initialFirstVisibleItemIndex = (seed.year - startYear).coerceIn(0, yearSpan - 1))
    val monthState = rememberLazyListState(initialFirstVisibleItemIndex = seed.month - 1)
    val dayState = rememberLazyListState(initialFirstVisibleItemIndex = seed.day - 1)
    val hourState = rememberLazyListState(initialFirstVisibleItemIndex = clock.hour)
    val minuteState = rememberLazyListState(initialFirstVisibleItemIndex = clock.minute)

    val year by remember { derivedStateOf { startYear + yearState.firstVisibleItemIndex } }
    val month by remember { derivedStateOf { monthState.firstVisibleItemIndex + 1 } }
    val day by remember { derivedStateOf { dayState.firstVisibleItemIndex + 1 } }
    val hour by remember { derivedStateOf { hourState.firstVisibleItemIndex } }
    val minute by remember { derivedStateOf { minuteState.firstVisibleItemIndex } }
    val dayCount = daysInMonth(year, month)

    LaunchedEffect(year, month) {
        if (dayState.firstVisibleItemIndex > dayCount - 1) dayState.scrollToItem(dayCount - 1)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .background(tokenColor(t.card), RoundedCornerShape(t.radiusSheet.dp))
                .border(1.dp, tokenColor(withAlpha(t.line2, 120)), RoundedCornerShape(t.radiusSheet.dp))
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            Text(
                text = "日期与时间",
                color = tokenColor(t.text),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = nibFontFamily(),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "上下滑动选择 · 不选日期即归入未排期",
                color = tokenColor(t.text3),
                fontSize = 11.sp,
                fontFamily = nibFontFamily(),
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                listOf("年" to 1.35f, "月" to 1f, "日" to 1f, "时" to 1f, "分" to 1f).forEach { (label, weight) ->
                    Text(
                        text = label,
                        modifier = Modifier.weight(weight),
                        color = tokenColor(t.text3),
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                        fontFamily = nibFontFamily(),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Box(modifier = Modifier.fillMaxWidth().height(wheelItemHeight * 5)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    WheelColumn(yearSpan, yearState, { (startYear + it).toString() }, Modifier.weight(1.35f))
                    WheelColumn(12, monthState, { twoDigits(it + 1) }, Modifier.weight(1f))
                    WheelColumn(dayCount, dayState, { twoDigits(it + 1) }, Modifier.weight(1f))
                    WheelColumn(24, hourState, { twoDigits(it) }, Modifier.weight(1f))
                    WheelColumn(60, minuteState, { twoDigits(it) }, Modifier.weight(1f))
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .height(wheelItemHeight)
                        .padding(horizontal = 2.dp)
                        .border(1.dp, tokenColor(withAlpha(t.accent, 110)), RoundedCornerShape(12.dp)),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(wheelItemHeight * 1.5f)
                        .background(
                            Brush.verticalGradient(
                                listOf(tokenColor(t.card), tokenColor(withAlpha(t.card, 0))),
                            ),
                        ),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(wheelItemHeight * 1.5f)
                        .background(
                            Brush.verticalGradient(
                                listOf(tokenColor(withAlpha(t.card, 0)), tokenColor(t.card)),
                            ),
                        ),
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onClear) {
                    Text("未排期", color = tokenColor(t.danger), fontSize = 13.sp, fontFamily = nibFontFamily())
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text("取消", color = tokenColor(t.text2), fontSize = 13.sp, fontFamily = nibFontFamily())
                }
                TextButton(onClick = {
                    val picked = NibDate(year, month, clampDay(year, month, day))
                    onPick(picked.dateKey(), "${twoDigits(hour)}:${twoDigits(minute)}")
                }) {
                    Text("确定", color = tokenColor(t.accent), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = nibFontFamily())
                }
            }
        }
    }
}
