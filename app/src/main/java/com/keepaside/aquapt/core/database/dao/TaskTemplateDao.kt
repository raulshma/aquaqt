package com.keepaside.aquapt.core.database.dao

import androidx.room.*
import com.keepaside.aquapt.core.database.TaskTemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskTemplateDao {
    @Query("SELECT * FROM task_templates ORDER BY title ASC")
    fun getAll(): Flow<List<TaskTemplateEntity>>

    @Query("SELECT * FROM task_templates WHERE aquariumId = :aquariumId ORDER BY title ASC")
    fun getByAquariumId(aquariumId: String): Flow<List<TaskTemplateEntity>>

    @Query("SELECT * FROM task_templates WHERE id = :id")
    suspend fun getById(id: String): TaskTemplateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TaskTemplateEntity)

    @Delete
    suspend fun delete(entity: TaskTemplateEntity)

    @Query("DELETE FROM task_templates WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE task_templates SET reminderGroupId = NULL WHERE reminderGroupId = :reminderGroupId")
    suspend fun clearReminderGroup(reminderGroupId: String): Int
}
