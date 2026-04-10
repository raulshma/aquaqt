package com.keepaside.aquapt.core.database.dao

import androidx.room.*
import com.keepaside.aquapt.core.database.ConsumableEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConsumableDao {
    @Query("SELECT * FROM consumables WHERE aquariumId = :aquariumId ORDER BY name ASC")
    fun getByAquariumId(aquariumId: String): Flow<List<ConsumableEntity>>

    @Query("SELECT * FROM consumables ORDER BY name ASC")
    fun getAll(): Flow<List<ConsumableEntity>>

    @Query("SELECT * FROM consumables WHERE id = :id")
    suspend fun getById(id: String): ConsumableEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ConsumableEntity)

    @Delete
    suspend fun delete(entity: ConsumableEntity)

    @Query("DELETE FROM consumables WHERE id = :id")
    suspend fun deleteById(id: String)
}
