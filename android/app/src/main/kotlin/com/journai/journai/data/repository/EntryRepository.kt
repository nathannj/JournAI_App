package com.journai.journai.data.repository

import com.journai.journai.data.dao.EntryDao
import com.journai.journai.data.entity.Entry
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EntryRepository @Inject constructor(
    private val entryDao: EntryDao
) {
    fun getAllEntries(): Flow<List<Entry>> = entryDao.getAllEntries()
    
    fun getActiveEntries(): Flow<List<Entry>> = entryDao.getActiveEntries()
    
    suspend fun getEntryById(id: String): Entry? = entryDao.getEntryById(id)
    
    suspend fun getEntryByDate(date: kotlinx.datetime.Instant): Entry? {
        val localDate = date.toLocalDateTime(TimeZone.currentSystemDefault()).date
        val startOfDay = LocalDateTime(localDate, LocalTime(0, 0)).toInstant(TimeZone.currentSystemDefault())
        val startOfNextDay = LocalDateTime(localDate.plus(1, DateTimeUnit.DAY), LocalTime(0, 0)).toInstant(TimeZone.currentSystemDefault())
        return entryDao.getEntryByDate(startOfDay, startOfNextDay)
    }
    
    fun getEntriesBetween(startDate: Instant, endDate: Instant): Flow<List<Entry>> = 
        entryDao.getEntriesBetween(startDate, endDate)
    
    fun getEntriesByMood(mood: Int): Flow<List<Entry>> = entryDao.getEntriesByMood(mood)
    
    fun searchEntries(query: String): Flow<List<Entry>> = entryDao.searchEntries(query)
    
    fun searchEntriesSimple(query: String): Flow<List<Entry>> = entryDao.searchEntriesSimple(query)
    
    suspend fun saveEntry(entry: Entry) = entryDao.insertEntry(entry)
    
    suspend fun updateEntry(entry: Entry) = entryDao.updateEntry(entry)
    
    suspend fun deleteEntry(entry: Entry) = entryDao.deleteEntry(entry)
    
    suspend fun deleteEntryById(id: String) = entryDao.deleteEntryById(id)
}
