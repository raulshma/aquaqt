package com.keepaside.aquapt.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.keepaside.aquapt.core.model.*

@Entity(
    tableName = "timeline_events",
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
data class TimelineEventEntity(
    @PrimaryKey val id: String,
    val aquariumId: String,
    val type: TimelineEventType,
    val createdAt: String,
    val title: String,
    val description: String? = null,
    val photoUri: String? = null
) {
    fun toDomain() = TimelineEvent(
        id = id,
        aquariumId = aquariumId,
        type = type,
        createdAt = createdAt,
        title = title,
        description = description,
        photoUri = photoUri
    )

    companion object {
        fun fromDomain(domain: TimelineEvent) = TimelineEventEntity(
            id = domain.id,
            aquariumId = domain.aquariumId,
            type = domain.type,
            createdAt = domain.createdAt,
            title = domain.title,
            description = domain.description,
            photoUri = domain.photoUri
        )
    }
}
