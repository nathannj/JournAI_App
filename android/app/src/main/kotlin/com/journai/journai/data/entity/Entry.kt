package com.journai.journai.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

@Entity(tableName = "entries")
data class Entry(
    @PrimaryKey
    val id: String,
    val createdAt: Instant,
    val editedAt: Instant,
    val richBody: String,
    val isArchived: Boolean = false
)
