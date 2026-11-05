package com.yourname.nib.widgets

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.yourname.nib.core.theme.LocalNibTokens
import com.yourname.nib.core.theme.tokenColor
import com.yourname.nib.core.theme.withAlpha
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val fabTouchSize = 64.dp
private val fabFullSize = 54.dp
private val fabDockWidth = 10.dp
private val fabDockHeight = 44.dp
private val fabSideGap = 20.dp
private const val fabIdleMillis = 1500L

@Composable
fun CaptureFab(onClick: () -> Unit) {
    val t = LocalNibTokens.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val fullPx = with(density) { fabFullSize.toPx() }
        val dockW = with(density) { fabDockWidth.toPx() }
        val dockH = with(density) { fabDockHeight.toPx() }
        val touchHalf = with(density) { fabTouchSize.toPx() } / 2f
        val topLimit = with(density) { (statusTop + 4.dp).toPx() }
        val bottomLimit = heightPx - with(density) { (navBottom + nibTabBarHeight + nibTabBarBottomGap * 2 + 12.dp).toPx() }
        val side = with(density) { fabSideGap.toPx() }

        var docked by remember { mutableStateOf(false) }
        var dragging by remember { mutableStateOf(false) }
        var wake by remember { mutableStateOf(0) }
        var dockJob by remember { mutableStateOf<Job?>(null) }
        val pos = remember {
            Animatable(Offset(widthPx - fullPx / 2 - side, (bottomLimit + topLimit) / 2f), Offset.VectorConverter)
        }

        fun dockNow() {
            val targetX = if (pos.value.x <= widthPx / 2) dockW / 2 else widthPx - dockW / 2
            val targetY = pos.value.y.coerceIn(topLimit + dockH / 2, bottomLimit - dockH / 2)
            docked = true
            dockJob?.cancel()
            dockJob = scope.launch {
                pos.animateTo(
                    Offset(targetX, targetY),
                    spring(dampingRatio = 0.42f, stiffness = 380f),
                )
            }
        }

        val widthAnim by animateDpAsState(
            if (docked) fabDockWidth else fabFullSize,
            spring(dampingRatio = 0.55f, stiffness = 520f),
        )
        val heightAnim by animateDpAsState(
            if (docked) fabDockHeight else fabFullSize,
            spring(dampingRatio = 0.55f, stiffness = 520f),
        )
        val radiusAnim by animateDpAsState(
            when {
                docked -> fabDockWidth / 2
                dragging -> fabFullSize / 2
                else -> 19.dp
            },
            spring(dampingRatio = 0.38f, stiffness = 620f),
        )
        val scaleAnim by animateFloatAsState(
            if (dragging) 1.07f else 1f,
            spring(dampingRatio = 0.36f, stiffness = 680f),
        )
        val liftAnim by animateDpAsState(
            if (dragging) 22.dp else 14.dp,
            spring(dampingRatio = 0.5f, stiffness = 520f),
        )
        val iconAlpha by animateFloatAsState(if (docked) 0f else 1f, spring(stiffness = 600f))
        val shape = RoundedCornerShape(radiusAnim)

        LaunchedEffect(dragging, wake) {
            if (dragging) return@LaunchedEffect
            delay(fabIdleMillis)
            if (!docked) dockNow()
        }

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        (pos.value.x - touchHalf).roundToInt(),
                        (pos.value.y - touchHalf).roundToInt(),
                    )
                }
                .size(fabTouchSize)
                .pointerInput(Unit) {
                    detectTapGestures {
                        if (docked) {
                            docked = false
                            dockJob?.cancel()
                            wake++
                            val backX = if (pos.value.x <= widthPx / 2) fullPx / 2 + side else widthPx - fullPx / 2 - side
                            dockJob = scope.launch {
                                pos.animateTo(
                                    Offset(backX, pos.value.y),
                                    spring(dampingRatio = 0.55f, stiffness = 420f),
                                )
                            }
                        } else {
                            onClick()
                        }
                    }
                }
                .pointerInput(widthPx, heightPx) {
                    detectDragGestures(
                        onDragStart = {
                            dockJob?.cancel()
                            dragging = true
                            docked = false
                            wake++
                        },
                        onDragEnd = {
                            dragging = false
                            wake++
                        },
                        onDragCancel = {
                            dragging = false
                            wake++
                        },
                    ) { change, drag ->
                        change.consume()
                        val halfW = fullPx / 2
                        val halfH = fullPx / 2
                        val next = Offset(
                            (pos.value.x + drag.x).coerceIn(halfW, widthPx - halfW),
                            (pos.value.y + drag.y).coerceIn(topLimit + halfH, bottomLimit - halfH),
                        )
                        scope.launch { pos.snapTo(next) }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(widthAnim, heightAnim)
                    .scale(scaleAnim)
                    .shadow(liftAnim, shape)
                    .clip(shape)
                    .background(Brush.linearGradient(listOf(tokenColor(t.accent), tokenColor(t.accent2))))
                    .border(1.dp, tokenColor(withAlpha(0xFFFFFFFFL, 46)), shape)
                    .alpha(if (docked) 0.92f else 1f),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp).alpha(iconAlpha),
                )
            }
        }
    }
}
