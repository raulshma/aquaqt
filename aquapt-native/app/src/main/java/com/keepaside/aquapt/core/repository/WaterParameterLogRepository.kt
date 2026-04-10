package com.keepaside.aquapt.core.repository

import com.keepaside.aquapt.core.database.WaterParameterLogEntity
import com.keepaside.aquapt.core.database.dao.WaterParameterLogDao
import com.keepaside.aquapt.core.model.WaterParameterLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class WaterParameterLogRepository(
    private val dao: WaterParameterLogDao
) {
    fun getByAquariumId(aquariumId: String): Flow<List<WaterParameterLog>> =
        dao.getByAquariumId(aquariumId).map { list -> list.map { it.toDomain() } }

    suspend fun getRecentByAquariumId(aquariumId: String, limit: Int = 30): List<WaterParameterLog> =
        dao.getRecentByAquariumId(aquariumId, limit).map { it.toDomain() }

    suspend fun getById(id: String): WaterParameterLog? =
        dao.getById(id)?.toDomain()

    suspend fun upsert(log: WaterParameterLog) =
        dao.upsert(WaterParameterLogEntity.fromDomain(log))

    suspend fun delete(log: WaterParameterLog) =
        dao.delete(WaterParameterLogEntity.fromDomain(log))

    suspend fun deleteById(id: String) =
        dao.deleteById(id)
}
