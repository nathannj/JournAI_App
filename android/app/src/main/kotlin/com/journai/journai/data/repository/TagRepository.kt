package com.journai.journai.data.repository

import com.journai.journai.data.dao.EntryTagDao
import com.journai.journai.data.dao.TagDao
import com.journai.journai.data.entity.Entry
import com.journai.journai.data.entity.EntryTag
import com.journai.journai.data.entity.Tag
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TagRepository @Inject constructor(
    private val tagDao: TagDao,
    private val entryTagDao: EntryTagDao
) {
    fun getAllTags(): Flow<List<Tag>> = tagDao.getAllTags()
    
    suspend fun getTagById(id: String): Tag? = tagDao.getTagById(id)
    
    suspend fun getTagByName(name: String): Tag? = tagDao.getTagByName(name)
    
    fun searchTags(query: String): Flow<List<Tag>> = tagDao.searchTags(query)
    
    fun getTagsForEntry(entryId: String): Flow<List<Tag>> = entryTagDao.getTagsForEntry(entryId)
    
    fun getEntriesForTag(tagId: String): Flow<List<Entry>> = entryTagDao.getEntriesForTag(tagId)
    
    suspend fun createTag(name: String): Tag {
        val tag = Tag(
            id = generateId(),
            name = name.trim(),
            createdAt = Clock.System.now()
        )
        tagDao.insertTag(tag)
        return tag
    }
    
    suspend fun createTagIfNotExists(name: String): Tag {
        val existingTag = tagDao.getTagByName(name.trim())
        return if (existingTag != null) {
            existingTag
        } else {
            createTag(name)
        }
    }
    
    suspend fun addTagToEntry(entryId: String, tagName: String) {
        val tag = createTagIfNotExists(tagName)
        val entryTag = EntryTag(entryId = entryId, tagId = tag.id)
        entryTagDao.insertEntryTag(entryTag)
    }
    
    suspend fun removeTagFromEntry(entryId: String, tagName: String) {
        val tag = tagDao.getTagByName(tagName.trim())
        if (tag != null) {
            val entryTag = EntryTag(entryId = entryId, tagId = tag.id)
            entryTagDao.deleteEntryTag(entryTag)
        }
    }
    
    suspend fun setTagsForEntry(entryId: String, tagNames: List<String>) {
        // Remove all existing tags for this entry
        entryTagDao.deleteAllTagsForEntry(entryId)
        
        // Add new tags
        tagNames.forEach { tagName ->
            if (tagName.isNotBlank()) {
                addTagToEntry(entryId, tagName)
            }
        }
    }
    
    suspend fun deleteTag(tag: Tag) = tagDao.deleteTag(tag)
    
    private fun generateId(): String = java.util.UUID.randomUUID().toString()
}
