package com.journai.journai.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "entry_tags",
    primaryKeys = ["entryId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = Entry::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Tag::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        androidx.room.Index(value = ["entryId"]),
        androidx.room.Index(value = ["tagId"])
    ]
)
data class EntryTag(
    val entryId: String,
    val tagId: String
)
