package com.keepaside.aquapt.core.database.dao

import androidx.room.*
import com.keepaside.aquapt.core.database.WaterParameterLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WaterParameterLogDao {
    @Query("SELECT * FROM parameter_logs WHERE aquariumId = :aquariumId ORDER BY createdAt DESC")
    fun getByAquariumId(aquariumId: String): Flow<List<WaterParameterLogEntity>>

    @Query("SELECT * FROM parameter_logs WHERE aquariumId = :aquariumId ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecentByAquariumId(aquariumId: String, limit: Int = 30): List<WaterParameterLogEntity>

    @Query("SELECT * FROM parameter_logs WHERE id = :id")
    suspend fun getById(id: String): WaterParameterLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WaterParameterLogEntity)

    @Delete
    suspend fun delete(entity: WaterParameterLogEntity)

    @Query("DELETE FROM parameter_logs WHERE id = :id")
    suspend fun deleteById(id: String)
}
