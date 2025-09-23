package com.journai.journai.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "embeddings",
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
data class Embedding(
    @PrimaryKey
    val id: String,
    val entryId: String,
    val chunkId: String,
    val vector: ByteArray, // Stored as BLOB
    val dims: Int,
    val model: String,
    val createdAt: kotlinx.datetime.Instant
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Embedding

        if (id != other.id) return false
        if (entryId != other.entryId) return false
        if (chunkId != other.chunkId) return false
        if (!vector.contentEquals(other.vector)) return false
        if (dims != other.dims) return false
        if (model != other.model) return false
        if (createdAt != other.createdAt) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + entryId.hashCode()
        result = 31 * result + chunkId.hashCode()
        result = 31 * result + vector.contentHashCode()
        result = 31 * result + dims
        result = 31 * result + model.hashCode()
        result = 31 * result + createdAt.hashCode()
        return result
    }
}
