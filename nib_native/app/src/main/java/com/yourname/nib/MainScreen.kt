package com.yourname.nib

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourname.nib.core.theme.LocalNibTokens
import com.yourname.nib.core.theme.NibBackdrop
import com.yourname.nib.core.theme.NibBackdropStamp
import com.yourname.nib.core.theme.NibBeam
import com.yourname.nib.core.theme.NibTheme
import com.yourname.nib.core.theme.tokenColor
import com.yourname.nib.features.all.AllScreen
import com.yourname.nib.features.calendar.CalendarScreen
import com.yourname.nib.features.editor.EntryDetailScreen
import com.yourname.nib.features.editor.QuickCaptureScreen
import com.yourname.nib.features.me.MeScreen
import com.yourname.nib.features.me.PermissionScreen
import com.yourname.nib.features.today.TodayScreen
import com.yourname.nib.widgets.CaptureFab
import com.yourname.nib.widgets.NibTabBar
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@Composable
fun NibApp(vm: NibViewModel, initialTab: String = "today", initialDetailId: String? = null) {
    val theme by vm.theme.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    var capture by rememberSaveable { mutableStateOf(false) }
    var detailId by rememberSaveable { mutableStateOf(initialDetailId) }
    var permissions by rememberSaveable { mutableStateOf(false) }
    val stacked = detailId != null || capture || permissions || tab != "today"
    BackHandler(enabled = stacked) {
        when {
            detailId != null -> detailId = null
            capture -> capture = false
            permissions -> {
                permissions = false
                vm.onForeground()
            }
            else -> tab = "today"
        }
    }
    val view = LocalView.current
    val activity = LocalContext.current as? Activity
    NibTheme(theme) {
        val t = LocalNibTokens.current
        SideEffect {
            if (activity != null) {
                val controller = WindowCompat.getInsetsController(activity.window, view)
                controller.isAppearanceLightStatusBars = !theme.dark
                controller.isAppearanceLightNavigationBars = !theme.dark
            }
        }
        Surface(modifier = Modifier.fillMaxSize(), color = tokenColor(t.bg)) {
            Box(Modifier.fillMaxSize()) {
                when {
                    detailId != null -> Box(Modifier.fillMaxSize()) {
                        NibBackdrop()
                        NibBeam()
                        NibBackdropStamp()
                        SafeArea { EntryDetailScreen(vm, detailId!!) { detailId = null } }
                    }
                    capture -> Box(Modifier.fillMaxSize()) {
                        NibBackdrop()
                        NibBeam()
                        NibBackdropStamp()
                        SafeArea { QuickCaptureScreen(vm) { capture = false } }
                    }
                    permissions -> Box(Modifier.fillMaxSize()) {
                        NibBackdrop()
                        NibBeam()
                        NibBackdropStamp()
                        SafeArea {
                            PermissionScreen {
                                permissions = false
                                vm.onForeground()
                            }
                        }
                    }
                    else -> MainScaffold(
                        vm = vm,
                        tab = tab,
                        onTab = { tab = it },
                        onCapture = { capture = true },
                        onOpen = { detailId = it },
                        onPermissions = { permissions = true },
                    )
                }
                DeleteConfirmDialog(vm)
            }
        }
    }
}

@Composable
private fun SafeArea(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) { content() }
}

@Composable
private fun DeleteConfirmDialog(vm: NibViewModel) {
    val request by vm.deleteRequest.collectAsStateWithLifecycle()
    val req = request ?: return
    val hasEvents = req.eventCount > 0
    AlertDialog(
        onDismissRequest = { vm.cancelDelete() },
        title = {
            Text(if (req.ids.size == 1) "删除这条记录？" else "删除 ${req.ids.size} 条记录？")
        },
        text = {
            Text(
                if (hasEvents) {
                    "其中 ${req.eventCount} 条已写入系统日历，是否同时删除系统日历中的对应日程？"
                } else {
                    "删除后不再出现在列表中。"
                },
            )
        },
        confirmButton = {
            TextButton(onClick = { vm.confirmDelete(true) }) {
                Text(if (hasEvents) "同时删日程" else "删除")
            }
        },
        dismissButton = {
            Row {
                if (hasEvents) {
                    TextButton(onClick = { vm.confirmDelete(false) }) { Text("仅删记录") }
                }
                TextButton(onClick = { vm.cancelDelete() }) { Text("取消") }
            }
        },
    )
}

@Composable
private fun MainScaffold(
    vm: NibViewModel,
    tab: String,
    onTab: (String) -> Unit,
    onCapture: () -> Unit,
    onOpen: (String) -> Unit,
    onPermissions: () -> Unit,
) {
    val hazeState = rememberHazeState()
    Box(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState),
        ) {
            NibBackdrop()
            NibBeam()
            NibBackdropStamp()
            Box(Modifier.fillMaxSize().statusBarsPadding()) {
                when (tab) {
                    "today" -> TodayScreen(vm, onOpen)
                    "all" -> AllScreen(vm, onOpen)
                    "calendar" -> CalendarScreen(vm, onOpen, onPermissions)
                    "me" -> MeScreen(vm, onPermissions)
                }
            }
        }
        NibTabBar(
            hazeState = hazeState,
            tab = tab,
            onTab = onTab,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        CaptureFab(onCapture)
    }
}
