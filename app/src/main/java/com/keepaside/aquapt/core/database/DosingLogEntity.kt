package com.keepaside.aquapt.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.keepaside.aquapt.core.model.*

@Entity(
    tableName = "dosing_logs",
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
data class DosingLogEntity(
    @PrimaryKey val id: String,
    val aquariumId: String,
    val product: String,
    val amountMl: Double,
    val createdAt: String,
    val note: String? = null
) {
    fun toDomain() = DosingLog(
        id = id,
        aquariumId = aquariumId,
        product = product,
        amountMl = amountMl,
        createdAt = createdAt,
        note = note
    )

    companion object {
        fun fromDomain(domain: DosingLog) = DosingLogEntity(
            id = domain.id,
            aquariumId = domain.aquariumId,
            product = domain.product,
            amountMl = domain.amountMl,
            createdAt = domain.createdAt,
            note = domain.note
        )
    }
}
