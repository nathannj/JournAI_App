package com.journai.journai.data.dao

import androidx.room.*
import com.journai.journai.data.entity.TimelineItem
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

@Dao
interface TimelineDao {
    @Query("SELECT * FROM timeline_items WHERE entryId = :entryId ORDER BY timestamp ASC")
    fun getTimelineForEntry(entryId: String): Flow<List<TimelineItem>>
    
    @Query("SELECT * FROM timeline_items WHERE id = :id")
    suspend fun getTimelineItemById(id: String): TimelineItem?
    
    @Query("SELECT * FROM timeline_items WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    fun getTimelineBetween(startTime: Instant, endTime: Instant): Flow<List<TimelineItem>>
    
    @Query("SELECT * FROM timeline_items WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    suspend fun getTimelineBetweenOnce(startTime: Instant, endTime: Instant): List<TimelineItem>
    
    @Query("SELECT * FROM timeline_items ORDER BY timestamp DESC")
    fun getAllTimelineItems(): Flow<List<TimelineItem>>
    
    @Query("SELECT * FROM timeline_items ORDER BY timestamp DESC")
    suspend fun getAllTimelineItemsOnce(): List<TimelineItem>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTimelineItem(timelineItem: TimelineItem)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTimelineItems(timelineItems: List<TimelineItem>)
    
    @Update
    suspend fun updateTimelineItem(timelineItem: TimelineItem)
    
    @Delete
    suspend fun deleteTimelineItem(timelineItem: TimelineItem)
    
    @Query("DELETE FROM timeline_items WHERE entryId = :entryId")
    suspend fun deleteTimelineForEntry(entryId: String)
}
