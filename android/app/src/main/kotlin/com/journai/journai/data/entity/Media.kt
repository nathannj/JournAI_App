package com.journai.journai.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "media",
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
data class Media(
    @PrimaryKey
    val id: String,
    val entryId: String,
    val type: MediaType,
    val uri: String,
    val metadata: String? // JSON string for additional metadata
)

enum class MediaType {
    IMAGE,
    AUDIO
}
