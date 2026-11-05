package com.yourname.nib.features.me

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yourname.nib.core.calendar.NibCalendar
import com.yourname.nib.core.reminder.localMillis
import com.yourname.nib.core.theme.LocalNibTokens
import com.yourname.nib.core.theme.nibFontFamily
import com.yourname.nib.core.theme.tokenColor
import com.yourname.nib.core.theme.withAlpha
import com.yourname.nib.core.time.NibDate
import com.yourname.nib.widgets.NibCard
import com.yourname.nib.widgets.NibHero
import com.yourname.nib.widgets.NibIconButton
import com.yourname.nib.widgets.SectionTitle

private data class PermissionItem(val label: String, val granted: Boolean, val hint: String)

private fun permissionItems(context: Context): List<PermissionItem> {
    val notifications = NotificationManagerCompat.from(context).areNotificationsEnabled()
    val postGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
    val battery = Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(context.packageName)
    val calendarRead = NibCalendar.hasReadPermission(context)
    val calendarWrite = NibCalendar.hasPermission(context)
    val calendarLabel = when {
        calendarWrite -> "日历读写"
        calendarRead -> "仅日历读取"
        else -> "日历权限"
    }
    val calendarHint = when {
        calendarWrite -> "已授权 · 可读 ${NibCalendar.calendarCount(context)} 个日历"
        calendarRead -> "缺写入权限，日程不会写回系统日历"
        else -> "未授权，系统日程无法读取"
    }
    return listOf(
        PermissionItem("通知权限", notifications && postGranted, "关闭后提醒无法弹出"),
        PermissionItem("精确闹钟", exact, "关闭后提醒可能延迟最多 15 分钟"),
        PermissionItem("忽略电池优化", battery, "未忽略时后台可能被冻结"),
        PermissionItem("自启动与厂商设置", false, "按机型在系统设置里允许自启动"),
        PermissionItem(calendarLabel, calendarWrite, calendarHint),
    )
}

private fun detailsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

private fun permissionIntent(context: Context, index: Int): Intent = when (index) {
    0 -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        detailsIntent(context)
    }
    1 -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
    } else {
        detailsIntent(context)
    }
    2 -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
    } else {
        detailsIntent(context)
    }
    else -> detailsIntent(context)
}

@Composable
fun PermissionScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val t = LocalNibTokens.current
    var refresh by remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        refresh++
        if (!NibCalendar.hasReadPermission(context)) {
            val target = detailsIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(target) }
        }
    }

    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    val items = remember(refresh) { permissionItems(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        NibHero(
            sub = "${items.count { it.granted }} / ${items.size} 项已开启",
            title = "权限",
            actions = { NibIconButton(Icons.AutoMirrored.Filled.ArrowBack) { onClose() } },
        )
        Column(modifier = Modifier.padding(horizontal = 14.dp)) {
            SectionTitle("系统设置检查")
            NibCard {
                items.forEachIndexed { index, item ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (index == 4 && !item.granted) {
                                    launcher.launch(
                                        arrayOf(
                                            Manifest.permission.READ_CALENDAR,
                                            Manifest.permission.WRITE_CALENDAR,
                                        ),
                                    )
                                } else {
                                    val target = permissionIntent(context, index).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    runCatching { context.startActivity(target) }
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(item.label, color = tokenColor(t.text), fontSize = 14.sp, fontFamily = nibFontFamily(), modifier = Modifier.weight(1f))
                            Text(
                                if (item.granted) "已开启" else "去设置",
                                color = tokenColor(if (item.granted) t.ok else t.accent),
                                fontSize = 13.sp,
                                fontFamily = nibFontFamily(),
                            )
                        }
                        Text(item.hint, color = tokenColor(t.text3), fontSize = 11.sp, fontFamily = nibFontFamily())
                    }
                }
            }
            SectionTitle("说明")
            NibCard {
                Text(
                    "提醒由系统闹钟托管；关闭精确闹钟或电池优化后，触发可能延迟。重启、改系统时间或更新应用后会自动重排。带时间的记录可写入自建本地日历，与系统日历 App 双向同步。",
                    color = tokenColor(t.text3),
                    fontSize = 12.sp,
                    fontFamily = nibFontFamily(),
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(12.dp),
                )
            }
            val today = NibDate.today()
            val monthStart = NibDate(today.year, today.month, 1)
            val nextMonth = if (today.month == 12) NibDate(today.year + 1, 1, 1) else NibDate(today.year, today.month + 1, 1)
            val diagnosis = remember(refresh) {
                NibCalendar.diagnose(
                    context,
                    localMillis(monthStart, 0, 0),
                    localMillis(nextMonth, 0, 0),
                )
            }
            SectionTitle("日历诊断")
            NibCard {
                diagnosis.forEachIndexed { index, (label, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, color = tokenColor(t.text2), fontSize = 12.sp, fontFamily = nibFontFamily(), modifier = Modifier.weight(1f))
                        Text(value, color = tokenColor(t.text), fontSize = 12.sp, fontFamily = nibFontFamily())
                    }
                    if (index < diagnosis.lastIndex) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .height(0.6.dp)
                                .background(tokenColor(withAlpha(t.text3, 40))),
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
