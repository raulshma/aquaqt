package com.keepaside.aquapt.core.database.dao

import androidx.room.*
import com.keepaside.aquapt.core.database.ReminderGroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderGroupDao {
    @Query("SELECT * FROM reminder_groups ORDER BY name ASC")
    fun getAll(): Flow<List<ReminderGroupEntity>>

    @Query("SELECT * FROM reminder_groups WHERE id = :id")
    suspend fun getById(id: String): ReminderGroupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ReminderGroupEntity)

    @Delete
    suspend fun delete(entity: ReminderGroupEntity)

    @Query("DELETE FROM reminder_groups WHERE id = :id")
    suspend fun deleteById(id: String)
}
