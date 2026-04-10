package com.keepaside.aquapt.core.repository

import com.keepaside.aquapt.core.database.AssetEntity
import com.keepaside.aquapt.core.database.dao.AssetDao
import com.keepaside.aquapt.core.model.Asset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AssetRepository(
    private val dao: AssetDao
) {
    fun getAll(): Flow<List<Asset>> =
        dao.getAll().map { list -> list.map { it.toDomain() } }

    fun getByAquariumId(aquariumId: String): Flow<List<Asset>> =
        dao.getByAquariumId(aquariumId).map { list -> list.map { it.toDomain() } }

    suspend fun getById(id: String): Asset? =
        dao.getById(id)?.toDomain()

    suspend fun upsert(asset: Asset) =
        dao.upsert(AssetEntity.fromDomain(asset))

    suspend fun delete(asset: Asset) =
        dao.delete(AssetEntity.fromDomain(asset))

    suspend fun deleteById(id: String) =
        dao.deleteById(id)
}
