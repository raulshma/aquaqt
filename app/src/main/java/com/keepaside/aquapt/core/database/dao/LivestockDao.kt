package com.keepaside.aquapt.core.database.dao

import androidx.room.*
import com.keepaside.aquapt.core.database.LivestockEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LivestockDao {
    @Query("SELECT * FROM livestock WHERE aquariumId = :aquariumId ORDER BY name ASC")
    fun getByAquariumId(aquariumId: String): Flow<List<LivestockEntity>>

    @Query("SELECT * FROM livestock ORDER BY name ASC")
    fun getAll(): Flow<List<LivestockEntity>>

    @Query("SELECT * FROM livestock WHERE id = :id")
    suspend fun getById(id: String): LivestockEntity?

    @Query("SELECT * FROM livestock WHERE parentId = :parentId")
    fun getOffspring(parentId: String): Flow<List<LivestockEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LivestockEntity)

    @Delete
    suspend fun delete(entity: LivestockEntity)

    @Query("DELETE FROM livestock WHERE id = :id")
    suspend fun deleteById(id: String)
}
