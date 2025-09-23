package com.journai.journai.data.dao

import androidx.room.*
import com.journai.journai.data.entity.Media
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    @Query("SELECT * FROM media WHERE entryId = :entryId")
    fun getMediaForEntry(entryId: String): Flow<List<Media>>
    
    @Query("SELECT * FROM media WHERE id = :id")
    suspend fun getMediaById(id: String): Media?
    
    @Query("SELECT * FROM media WHERE type = :type ORDER BY id DESC")
    fun getMediaByType(type: com.journai.journai.data.entity.MediaType): Flow<List<Media>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(media: Media)
    
    @Update
    suspend fun updateMedia(media: Media)
    
    @Delete
    suspend fun deleteMedia(media: Media)
    
    @Query("DELETE FROM media WHERE entryId = :entryId")
    suspend fun deleteMediaForEntry(entryId: String)
}
