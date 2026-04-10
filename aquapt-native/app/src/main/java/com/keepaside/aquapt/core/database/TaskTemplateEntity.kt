package com.keepaside.aquapt.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.keepaside.aquapt.core.model.*

@Entity(
    tableName = "task_templates",
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
data class TaskTemplateEntity(
    @PrimaryKey val id: String,
    val aquariumId: String,
    val title: String,
    val description: String? = null,
    val category: TaskCategory? = null,
    val livestockId: String? = null,
    val frequency: TaskFrequency = TaskFrequency.DAILY,
    val aquariumIds: List<String> = emptyList(),
    val startDate: String? = null,
    val timesPerDay: Int? = null,
    val reminderHours: List<Int> = emptyList(),
    val reminderGroupId: String? = null
) {
    fun toDomain() = TaskTemplate(
        id = id,
        title = title,
        description = description,
        category = category,
        livestockId = livestockId,
        frequency = frequency,
        aquariumIds = aquariumIds,
        startDate = startDate,
        timesPerDay = timesPerDay,
        reminderHours = reminderHours,
        reminderGroupId = reminderGroupId
    )

    companion object {
        fun fromDomain(domain: TaskTemplate, primaryAquariumId: String) = TaskTemplateEntity(
            id = domain.id,
            aquariumId = primaryAquariumId,
            title = domain.title,
            description = domain.description,
            category = domain.category,
            livestockId = domain.livestockId,
            frequency = domain.frequency,
            aquariumIds = domain.aquariumIds,
            startDate = domain.startDate,
            timesPerDay = domain.timesPerDay,
            reminderHours = domain.reminderHours,
            reminderGroupId = domain.reminderGroupId
        )
    }
}
