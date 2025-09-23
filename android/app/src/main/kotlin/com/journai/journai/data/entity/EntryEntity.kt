package com.journai.journai.data.entity

import androidx.room.Entity as RoomEntity
import androidx.room.ForeignKey

@RoomEntity(
    tableName = "entry_entities",
    primaryKeys = ["entryId", "entityId"],
    foreignKeys = [
        ForeignKey(
            entity = Entry::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = com.journai.journai.data.entity.Entity::class,
            parentColumns = ["id"],
            childColumns = ["entityId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        androidx.room.Index(value = ["entryId"]),
        androidx.room.Index(value = ["entityId"]) 
    ]
)
data class EntryEntity(
    val entryId: String,
    val entityId: String,
    val salience: Float // 0.0 to 1.0
)
