package com.journai.journai.data.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.content.Context
import com.journai.journai.data.dao.*
import com.journai.journai.data.entity.*
import com.journai.journai.data.converter.Converters

@Database(
    entities = [
        Entry::class,
        Tag::class,
        EntryTag::class,
        Media::class,
        Embedding::class,
        Entity::class,
        EntryEntity::class,
        TimelineItem::class,
        EntryFts::class,
        com.journai.journai.data.entity.ChatThread::class,
        com.journai.journai.data.entity.ChatMessageEntity::class
    ],
    version = 4,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class JournAIDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao
    abstract fun tagDao(): TagDao
    abstract fun entryTagDao(): EntryTagDao
    abstract fun mediaDao(): MediaDao
    abstract fun embeddingDao(): EmbeddingDao
    abstract fun entityDao(): EntityDao
    abstract fun entryEntityDao(): EntryEntityDao
    abstract fun timelineDao(): TimelineDao
    abstract fun chatDao(): ChatDao
    
    companion object {
        @Volatile
        private var INSTANCE: JournAIDatabase? = null
        
        fun getDatabase(context: Context): JournAIDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    JournAIDatabase::class.java,
                    "journai_database"
                )
                .addMigrations(MIGRATION_3_4)
                // Room manages FTS virtual table and triggers for @Fts4 contentEntity
                .build()
                INSTANCE = instance
                instance
            }
        }

        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // 1) Create new table without mood column
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS entries_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        createdAt TEXT NOT NULL,
                        editedAt TEXT NOT NULL,
                        richBody TEXT NOT NULL,
                        isArchived INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                // 2) Copy data from old table to new table (omitting mood)
                db.execSQL(
                    """
                    INSERT INTO entries_new (id, createdAt, editedAt, richBody, isArchived)
                    SELECT id, createdAt, editedAt, richBody, isArchived FROM entries
                    """.trimIndent()
                )

                // 3) Drop FTS triggers to avoid conflicts during rename
                db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_entries_fts_BEFORE_UPDATE")
                db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_entries_fts_BEFORE_DELETE")
                db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_entries_fts_AFTER_UPDATE")
                db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_entries_fts_AFTER_INSERT")

                // 4) Drop old entries table and rename new table
                db.execSQL("DROP TABLE entries")
                db.execSQL("ALTER TABLE entries_new RENAME TO entries")

                // 5) Recreate FTS virtual table and triggers so it reflects new schema
                db.execSQL("DROP TABLE IF EXISTS entries_fts")
                db.execSQL(
                    """
                    CREATE VIRTUAL TABLE IF NOT EXISTS entries_fts USING FTS4(
                        richBody TEXT NOT NULL,
                        content=`entries`
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_entries_fts_BEFORE_UPDATE
                    BEFORE UPDATE ON entries BEGIN
                        DELETE FROM entries_fts WHERE docid=OLD.rowid;
                    END
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_entries_fts_BEFORE_DELETE
                    BEFORE DELETE ON entries BEGIN
                        DELETE FROM entries_fts WHERE docid=OLD.rowid;
                    END
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_entries_fts_AFTER_UPDATE
                    AFTER UPDATE ON entries BEGIN
                        INSERT INTO entries_fts(docid, richBody) VALUES (NEW.rowid, NEW.richBody);
                    END
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_entries_fts_AFTER_INSERT
                    AFTER INSERT ON entries BEGIN
                        INSERT INTO entries_fts(docid, richBody) VALUES (NEW.rowid, NEW.richBody);
                    END
                    """.trimIndent()
                )
            }
        }
    }
}
