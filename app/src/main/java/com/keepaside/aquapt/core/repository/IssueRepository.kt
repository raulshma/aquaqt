package com.keepaside.aquapt.core.repository

import com.keepaside.aquapt.core.database.IssueEntity
import com.keepaside.aquapt.core.database.dao.IssueDao
import com.keepaside.aquapt.core.model.Issue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class IssueRepository(
    private val dao: IssueDao
) {
    fun getAll(): Flow<List<Issue>> =
        dao.getAll().map { list -> list.map { it.toDomain() } }

    fun getByAquariumId(aquariumId: String): Flow<List<Issue>> =
        dao.getByAquariumId(aquariumId).map { list -> list.map { it.toDomain() } }

    fun getOpen(): Flow<List<Issue>> =
        dao.getOpen().map { list -> list.map { it.toDomain() } }

    suspend fun getById(id: String): Issue? =
        dao.getById(id)?.toDomain()

    suspend fun upsert(issue: Issue) =
        dao.upsert(IssueEntity.fromDomain(issue))

    suspend fun delete(issue: Issue) =
        dao.delete(IssueEntity.fromDomain(issue))

    suspend fun deleteById(id: String) =
        dao.deleteById(id)
}
