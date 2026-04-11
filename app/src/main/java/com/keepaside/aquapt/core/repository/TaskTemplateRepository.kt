package com.keepaside.aquapt.core.repository

import com.keepaside.aquapt.core.database.TaskTemplateEntity
import com.keepaside.aquapt.core.database.dao.TaskTemplateDao
import com.keepaside.aquapt.core.model.TaskTemplate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TaskTemplateRepository(
    private val dao: TaskTemplateDao
) {
    fun getAll(): Flow<List<TaskTemplate>> =
        dao.getAll().map { list -> list.map { it.toDomain() } }

    fun getByAquariumId(aquariumId: String): Flow<List<TaskTemplate>> =
        dao.getByAquariumId(aquariumId).map { list -> list.map { it.toDomain() } }

    suspend fun getById(id: String): TaskTemplate? =
        dao.getById(id)?.toDomain()

    suspend fun upsert(template: TaskTemplate, primaryAquariumId: String) =
        dao.upsert(TaskTemplateEntity.fromDomain(template, primaryAquariumId))

    suspend fun delete(template: TaskTemplate, primaryAquariumId: String) =
        dao.delete(TaskTemplateEntity.fromDomain(template, primaryAquariumId))

    suspend fun deleteById(id: String) =
        dao.deleteById(id)

    suspend fun clearReminderGroup(reminderGroupId: String): Int =
        dao.clearReminderGroup(reminderGroupId)
}
