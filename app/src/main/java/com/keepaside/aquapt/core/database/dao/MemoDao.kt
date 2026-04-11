package com.keepaside.aquapt.core.database.dao

import androidx.room.*
import com.keepaside.aquapt.core.database.MemoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoDao {
    @Query("SELECT * FROM memos WHERE aquariumId = :aquariumId ORDER BY createdAt DESC")
    fun getByAquariumId(aquariumId: String): Flow<List<MemoEntity>>

    @Query("SELECT * FROM memos ORDER BY createdAt DESC")
    fun getAll(): Flow<List<MemoEntity>>

    @Query("SELECT * FROM memos WHERE id = :id")
    suspend fun getById(id: String): MemoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MemoEntity)

    @Delete
    suspend fun delete(entity: MemoEntity)

    @Query("DELETE FROM memos WHERE id = :id")
    suspend fun deleteById(id: String)
}
