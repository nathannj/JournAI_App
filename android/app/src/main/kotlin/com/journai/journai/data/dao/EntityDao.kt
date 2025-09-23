package com.journai.journai.data.dao

import androidx.room.*
import com.journai.journai.data.entity.Entity
import com.journai.journai.data.entity.EntityType
import kotlinx.coroutines.flow.Flow

@Dao
interface EntityDao {
    @Query("SELECT * FROM entities ORDER BY name ASC")
    fun getAllEntities(): Flow<List<Entity>>
    
    @Query("SELECT * FROM entities WHERE id = :id")
    suspend fun getEntityById(id: String): Entity?
    
    @Query("SELECT * FROM entities WHERE name = :name")
    suspend fun getEntityByName(name: String): Entity?
    
    @Query("SELECT * FROM entities WHERE type = :type ORDER BY name ASC")
    fun getEntitiesByType(type: EntityType): Flow<List<Entity>>
    
    @Query("SELECT * FROM entities WHERE name LIKE :query ORDER BY name ASC")
    fun searchEntities(query: String): Flow<List<Entity>>
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEntity(entity: Entity): Long
    
    @Update
    suspend fun updateEntity(entity: Entity)
    
    @Delete
    suspend fun deleteEntity(entity: Entity)
}
