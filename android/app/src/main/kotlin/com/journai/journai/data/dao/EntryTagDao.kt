package com.journai.journai.data.dao

import androidx.room.*
import com.journai.journai.data.entity.EntryTag
import com.journai.journai.data.entity.Tag
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryTagDao {
    @Query("SELECT t.* FROM tags t INNER JOIN entry_tags et ON t.id = et.tagId WHERE et.entryId = :entryId")
    fun getTagsForEntry(entryId: String): Flow<List<Tag>>
    
    @Query("SELECT e.* FROM entries e INNER JOIN entry_tags et ON e.id = et.entryId WHERE et.tagId = :tagId ORDER BY e.createdAt DESC")
    fun getEntriesForTag(tagId: String): Flow<List<com.journai.journai.data.entity.Entry>>
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEntryTag(entryTag: EntryTag)
    
    @Delete
    suspend fun deleteEntryTag(entryTag: EntryTag)
    
    @Query("DELETE FROM entry_tags WHERE entryId = :entryId")
    suspend fun deleteAllTagsForEntry(entryId: String)
    
    @Query("DELETE FROM entry_tags WHERE tagId = :tagId")
    suspend fun deleteAllEntriesForTag(tagId: String)
}
