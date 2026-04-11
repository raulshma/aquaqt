package com.keepaside.aquapt.core.repository

import com.keepaside.aquapt.core.database.DosingLogEntity
import com.keepaside.aquapt.core.database.dao.DosingLogDao
import com.keepaside.aquapt.core.model.DosingLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DosingLogRepository(
    private val dao: DosingLogDao
) {
    fun getAll(): Flow<List<DosingLog>> =
        dao.getAll().map { list -> list.map { it.toDomain() } }

    fun getByAquariumId(aquariumId: String): Flow<List<DosingLog>> =
        dao.getByAquariumId(aquariumId).map { list -> list.map { it.toDomain() } }

    suspend fun getById(id: String): DosingLog? =
        dao.getById(id)?.toDomain()

    suspend fun upsert(log: DosingLog) =
        dao.upsert(DosingLogEntity.fromDomain(log))

    suspend fun delete(log: DosingLog) =
        dao.delete(DosingLogEntity.fromDomain(log))

    suspend fun deleteById(id: String) =
        dao.deleteById(id)
}
