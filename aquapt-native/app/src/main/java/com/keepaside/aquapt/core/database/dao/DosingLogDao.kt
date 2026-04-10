package com.keepaside.aquapt.core.database.dao

import androidx.room.*
import com.keepaside.aquapt.core.database.DosingLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DosingLogDao {
    @Query("SELECT * FROM dosing_logs ORDER BY createdAt DESC")
    fun getAll(): Flow<List<DosingLogEntity>>

    @Query("SELECT * FROM dosing_logs WHERE aquariumId = :aquariumId ORDER BY createdAt DESC")
    fun getByAquariumId(aquariumId: String): Flow<List<DosingLogEntity>>

    @Query("SELECT * FROM dosing_logs WHERE id = :id")
    suspend fun getById(id: String): DosingLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DosingLogEntity)

    @Delete
    suspend fun delete(entity: DosingLogEntity)

    @Query("DELETE FROM dosing_logs WHERE id = :id")
    suspend fun deleteById(id: String)
}
