package com.yourname.nib.core.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.yourname.nib.core.CrashCatcher
import java.io.File

object NibGraph {
    @Volatile
    private var database: NibDatabase? = null

    fun db(context: Context): NibDatabase = database ?: synchronized(this) {
        database ?: open(context.applicationContext).also { database = it }
    }

    fun repository(context: Context): NibRepository = NibRepository(db(context))

    private fun open(context: Context): NibDatabase {
        if (isUnusable(context)) quarantine(context)
        return runCatching { NibDatabase.build(context) }.getOrElse { error ->
            CrashCatcher.record(error)
            quarantine(context)
            NibDatabase.build(context)
        }
    }

    private fun isUnusable(context: Context): Boolean {
        val file = context.getDatabasePath("nib.db")
        if (!file.exists()) return false
        if (file.length() == 0L) return true
        return runCatching {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery(
                    "SELECT count(*) FROM sqlite_master WHERE type='table' AND name='room_master_table'",
                    null,
                ).use { cursor -> !(cursor.moveToFirst() && cursor.getInt(0) > 0) }
            }
        }.getOrDefault(true)
    }

    private fun quarantine(context: Context) {
        runCatching {
            val file = context.getDatabasePath("nib.db")
            if (!file.exists()) return
            val backup = File(file.parentFile, "nib.db.broken-${System.currentTimeMillis()}")
            file.renameTo(backup)
        }
    }
}
