package com.keepaside.aquapt.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.keepaside.aquapt.core.model.*

@Entity(
    tableName = "consumables",
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
data class ConsumableEntity(
    @PrimaryKey val id: String,
    val aquariumId: String,
    val name: String,
    val unit: ConsumableUnit = ConsumableUnit.ML,
    val remaining: Double = 0.0,
    val reorderAt: Double? = null,
    val updatedAt: String = "",
    val photoUri: String? = null
) {
    fun toDomain() = Consumable(
        id = id,
        aquariumId = aquariumId,
        name = name,
        unit = unit,
        remaining = remaining,
        reorderAt = reorderAt,
        updatedAt = updatedAt,
        photoUri = photoUri
    )

    companion object {
        fun fromDomain(domain: Consumable) = ConsumableEntity(
            id = domain.id,
            aquariumId = domain.aquariumId,
            name = domain.name,
            unit = domain.unit,
            remaining = domain.remaining,
            reorderAt = domain.reorderAt,
            updatedAt = domain.updatedAt,
            photoUri = domain.photoUri
        )
    }
}
