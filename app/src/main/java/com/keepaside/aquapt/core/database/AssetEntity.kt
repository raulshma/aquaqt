package com.keepaside.aquapt.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.keepaside.aquapt.core.model.*

@Entity(
    tableName = "assets",
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
data class AssetEntity(
    @PrimaryKey val id: String,
    val aquariumId: String,
    val category: AssetCategory = AssetCategory.OTHER,
    val brandModel: String = "",
    val purchasedAt: String? = null,
    val price: Double? = null,
    val maintenanceTaskTemplateIds: List<String> = emptyList(),
    val photoUri: String? = null
) {
    fun toDomain() = Asset(
        id = id,
        aquariumId = aquariumId,
        category = category,
        brandModel = brandModel,
        purchasedAt = purchasedAt,
        price = price,
        maintenanceTaskTemplateIds = maintenanceTaskTemplateIds,
        photoUri = photoUri
    )

    companion object {
        fun fromDomain(domain: Asset) = AssetEntity(
            id = domain.id,
            aquariumId = domain.aquariumId,
            category = domain.category,
            brandModel = domain.brandModel,
            purchasedAt = domain.purchasedAt,
            price = domain.price,
            maintenanceTaskTemplateIds = domain.maintenanceTaskTemplateIds,
            photoUri = domain.photoUri
        )
    }
}
