package com.journai.journai.di

import android.content.Context
import androidx.room.Room
import com.journai.journai.data.dao.*
import com.journai.journai.data.database.JournAIDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideJournAIDatabase(
        @ApplicationContext context: Context
    ): JournAIDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            JournAIDatabase::class.java,
            "journai_database"
        )
        .fallbackToDestructiveMigration() // For MVP - replace with proper migrations later
        .build()
    }
    
    @Provides
    fun provideEntryDao(database: JournAIDatabase): EntryDao = database.entryDao()
    
    @Provides
    fun provideTagDao(database: JournAIDatabase): TagDao = database.tagDao()
    
    @Provides
    fun provideEntryTagDao(database: JournAIDatabase): EntryTagDao = database.entryTagDao()
    
    @Provides
    fun provideMediaDao(database: JournAIDatabase): MediaDao = database.mediaDao()
    
    @Provides
    fun provideEmbeddingDao(database: JournAIDatabase): EmbeddingDao = database.embeddingDao()
    
    @Provides
    fun provideEntityDao(database: JournAIDatabase): EntityDao = database.entityDao()
    
    @Provides
    fun provideEntryEntityDao(database: JournAIDatabase): EntryEntityDao = database.entryEntityDao()
    
    @Provides
    fun provideTimelineDao(database: JournAIDatabase): TimelineDao = database.timelineDao()
}
