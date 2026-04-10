package com.keepaside.aquapt.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.keepaside.aquapt.core.database.dao.*

@Database(
    entities = [
        AquariumEntity::class,
        LivestockEntity::class,
        WaterParameterLogEntity::class,
        TaskTemplateEntity::class,
        TaskExecutionEntity::class,
        ReminderGroupEntity::class,
        DosingLogEntity::class,
        AssetEntity::class,
        ConsumableEntity::class,
        IssueEntity::class,
        MemoEntity::class,
        TimelineEventEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(AquaPTConverters::class)
abstract class AquaPTDatabase : RoomDatabase() {
    abstract fun aquariumDao(): AquariumDao
    abstract fun livestockDao(): LivestockDao
    abstract fun taskTemplateDao(): TaskTemplateDao
    abstract fun taskExecutionDao(): TaskExecutionDao
    abstract fun waterParameterLogDao(): WaterParameterLogDao
    abstract fun reminderGroupDao(): ReminderGroupDao
    abstract fun dosingLogDao(): DosingLogDao
    abstract fun assetDao(): AssetDao
    abstract fun consumableDao(): ConsumableDao
    abstract fun issueDao(): IssueDao
    abstract fun memoDao(): MemoDao
    abstract fun timelineEventDao(): TimelineEventDao
}
