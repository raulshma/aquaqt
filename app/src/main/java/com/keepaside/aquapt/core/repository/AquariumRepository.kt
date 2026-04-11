package com.keepaside.aquapt.core.repository

import com.keepaside.aquapt.core.database.AquariumEntity
import com.keepaside.aquapt.core.database.dao.AquariumDao
import com.keepaside.aquapt.core.model.Aquarium
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AquariumRepository(
    private val dao: AquariumDao
) {
    fun getAll(): Flow<List<Aquarium>> =
        dao.getAll().map { list -> list.map { it.toDomain() } }

    fun observeById(id: String): Flow<Aquarium?> =
        dao.observeById(id).map { it?.toDomain() }

    suspend fun getById(id: String): Aquarium? =
        dao.getById(id)?.toDomain()

    suspend fun upsert(aquarium: Aquarium) =
        dao.upsert(AquariumEntity.fromDomain(aquarium))

    suspend fun delete(aquarium: Aquarium) =
        dao.delete(AquariumEntity.fromDomain(aquarium))

    suspend fun deleteById(id: String) =
        dao.deleteById(id)
}
