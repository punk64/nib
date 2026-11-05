package com.yourname.nib.core.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        NoteList::class,
        Entry::class,
        Tag::class,
        EntryTag::class,
        EntryItem::class,
        OpLog::class,
        PendingCapture::class,
        Conflict::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class NibDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao
    abstract fun listDao(): ListDao
    abstract fun tagDao(): TagDao
    abstract fun entryTagDao(): EntryTagDao
    abstract fun entryItemDao(): EntryItemDao
    abstract fun opLogDao(): OpLogDao
    abstract fun pendingCaptureDao(): PendingCaptureDao
    abstract fun conflictDao(): ConflictDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `pending_capture` (`id` TEXT NOT NULL, `raw` TEXT NOT NULL, `created_at` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            }
        }

        private fun createFts(db: SupportSQLiteDatabase) {
            runCatching {
                db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS entries_fts USING fts5(entry_id UNINDEXED, title, body, tags, tokenize = 'trigram')")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS entries_fts_ai AFTER INSERT ON entries BEGIN INSERT INTO entries_fts(entry_id, title, body, tags) VALUES (new.id, new.title, new.body, ''); END")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS entries_fts_au AFTER UPDATE ON entries BEGIN UPDATE entries_fts SET title = new.title, body = new.body WHERE entry_id = new.id; END")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS entries_fts_ad AFTER DELETE ON entries BEGIN DELETE FROM entries_fts WHERE entry_id = old.id; END")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS entry_tags_fts_ai AFTER INSERT ON entry_tags BEGIN UPDATE entries_fts SET tags = COALESCE((SELECT group_concat(name, ' ') FROM tags WHERE id IN (SELECT tag_id FROM entry_tags WHERE entry_id = new.entry_id)), '') WHERE entry_id = new.entry_id; END")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS entry_tags_fts_ad AFTER DELETE ON entry_tags BEGIN UPDATE entries_fts SET tags = COALESCE((SELECT group_concat(name, ' ') FROM tags WHERE id IN (SELECT tag_id FROM entry_tags WHERE entry_id = old.entry_id)), '') WHERE entry_id = old.entry_id; END")
                db.execSQL("INSERT INTO entries_fts(entry_id, title, body, tags) SELECT e.id, e.title, e.body, COALESCE((SELECT group_concat(t.name, ' ') FROM tags t JOIN entry_tags et ON et.tag_id = t.id WHERE et.entry_id = e.id), '') FROM entries e")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createFts(db)
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `conflicts` (" +
                        "`id` TEXT NOT NULL, `entity_type` TEXT NOT NULL, `entity_id` TEXT NOT NULL, " +
                        "`field` TEXT NOT NULL, `loser_value` TEXT, `loser_hlc` TEXT NOT NULL, " +
                        "`winner_hlc` TEXT NOT NULL, `device_id` TEXT NOT NULL, " +
                        "`created_at` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
            }
        }

        fun build(context: Context): NibDatabase =
            Room.databaseBuilder(context, NibDatabase::class.java, "nib.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        runCatching {
                            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_tags_name ON tags(name) WHERE deleted_at IS NULL")
                        }
                        createFts(db)
                        runCatching { db.execSQL("PRAGMA foreign_keys = ON") }
                    }

                    override fun onOpen(db: SupportSQLiteDatabase) {
                        runCatching { db.execSQL("PRAGMA foreign_keys = ON") }
                    }
                })
                .build()
    }
}
