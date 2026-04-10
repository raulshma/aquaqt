package com.keepaside.aquapt.core.repository

import com.keepaside.aquapt.core.database.TaskExecutionEntity
import com.keepaside.aquapt.core.database.dao.TaskExecutionDao
import com.keepaside.aquapt.core.model.TaskExecution
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TaskExecutionRepository(
    private val dao: TaskExecutionDao
) {
    fun getByTemplateId(templateId: String): Flow<List<TaskExecution>> =
        dao.getByTemplateId(templateId).map { list -> list.map { it.toDomain() } }

    fun getByAquariumId(aquariumId: String): Flow<List<TaskExecution>> =
        dao.getByAquariumId(aquariumId).map { list -> list.map { it.toDomain() } }

    fun getRecent(limit: Int = 50): Flow<List<TaskExecution>> =
        dao.getRecent(limit).map { list -> list.map { it.toDomain() } }

    suspend fun getById(id: String): TaskExecution? =
        dao.getById(id)?.toDomain()

    suspend fun upsert(execution: TaskExecution) =
        dao.upsert(TaskExecutionEntity.fromDomain(execution))

    suspend fun delete(execution: TaskExecution) =
        dao.delete(TaskExecutionEntity.fromDomain(execution))

    suspend fun deleteById(id: String) =
        dao.deleteById(id)
}
