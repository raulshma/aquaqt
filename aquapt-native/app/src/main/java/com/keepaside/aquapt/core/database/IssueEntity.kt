package com.keepaside.aquapt.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.keepaside.aquapt.core.model.*

@Entity(
    tableName = "issues",
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
data class IssueEntity(
    @PrimaryKey val id: String,
    val aquariumId: String,
    val title: String,
    val status: IssueStatus = IssueStatus.OPEN,
    val createdAt: String,
    val resolutionNote: String? = null
) {
    fun toDomain() = Issue(
        id = id,
        aquariumId = aquariumId,
        title = title,
        status = status,
        createdAt = createdAt,
        resolutionNote = resolutionNote
    )

    companion object {
        fun fromDomain(domain: Issue) = IssueEntity(
            id = domain.id,
            aquariumId = domain.aquariumId,
            title = domain.title,
            status = domain.status,
            createdAt = domain.createdAt,
            resolutionNote = domain.resolutionNote
        )
    }
}
