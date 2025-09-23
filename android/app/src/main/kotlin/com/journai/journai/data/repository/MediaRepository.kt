package com.journai.journai.data.repository

import com.journai.journai.data.dao.MediaDao
import com.journai.journai.data.entity.Media
import com.journai.journai.data.entity.MediaType
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepository @Inject constructor(
    private val mediaDao: MediaDao
) {
    fun getMediaForEntry(entryId: String): Flow<List<Media>> = mediaDao.getMediaForEntry(entryId)
    
    suspend fun getMediaById(id: String): Media? = mediaDao.getMediaById(id)
    
    fun getMediaByType(type: MediaType): Flow<List<Media>> = mediaDao.getMediaByType(type)
    
    suspend fun saveMedia(media: Media) = mediaDao.insertMedia(media)
    
    suspend fun updateMedia(media: Media) = mediaDao.updateMedia(media)
    
    suspend fun deleteMedia(media: Media) = mediaDao.deleteMedia(media)
    
    suspend fun deleteMediaForEntry(entryId: String) = mediaDao.deleteMediaForEntry(entryId)
    
    suspend fun addMediaToEntry(
        entryId: String,
        uri: String,
        type: MediaType,
        metadata: String? = null
    ) {
        val media = Media(
            id = generateId(),
            entryId = entryId,
            type = type,
            uri = uri,
            metadata = metadata
        )
        saveMedia(media)
    }
    
    private fun generateId(): String = java.util.UUID.randomUUID().toString()
}
