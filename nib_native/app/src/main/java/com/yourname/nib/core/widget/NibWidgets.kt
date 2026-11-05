package com.yourname.nib.core.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.yourname.nib.R
import com.yourname.nib.core.capture.captureIntent
import com.yourname.nib.core.capture.detailIntent
import com.yourname.nib.core.capture.mainIntent
import com.yourname.nib.core.db.NibGraph
import com.yourname.nib.core.reminder.ReminderBootstrap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val cardBg = ColorProvider(R.color.widget_bg)
private val primaryText = ColorProvider(R.color.widget_text)
private val secondaryText = ColorProvider(R.color.widget_text_sub)
private val accentText = ColorProvider(R.color.widget_accent)

private val KEY_ENTRY_ID = ActionParameters.Key<String>("entry_id")

private fun card(): GlanceModifier = GlanceModifier.fillMaxSize().background(cardBg).padding(12.dp)

private fun titleStyle() = TextStyle(color = primaryText, fontWeight = FontWeight.Bold)

private fun bodyStyle() = TextStyle(color = primaryText)

private fun subStyle() = TextStyle(color = secondaryText)

class NibCaptureWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            Column(
                modifier = card().clickable(actionStartActivity(captureIntent(context))),
            ) {
                Text("落笔 · 随手记一句", style = titleStyle())
                Text("点一下打开速记浮层", style = subStyle())
            }
        }
    }
}

class NibTodoWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val rows = withContext(Dispatchers.IO) { widgetTodos(context) }
        provideContent {
            Column(modifier = card()) {
                Text("今日待办", style = titleStyle())
                if (rows.isEmpty()) {
                    Text("今天没有待办", style = subStyle())
                } else {
                    rows.forEach { row ->
                        Row(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .padding(4.dp)
                                .clickable(
                                    actionRunCallback<MarkDoneAction>(
                                        actionParametersOf(KEY_ENTRY_ID to row.id),
                                    ),
                                ),
                        ) {
                            Text("○ ${row.title}", style = bodyStyle())
                        }
                    }
                }
                Text("打开落笔", style = TextStyle(color = accentText))
            }
        }
    }
}

class NibListWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val rows = withContext(Dispatchers.IO) { widgetRecent(context) }
        provideContent {
            Column(modifier = card()) {
                Text("最近记录", style = titleStyle())
                if (rows.isEmpty()) {
                    Text("还没有记录", style = subStyle())
                } else {
                    rows.forEach { row ->
                        Row(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .padding(4.dp)
                                .clickable(actionStartActivity(detailIntent(context, row.id))),
                        ) {
                            Text(
                                if (row.done) "✓ ${row.title}" else "· ${row.title}",
                                style = bodyStyle(),
                            )
                        }
                    }
                }
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(4.dp)
                        .clickable(actionStartActivity(mainIntent(context, "all"))),
                ) {
                    Text("打开落笔", style = TextStyle(color = accentText))
                }
            }
        }
    }
}

class MarkDoneAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val entryId = parameters[KEY_ENTRY_ID] ?: return
        NibGraph.repository(context.applicationContext).markDoneFromWidget(entryId)
        ReminderBootstrap.reschedule(context.applicationContext)
        NibTodoWidget().update(context.applicationContext, glanceId)
    }
}

class CaptureWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NibCaptureWidget()
}

class TodoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NibTodoWidget()
}

class ListWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NibListWidget()
}
