package com.keepaside.aquapt.core.repository

import com.keepaside.aquapt.core.database.ConsumableEntity
import com.keepaside.aquapt.core.database.dao.ConsumableDao
import com.keepaside.aquapt.core.model.Consumable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ConsumableRepository(
    private val dao: ConsumableDao
) {
    fun getByAquariumId(aquariumId: String): Flow<List<Consumable>> =
        dao.getByAquariumId(aquariumId).map { list -> list.map { it.toDomain() } }

    fun getAll(): Flow<List<Consumable>> =
        dao.getAll().map { list -> list.map { it.toDomain() } }

    suspend fun getById(id: String): Consumable? =
        dao.getById(id)?.toDomain()

    suspend fun upsert(consumable: Consumable) =
        dao.upsert(ConsumableEntity.fromDomain(consumable))

    suspend fun delete(consumable: Consumable) =
        dao.delete(ConsumableEntity.fromDomain(consumable))

    suspend fun deleteById(id: String) =
        dao.deleteById(id)
}
