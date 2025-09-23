package com.journai.journai.data.dao

import androidx.room.*
import com.journai.journai.data.entity.EntryEntity
import com.journai.journai.data.entity.Entity
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryEntityDao {
    @Query("SELECT e.* FROM entities e INNER JOIN entry_entities ee ON e.id = ee.entityId WHERE ee.entryId = :entryId ORDER BY ee.salience DESC")
    fun getEntitiesForEntry(entryId: String): Flow<List<Entity>>
    
    @Query("SELECT en.* FROM entries en INNER JOIN entry_entities ee ON en.id = ee.entryId WHERE ee.entityId = :entityId ORDER BY en.createdAt DESC")
    fun getEntriesForEntity(entityId: String): Flow<List<com.journai.journai.data.entity.Entry>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntryEntity(entryEntity: EntryEntity)
    
    @Delete
    suspend fun deleteEntryEntity(entryEntity: EntryEntity)
    
    @Query("DELETE FROM entry_entities WHERE entryId = :entryId")
    suspend fun deleteAllEntitiesForEntry(entryId: String)
    
    @Query("DELETE FROM entry_entities WHERE entityId = :entityId")
    suspend fun deleteAllEntriesForEntity(entityId: String)
}
