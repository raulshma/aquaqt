package com.keepaside.aquapt.core.database.dao

import androidx.room.*
import com.keepaside.aquapt.core.database.TaskExecutionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskExecutionDao {
    @Query("SELECT * FROM task_executions ORDER BY completedAt DESC")
    fun getAll(): Flow<List<TaskExecutionEntity>>

    @Query("SELECT * FROM task_executions WHERE taskTemplateId = :templateId ORDER BY completedAt DESC")
    fun getByTemplateId(templateId: String): Flow<List<TaskExecutionEntity>>

    @Query("SELECT * FROM task_executions WHERE aquariumId = :aquariumId ORDER BY completedAt DESC")
    fun getByAquariumId(aquariumId: String): Flow<List<TaskExecutionEntity>>

    @Query("SELECT * FROM task_executions ORDER BY completedAt DESC LIMIT :limit")
    fun getRecent(limit: Int = 50): Flow<List<TaskExecutionEntity>>

    @Query("SELECT * FROM task_executions WHERE id = :id")
    suspend fun getById(id: String): TaskExecutionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TaskExecutionEntity)

    @Delete
    suspend fun delete(entity: TaskExecutionEntity)

    @Query("DELETE FROM task_executions WHERE id = :id")
    suspend fun deleteById(id: String)
}
