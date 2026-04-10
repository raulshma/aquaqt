package com.keepaside.aquapt.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.keepaside.aquapt.core.model.*

@Entity(
    tableName = "livestock",
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
data class LivestockEntity(
    @PrimaryKey val id: String,
    val aquariumId: String,
    val kind: LivestockKind = LivestockKind.FISH,
    val name: String,
    val species: String = "",
    val quantity: Int = 1,
    val acquiredAt: String = "",
    val purchasePrice: Double? = null,
    val photoUri: String? = null,
    val dietaryNotes: String? = null,
    val parentId: String? = null,
    val status: LivestockStatus = LivestockStatus.ACTIVE
) {
    fun toDomain() = Livestock(
        id = id,
        aquariumId = aquariumId,
        kind = kind,
        name = name,
        species = species,
        quantity = quantity,
        acquiredAt = acquiredAt,
        purchasePrice = purchasePrice,
        photoUri = photoUri,
        dietaryNotes = dietaryNotes,
        parentId = parentId,
        status = status
    )

    companion object {
        fun fromDomain(domain: Livestock) = LivestockEntity(
            id = domain.id,
            aquariumId = domain.aquariumId,
            kind = domain.kind,
            name = domain.name,
            species = domain.species,
            quantity = domain.quantity,
            acquiredAt = domain.acquiredAt,
            purchasePrice = domain.purchasePrice,
            photoUri = domain.photoUri,
            dietaryNotes = domain.dietaryNotes,
            parentId = domain.parentId,
            status = domain.status
        )
    }
}
