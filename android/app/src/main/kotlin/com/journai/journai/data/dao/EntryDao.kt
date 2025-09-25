package com.journai.journai.data.dao

import androidx.room.*
import com.journai.journai.data.entity.Entry
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

@Dao
interface EntryDao {
    @Query("SELECT * FROM entries ORDER BY createdAt DESC")
    fun getAllEntries(): Flow<List<Entry>>
    
    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun getEntryById(id: String): Entry?
    
    @Query("SELECT * FROM entries WHERE isArchived = 0 ORDER BY createdAt DESC")
    fun getActiveEntries(): Flow<List<Entry>>
    
    @Query("SELECT * FROM entries WHERE isArchived = 0 ORDER BY createdAt DESC")
    suspend fun getActiveEntriesOnce(): List<Entry>
    
    @Query("SELECT * FROM entries WHERE createdAt BETWEEN :startDate AND :endDate ORDER BY createdAt DESC")
    fun getEntriesBetween(startDate: Instant, endDate: Instant): Flow<List<Entry>>
    
    @Query("SELECT * FROM entries WHERE createdAt BETWEEN :startDate AND :endDate ORDER BY createdAt DESC")
    suspend fun getEntriesBetweenOnce(startDate: Instant, endDate: Instant): List<Entry>
    
    @Query("SELECT * FROM entries WHERE mood = :mood ORDER BY createdAt DESC")
    fun getEntriesByMood(mood: Int): Flow<List<Entry>>
    
    @Query("SELECT * FROM entries WHERE editedAt > :since ORDER BY editedAt ASC")
    suspend fun getEntriesEditedSince(since: Instant): List<Entry>
    
    @Query("""
        SELECT * FROM entries 
        WHERE createdAt >= :startOfDay 
        AND createdAt < :startOfNextDay 
        LIMIT 1
    """)
    suspend fun getEntryByDate(startOfDay: Instant, startOfNextDay: Instant): Entry?
    
    @Query("""
        SELECT e.* FROM entries e 
        JOIN entries_fts ON e.rowid = entries_fts.rowid 
        WHERE entries_fts MATCH :query 
        ORDER BY e.createdAt DESC
    """)
    fun searchEntries(query: String): Flow<List<Entry>>
    
    @Query("SELECT * FROM entries WHERE richBody LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun searchEntriesSimple(query: String): Flow<List<Entry>>
    
    @Query("SELECT * FROM entries WHERE richBody LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    suspend fun searchEntriesSimpleOnce(query: String): List<Entry>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: Entry)
    
    @Update
    suspend fun updateEntry(entry: Entry)
    
    @Delete
    suspend fun deleteEntry(entry: Entry)
    
    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun deleteEntryById(id: String)
}
