package com.keepaside.aquapt.core.repository

import com.keepaside.aquapt.core.database.ReminderGroupEntity
import com.keepaside.aquapt.core.database.dao.ReminderGroupDao
import com.keepaside.aquapt.core.model.ReminderGroup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ReminderGroupRepository(
    private val dao: ReminderGroupDao
) {
    fun getAll(): Flow<List<ReminderGroup>> =
        dao.getAll().map { list -> list.map { it.toDomain() } }

    suspend fun getById(id: String): ReminderGroup? =
        dao.getById(id)?.toDomain()

    suspend fun upsert(group: ReminderGroup) =
        dao.upsert(ReminderGroupEntity.fromDomain(group))

    suspend fun delete(group: ReminderGroup) =
        dao.delete(ReminderGroupEntity.fromDomain(group))

    suspend fun deleteById(id: String) =
        dao.deleteById(id)
}
