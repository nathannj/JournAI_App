package com.journai.journai.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

@Entity(
    tableName = "timeline_items",
    foreignKeys = [
        ForeignKey(
            entity = Entry::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        androidx.room.Index(value = ["entryId"]) 
    ]
)
data class TimelineItem(
    @PrimaryKey
    val id: String,
    val entryId: String,
    val timestamp: Instant,
    val summary: String
)
