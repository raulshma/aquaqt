package com.keepaside.aquapt.core.repository

import com.keepaside.aquapt.core.database.LivestockEntity
import com.keepaside.aquapt.core.database.dao.LivestockDao
import com.keepaside.aquapt.core.model.Livestock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LivestockRepository(
    private val dao: LivestockDao
) {
    fun getByAquariumId(aquariumId: String): Flow<List<Livestock>> =
        dao.getByAquariumId(aquariumId).map { list -> list.map { it.toDomain() } }

    fun getAll(): Flow<List<Livestock>> =
        dao.getAll().map { list -> list.map { it.toDomain() } }

    fun getOffspring(parentId: String): Flow<List<Livestock>> =
        dao.getOffspring(parentId).map { list -> list.map { it.toDomain() } }

    suspend fun getById(id: String): Livestock? =
        dao.getById(id)?.toDomain()

    suspend fun upsert(livestock: Livestock) =
        dao.upsert(LivestockEntity.fromDomain(livestock))

    suspend fun delete(livestock: Livestock) =
        dao.delete(LivestockEntity.fromDomain(livestock))

    suspend fun deleteById(id: String) =
        dao.deleteById(id)
}
