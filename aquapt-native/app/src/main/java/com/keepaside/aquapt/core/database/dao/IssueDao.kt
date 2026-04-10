package com.keepaside.aquapt.core.database.dao

import androidx.room.*
import com.keepaside.aquapt.core.database.IssueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IssueDao {
    @Query("SELECT * FROM issues WHERE aquariumId = :aquariumId ORDER BY createdAt DESC")
    fun getByAquariumId(aquariumId: String): Flow<List<IssueEntity>>

    @Query("SELECT * FROM issues WHERE status != 'RESOLVED' ORDER BY createdAt DESC")
    fun getOpen(): Flow<List<IssueEntity>>

    @Query("SELECT * FROM issues WHERE id = :id")
    suspend fun getById(id: String): IssueEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: IssueEntity)

    @Delete
    suspend fun delete(entity: IssueEntity)

    @Query("DELETE FROM issues WHERE id = :id")
    suspend fun deleteById(id: String)
}
