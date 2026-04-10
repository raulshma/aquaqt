package com.keepaside.aquapt.core.repository

import com.keepaside.aquapt.core.database.MemoEntity
import com.keepaside.aquapt.core.database.dao.MemoDao
import com.keepaside.aquapt.core.model.Memo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MemoRepository(
    private val dao: MemoDao
) {
    fun getByAquariumId(aquariumId: String): Flow<List<Memo>> =
        dao.getByAquariumId(aquariumId).map { list -> list.map { it.toDomain() } }

    fun getAll(): Flow<List<Memo>> =
        dao.getAll().map { list -> list.map { it.toDomain() } }

    suspend fun getById(id: String): Memo? =
        dao.getById(id)?.toDomain()

    suspend fun upsert(memo: Memo) =
        dao.upsert(MemoEntity.fromDomain(memo))

    suspend fun delete(memo: Memo) =
        dao.delete(MemoEntity.fromDomain(memo))

    suspend fun deleteById(id: String) =
        dao.deleteById(id)
}
