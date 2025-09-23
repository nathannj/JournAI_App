package com.journai.journai.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tags")
data class Tag(
    @PrimaryKey
    val id: String,
    val name: String,
    val createdAt: kotlinx.datetime.Instant
)
