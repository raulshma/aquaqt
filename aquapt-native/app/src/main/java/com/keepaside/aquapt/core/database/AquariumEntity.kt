package com.keepaside.aquapt.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.keepaside.aquapt.core.model.*

@Entity(tableName = "aquariums")
data class AquariumEntity(
    @PrimaryKey val id: String,
    val name: String,
    val volumeLiters: Double,
    val dimensions: String = "",
    val waterType: WaterType = WaterType.FRESHWATER,
    val setupDate: String = "",
    val investmentCost: Double? = null,
    val photoUri: String? = null
) {
    fun toDomain() = Aquarium(
        id = id,
        name = name,
        volumeLiters = volumeLiters,
        dimensions = dimensions,
        waterType = waterType,
        setupDate = setupDate,
        investmentCost = investmentCost,
        photoUri = photoUri
    )

    companion object {
        fun fromDomain(domain: Aquarium) = AquariumEntity(
            id = domain.id,
            name = domain.name,
            volumeLiters = domain.volumeLiters,
            dimensions = domain.dimensions,
            waterType = domain.waterType,
            setupDate = domain.setupDate,
            investmentCost = domain.investmentCost,
            photoUri = domain.photoUri
        )
    }
}
