package com.keepaside.aquapt.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.keepaside.aquapt.core.model.*

@Entity(
    tableName = "parameter_logs",
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
data class WaterParameterLogEntity(
    @PrimaryKey val id: String,
    val aquariumId: String,
    val createdAt: String,
    val values: WaterParameters
) {
    fun toDomain() = WaterParameterLog(
        id = id,
        aquariumId = aquariumId,
        createdAt = createdAt,
        values = values
    )

    companion object {
        fun fromDomain(domain: WaterParameterLog) = WaterParameterLogEntity(
            id = domain.id,
            aquariumId = domain.aquariumId,
            createdAt = domain.createdAt,
            values = domain.values
        )
    }
}
