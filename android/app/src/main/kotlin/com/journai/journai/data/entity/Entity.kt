package com.journai.journai.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "entities")
data class Entity(
    @PrimaryKey
    val id: String,
    val type: EntityType,
    val name: String,
    val createdAt: kotlinx.datetime.Instant
)

enum class EntityType {
    PERSON,
    PLACE,
    ORGANIZATION,
    EVENT,
    EMOTION,
    ACTIVITY,
    OTHER
}
