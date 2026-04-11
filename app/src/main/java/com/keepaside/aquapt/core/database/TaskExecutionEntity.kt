package com.keepaside.aquapt.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.keepaside.aquapt.core.model.*

@Entity(
    tableName = "task_executions",
    foreignKeys = [
        ForeignKey(
            entity = TaskTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskTemplateId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = AquariumEntity::class,
            parentColumns = ["id"],
            childColumns = ["aquariumId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["taskTemplateId"]), Index(value = ["aquariumId"])]
)
data class TaskExecutionEntity(
    @PrimaryKey val id: String,
    val taskTemplateId: String,
    val aquariumId: String,
    val completedAt: String,
    val note: String? = null
) {
    fun toDomain() = TaskExecution(
        id = id,
        taskTemplateId = taskTemplateId,
        aquariumId = aquariumId,
        completedAt = completedAt,
        note = note
    )

    companion object {
        fun fromDomain(domain: TaskExecution) = TaskExecutionEntity(
            id = domain.id,
            taskTemplateId = domain.taskTemplateId,
            aquariumId = domain.aquariumId,
            completedAt = domain.completedAt,
            note = domain.note
        )
    }
}
