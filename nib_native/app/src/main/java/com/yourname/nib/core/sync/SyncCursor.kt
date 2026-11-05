package com.yourname.nib.core.sync

import android.content.Context

class SyncCursor(context: Context) {
    private val prefs = context.getSharedPreferences("nib_sync", Context.MODE_PRIVATE)

    fun get(): Long = prefs.getLong("cursor", 0L)

    fun set(value: Long) {
        prefs.edit().putLong("cursor", value).apply()
    }
}
