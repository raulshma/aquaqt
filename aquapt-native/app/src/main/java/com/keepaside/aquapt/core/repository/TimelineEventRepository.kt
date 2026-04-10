package com.keepaside.aquapt.core.repository

import com.keepaside.aquapt.core.database.TimelineEventEntity
import com.keepaside.aquapt.core.database.dao.TimelineEventDao
import com.keepaside.aquapt.core.model.TimelineEvent
import com.keepaside.aquapt.core.model.TimelineEventType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TimelineEventRepository(
    private val dao: TimelineEventDao
) {
    fun getAll(): Flow<List<TimelineEvent>> =
        dao.getAll().map { list -> list.map { it.toDomain() } }

    fun getByAquariumId(aquariumId: String): Flow<List<TimelineEvent>> =
        dao.getByAquariumId(aquariumId).map { list -> list.map { it.toDomain() } }

    fun getByType(type: TimelineEventType): Flow<List<TimelineEvent>> =
        dao.getByType(type.name).map { list -> list.map { it.toDomain() } }

    fun getByAquariumIdAndType(aquariumId: String, type: TimelineEventType): Flow<List<TimelineEvent>> =
        dao.getByAquariumIdAndType(aquariumId, type.name).map { list -> list.map { it.toDomain() } }

    suspend fun getById(id: String): TimelineEvent? =
        dao.getById(id)?.toDomain()

    suspend fun upsert(event: TimelineEvent) =
        dao.upsert(TimelineEventEntity.fromDomain(event))

    suspend fun delete(event: TimelineEvent) =
        dao.delete(TimelineEventEntity.fromDomain(event))

    suspend fun deleteById(id: String) =
        dao.deleteById(id)
}
