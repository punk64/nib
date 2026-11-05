package com.yourname.nib.core

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File

object CrashCatcher {
    private const val PREFS = "nib_crash"
    private const val SEEN = "seen_at"

    private var app: Context? = null

    fun install(context: Context) {
        app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            record(error, thread.name)
            previous?.uncaughtException(thread, error)
        }
    }

    fun record(error: Throwable, threadName: String = Thread.currentThread().name) {
        val context = app ?: return
        runCatching {
            val stamp = System.currentTimeMillis()
            val text = buildString {
                appendLine("time: $stamp")
                appendLine("thread: $threadName")
                appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL} / API ${Build.VERSION.SDK_INT} / ${Build.VERSION.RELEASE}")
                appendLine(Log.getStackTraceString(error))
            }
            val dir = File(context.filesDir, "crash").apply { mkdirs() }
            File(dir, "crash-$stamp.txt").writeText(text)
            context.getExternalFilesDir("crash")?.let { ext ->
                ext.mkdirs()
                File(ext, "crash-$stamp.txt").writeText(text)
            }
            dir.listFiles()?.sortedByDescending { it.name }?.drop(10)?.forEach { it.delete() }
        }
    }

    fun unread(context: Context): String? = runCatching {
        val latest = File(context.filesDir, "crash").listFiles()?.maxByOrNull { it.lastModified() }
            ?: return null
        val seen = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(SEEN, 0L)
        if (latest.lastModified() <= seen) return null
        latest.readText()
    }.getOrNull()

    fun markSeen(context: Context) {
        runCatching {
            val latest = File(context.filesDir, "crash").listFiles()?.maxByOrNull { it.lastModified() } ?: return
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putLong(SEEN, latest.lastModified()).apply()
        }
    }

    fun latest(context: Context): String? = runCatching {
        File(context.filesDir, "crash").listFiles()
            ?.maxByOrNull { it.lastModified() }
            ?.readText()
    }.getOrNull()

    fun exportPath(context: Context): String =
        (context.getExternalFilesDir("crash") ?: File(context.filesDir, "crash")).absolutePath
}
