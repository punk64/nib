package com.yourname.nib

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yourname.nib.core.CrashCatcher
import com.yourname.nib.core.calendar.CalendarBridge
import com.yourname.nib.core.capture.EXTRA_NIB_ENTRY_ID
import com.yourname.nib.core.capture.EXTRA_NIB_TAB
import com.yourname.nib.core.db.NibGraph
import com.yourname.nib.core.db.NibRepository
import com.yourname.nib.core.reminder.ReminderBootstrap
import com.yourname.nib.core.sync.SyncCursor
import com.yourname.nib.core.theme.ThemeStore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { enableEdgeToEdge() }
        runCatching { CrashCatcher.install(applicationContext) }
        val previous = if (savedInstanceState == null) CrashCatcher.unread(this) else null
        if (previous != null) {
            showReport(previous)
            return
        }
        runCatching { start() }.onFailure { error ->
            CrashCatcher.record(error)
            showReport(Log.getStackTraceString(error))
        }
    }

    private fun start() {
        val db = NibGraph.db(applicationContext)
        val repo = NibRepository(db)
        val bridge = CalendarBridge(applicationContext, repo)
        ReminderBootstrap.reschedule(applicationContext)
        setContent {
            val vm: NibViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        NibViewModel(
                            repo,
                            ThemeStore(applicationContext),
                            { ReminderBootstrap.reschedule(applicationContext) },
                            bridge,
                            SyncCursor(applicationContext),
                        )
                    }
                },
            )
            val owner = LocalLifecycleOwner.current
            DisposableEffect(owner, vm) {
                var contentObserver = bridge.observe { vm.onCalendarChanged() }
                val lifecycleObserver = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        if (contentObserver == null) contentObserver = bridge.observe { vm.onCalendarChanged() }
                        vm.onForeground()
                    }
                }
                owner.lifecycle.addObserver(lifecycleObserver)
                onDispose {
                    owner.lifecycle.removeObserver(lifecycleObserver)
                    contentObserver?.let { bridge.unobserve(it) }
                }
            }
            val initialTab = this@MainActivity.intent.getStringExtra(EXTRA_NIB_TAB) ?: "today"
            val initialDetailId = this@MainActivity.intent.getStringExtra(EXTRA_NIB_ENTRY_ID)
            NibApp(vm, initialTab, initialDetailId)
        }
    }

    private fun showReport(text: String) {
        runCatching {
            val root = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.WHITE)
            }
            val title = TextView(this).apply {
                this.text = "落笔启动失败"
                setTextColor(Color.BLACK)
                textSize = 18f
                setPadding(48, 96, 48, 12)
                setTypeface(typeface, Typeface.BOLD)
            }
            val body = TextView(this).apply {
                this.text = text
                setTextColor(Color.BLACK)
                textSize = 10f
                setPadding(48, 0, 48, 24)
                setTextIsSelectable(true)
            }
            val scroll = ScrollView(this).apply { addView(body) }
            val actions = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(24, 0, 24, 48)
            }
            val copy = Button(this).apply {
                this.text = "复制错误信息"
                setOnClickListener { copyToClipboard(text) }
            }
            val proceed = Button(this).apply {
                this.text = "仍然进入"
                setOnClickListener {
                    CrashCatcher.markSeen(this@MainActivity)
                    recreate()
                }
            }
            actions.addView(copy, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            actions.addView(proceed, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            root.addView(title)
            root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            root.addView(actions)
            setContentView(root)
        }.onFailure { fallback ->
            val simple = TextView(this).apply {
                this.text = "落笔启动失败\n\n$text\n\n${Log.getStackTraceString(fallback)}"
                setTextColor(Color.BLACK)
                setBackgroundColor(Color.WHITE)
                textSize = 10f
                setPadding(48, 96, 48, 48)
                setTextIsSelectable(true)
            }
            setContentView(ScrollView(this).apply { addView(simple) })
        }
    }

    private fun copyToClipboard(text: String) {
        runCatching {
            getSystemService(ClipboardManager::class.java)
                ?.setPrimaryClip(ClipData.newPlainText("nib-crash", text))
            Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show()
        }
    }
}
