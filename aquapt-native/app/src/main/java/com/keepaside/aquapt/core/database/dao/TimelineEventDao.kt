package com.keepaside.aquapt.core.database.dao

import androidx.room.*
import com.keepaside.aquapt.core.database.TimelineEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TimelineEventDao {
    @Query("SELECT * FROM timeline_events ORDER BY createdAt DESC")
    fun getAll(): Flow<List<TimelineEventEntity>>

    @Query("SELECT * FROM timeline_events WHERE aquariumId = :aquariumId ORDER BY createdAt DESC")
    fun getByAquariumId(aquariumId: String): Flow<List<TimelineEventEntity>>

    @Query("SELECT * FROM timeline_events WHERE type = :type ORDER BY createdAt DESC")
    fun getByType(type: String): Flow<List<TimelineEventEntity>>

    @Query("SELECT * FROM timeline_events WHERE aquariumId = :aquariumId AND type = :type ORDER BY createdAt DESC")
    fun getByAquariumIdAndType(aquariumId: String, type: String): Flow<List<TimelineEventEntity>>

    @Query("SELECT * FROM timeline_events WHERE id = :id")
    suspend fun getById(id: String): TimelineEventEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TimelineEventEntity)

    @Delete
    suspend fun delete(entity: TimelineEventEntity)

    @Query("DELETE FROM timeline_events WHERE id = :id")
    suspend fun deleteById(id: String)
}
