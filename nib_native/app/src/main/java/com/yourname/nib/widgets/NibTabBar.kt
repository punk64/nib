package com.yourname.nib.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourname.nib.core.theme.LocalNibTokens
import com.yourname.nib.core.theme.nibFontFamily
import com.yourname.nib.core.theme.tokenColor
import com.yourname.nib.core.theme.withAlpha
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurDefaults
import dev.chrisbanes.haze.blur.hazeBlur

private data class TabItem(val key: String, val label: String, val icon: ImageVector)

private val nibTabs = listOf(
    TabItem("today", "今日", Icons.Outlined.Home),
    TabItem("all", "全部", Icons.AutoMirrored.Outlined.List),
    TabItem("calendar", "日历", Icons.Outlined.DateRange),
    TabItem("me", "我的", Icons.Outlined.Settings),
)

val nibTabBarHeight = 58.dp
val nibTabBarBottomGap = 10.dp

private const val SIGMA_FULL = 20f
private const val SIGMA_LITE = 11f

@Composable
private fun rememberAdaptiveSigma(enabled: Boolean): Float {
    var sigma by remember(enabled) { mutableStateOf(if (enabled) SIGMA_FULL else 0f) }
    LaunchedEffect(enabled) {
        if (!enabled) {
            sigma = 0f
            return@LaunchedEffect
        }
        var last = withFrameNanos { it }
        var slow = 0
        var frames = 0
        while (true) {
            val now = withFrameNanos { it }
            val dt = (now - last) / 1_000_000f
            last = now
            frames++
            if (dt > 28f) slow++
            if (frames >= 30) {
                if (slow >= 16 && sigma > SIGMA_LITE) sigma = SIGMA_LITE
                frames = 0
                slow = 0
            }
        }
    }
    return sigma
}

@Composable
fun NibTabBar(
    hazeState: HazeState?,
    tab: String,
    onTab: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalNibTokens.current
    val glassEnabled = (t.styleId == "v1" || t.styleId == "v2") && hazeState != null
    val sigma = rememberAdaptiveSigma(glassEnabled)
    val shape = CircleShape
    val glow = remember { mutableStateOf<Offset?>(null) }
    val accent = tokenColor(t.accent)
    val borderBrush = Brush.verticalGradient(
        listOf(
            tokenColor(withAlpha(0xFFFFFFFFL, if (t.dark) 66 else 150)),
            tokenColor(withAlpha(0xFFFFFFFFL, if (t.dark) 18 else 60)),
        ),
    )
    val style = remember(sigma) {
        HazeBlurDefaults.style.then {
            blurRadius(sigma.dp)
            alpha(1f)
            noiseFactor(0.02f)
        }
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = nibTabBarBottomGap),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(nibTabBarHeight)
                .clip(shape)
                .then(
                    if (glassEnabled) {
                        Modifier
                            .hazeBlur(HazeInput.Sources(hazeState!!), style)
                            .background(tokenColor(withAlpha(t.bg, 18)))
                    } else {
                        Modifier.background(tokenColor(t.card))
                    },
                )
                .border(1.dp, borderBrush, shape)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val position = event.changes.firstOrNull()?.position
                            glow.value = position?.let {
                                Offset(
                                    it.x.coerceIn(0f, size.width.toFloat()),
                                    it.y.coerceIn(0f, size.height.toFloat()),
                                )
                            }
                            if (event.changes.all { it.pressed.not() }) {
                                glow.value = null
                            }
                        }
                    }
                },
        ) {
            Row(Modifier.fillMaxWidth().height(nibTabBarHeight)) {
                nibTabs.forEach { item ->
                    val on = tab == item.key
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onTab(item.key) }
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(46.dp)
                                .height(25.dp)
                                .clip(CircleShape)
                                .background(if (on) tokenColor(t.accentWeak) else Color.Transparent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint = if (on) accent else tokenColor(t.text3),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Text(
                            text = item.label,
                            color = if (on) accent else tokenColor(t.text3),
                            fontSize = 10.5.sp,
                            fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
                            fontFamily = nibFontFamily(),
                            modifier = Modifier.padding(top = 1.dp),
                        )
                    }
                }
            }
            Canvas(Modifier.matchParentSize()) {
                val center = glow.value ?: return@Canvas
                val radius = (size.height + 10.dp.toPx()) * 2f
                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0f to accent.copy(alpha = 0.34f),
                            0.08f to accent.copy(alpha = 0.22f),
                            0.26f to accent.copy(alpha = 0.11f),
                            0.56f to accent.copy(alpha = 0.035f),
                            1f to Color.Transparent,
                        ),
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                    center = center,
                )
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.035f),
                            Color.Transparent,
                        ),
                        startY = 0f,
                        endY = size.height * 0.55f,
                    ),
                )
            }
        }
    }
}
