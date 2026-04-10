package com.keepaside.aquapt.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.keepaside.aquapt.core.model.*

@Entity(
    tableName = "memos",
    foreignKeys = [
        ForeignKey(
            entity = AquariumEntity::class,
            parentColumns = ["id"],
            childColumns = ["aquariumId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["aquariumId"])]
)
data class MemoEntity(
    @PrimaryKey val id: String,
    val aquariumId: String,
    val content: String,
    val createdAt: String,
    val photoUri: String? = null
) {
    fun toDomain() = Memo(
        id = id,
        aquariumId = aquariumId,
        content = content,
        createdAt = createdAt,
        photoUri = photoUri
    )

    companion object {
        fun fromDomain(domain: Memo) = MemoEntity(
            id = domain.id,
            aquariumId = domain.aquariumId,
            content = domain.content,
            createdAt = domain.createdAt,
            photoUri = domain.photoUri
        )
    }
}
