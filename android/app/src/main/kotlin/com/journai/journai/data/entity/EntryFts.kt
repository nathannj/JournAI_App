package com.journai.journai.data.entity

import androidx.room.Entity
import androidx.room.Fts4

@Fts4(contentEntity = Entry::class)
@Entity(tableName = "entries_fts")
data class EntryFts(
    val richBody: String
)
