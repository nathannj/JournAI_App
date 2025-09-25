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
    version = 3,
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
                .fallbackToDestructiveMigration() // For MVP - replace with proper migrations later
                // Room manages FTS virtual table and triggers for @Fts4 contentEntity
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
