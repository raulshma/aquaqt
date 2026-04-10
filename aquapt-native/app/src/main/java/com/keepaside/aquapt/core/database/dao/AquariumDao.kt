package com.keepaside.aquapt.core.database.dao

import androidx.room.*
import com.keepaside.aquapt.core.database.AquariumEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AquariumDao {
    @Query("SELECT * FROM aquariums ORDER BY name ASC")
    fun getAll(): Flow<List<AquariumEntity>>

    @Query("SELECT * FROM aquariums WHERE id = :id")
    suspend fun getById(id: String): AquariumEntity?

    @Query("SELECT * FROM aquariums WHERE id = :id")
    fun observeById(id: String): Flow<AquariumEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AquariumEntity)

    @Delete
    suspend fun delete(entity: AquariumEntity)

    @Query("DELETE FROM aquariums WHERE id = :id")
    suspend fun deleteById(id: String)
}
