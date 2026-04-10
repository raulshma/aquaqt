package com.keepaside.aquapt.core.database

import androidx.room.TypeConverter
import com.keepaside.aquapt.core.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object AquaPTConverters {

    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromWaterType(value: WaterType): String = value.name

    @TypeConverter
    fun toWaterType(value: String): WaterType = WaterType.valueOf(value)

    @TypeConverter
    fun fromIssueStatus(value: IssueStatus): String = value.name

    @TypeConverter
    fun toIssueStatus(value: String): IssueStatus = IssueStatus.valueOf(value)

    @TypeConverter
    fun fromTaskFrequency(value: TaskFrequency): String = value.serialize()

    @TypeConverter
    fun toTaskFrequency(value: String): TaskFrequency = TaskFrequency.parse(value)

    @TypeConverter
    fun fromTaskCategory(value: TaskCategory): String = value.name

    @TypeConverter
    fun toTaskCategory(value: String): TaskCategory = TaskCategory.valueOf(value)

    @TypeConverter
    fun fromLivestockKind(value: LivestockKind): String = value.name

    @TypeConverter
    fun toLivestockKind(value: String): LivestockKind = LivestockKind.valueOf(value)

    @TypeConverter
    fun fromLivestockStatus(value: LivestockStatus): String = value.name

    @TypeConverter
    fun toLivestockStatus(value: String): LivestockStatus = LivestockStatus.valueOf(value)

    @TypeConverter
    fun fromAssetCategory(value: AssetCategory): String = value.name

    @TypeConverter
    fun toAssetCategory(value: String): AssetCategory = AssetCategory.valueOf(value)

    @TypeConverter
    fun fromConsumableUnit(value: ConsumableUnit): String = value.name

    @TypeConverter
    fun toConsumableUnit(value: String): ConsumableUnit = ConsumableUnit.valueOf(value)

    @TypeConverter
    fun fromTimelineEventType(value: TimelineEventType): String = value.name

    @TypeConverter
    fun toTimelineEventType(value: String): TimelineEventType = TimelineEventType.valueOf(value)

    @TypeConverter
    fun fromEntityKind(value: EntityKind): String = value.name

    @TypeConverter
    fun toEntityKind(value: String): EntityKind = EntityKind.valueOf(value)

    @TypeConverter
    fun fromAppThemePreference(value: AppThemePreference): String = value.name

    @TypeConverter
    fun toAppThemePreference(value: String): AppThemePreference = AppThemePreference.valueOf(value)

    @TypeConverter
    fun fromRegionalPreferencesMode(value: RegionalPreferencesMode): String = value.name

    @TypeConverter
    fun toRegionalPreferencesMode(value: String): RegionalPreferencesMode =
        RegionalPreferencesMode.valueOf(value)

    @TypeConverter
    fun fromStringList(value: List<String>): String = json.encodeToString(value)

    @TypeConverter
    fun toStringList(value: String): List<String> = json.decodeFromString(value)

    @TypeConverter
    fun fromIntList(value: List<Int>): String = json.encodeToString(value)

    @TypeConverter
    fun toIntList(value: String): List<Int> = json.decodeFromString(value)

    @TypeConverter
    fun fromWaterParameters(value: WaterParameters): String = json.encodeToString(value)

    @TypeConverter
    fun toWaterParameters(value: String): WaterParameters = json.decodeFromString(value)
}
