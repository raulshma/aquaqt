package com.keepaside.aquapt.core.backup

import com.keepaside.aquapt.core.model.AppSettings
import com.keepaside.aquapt.core.model.AppThemePreference
import com.keepaside.aquapt.core.model.Aquarium
import com.keepaside.aquapt.core.model.Asset
import com.keepaside.aquapt.core.model.AssetCategory
import com.keepaside.aquapt.core.model.Consumable
import com.keepaside.aquapt.core.model.ConsumableUnit
import com.keepaside.aquapt.core.model.DosingLog
import com.keepaside.aquapt.core.model.EntityKind
import com.keepaside.aquapt.core.model.EntityRef
import com.keepaside.aquapt.core.model.Issue
import com.keepaside.aquapt.core.model.IssueStatus
import com.keepaside.aquapt.core.model.Livestock
import com.keepaside.aquapt.core.model.LivestockKind
import com.keepaside.aquapt.core.model.LivestockStatus
import com.keepaside.aquapt.core.model.Memo
import com.keepaside.aquapt.core.model.RegionalPreferencesMode
import com.keepaside.aquapt.core.model.ReminderGroup
import com.keepaside.aquapt.core.model.TaskCategory
import com.keepaside.aquapt.core.model.TaskExecution
import com.keepaside.aquapt.core.model.TaskFrequency
import com.keepaside.aquapt.core.model.TaskTemplate
import com.keepaside.aquapt.core.model.TimelineEvent
import com.keepaside.aquapt.core.model.TimelineEventType
import com.keepaside.aquapt.core.model.WaterParameterLog
import com.keepaside.aquapt.core.model.WaterParameters
import com.keepaside.aquapt.core.model.WaterType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class PersistedAppStateSnapshot(
    val aquariums: List<Aquarium> = emptyList(),
    val taskTemplates: List<TaskTemplate> = emptyList(),
    val livestock: List<Livestock> = emptyList(),
    val taskExecutions: List<TaskExecution> = emptyList(),
    val dosingLogs: List<DosingLog> = emptyList(),
    val assets: List<Asset> = emptyList(),
    val consumables: List<Consumable> = emptyList(),
    val parameterLogs: List<WaterParameterLog> = emptyList(),
    val issues: List<Issue> = emptyList(),
    val memos: List<Memo> = emptyList(),
    val timeline: List<TimelineEvent> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val reminderGroups: List<ReminderGroup> = emptyList()
)

object AppStateJsonCompatibility {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val prettyJson = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    fun decode(payload: String): PersistedAppStateSnapshot {
        val dto = json.decodeFromString(PersistedAppStateDto.serializer(), payload)
        return dto.toDomain()
    }

    fun encode(snapshot: PersistedAppStateSnapshot, pretty: Boolean = true): String {
        val dto = PersistedAppStateDto.fromDomain(snapshot)
        return (if (pretty) prettyJson else json).encodeToString(PersistedAppStateDto.serializer(), dto)
    }
}

@Serializable
private data class PersistedAppStateDto(
    val aquariums: List<AquariumDto> = emptyList(),
    val taskTemplates: List<TaskTemplateDto> = emptyList(),
    val livestock: List<LivestockDto> = emptyList(),
    val taskExecutions: List<TaskExecutionDto> = emptyList(),
    val dosingLogs: List<DosingLogDto> = emptyList(),
    val assets: List<AssetDto> = emptyList(),
    val consumables: List<ConsumableDto> = emptyList(),
    val parameterLogs: List<WaterParameterLogDto> = emptyList(),
    val issues: List<IssueDto> = emptyList(),
    val memos: List<MemoDto> = emptyList(),
    val timeline: List<TimelineEventDto> = emptyList(),
    val settings: AppSettingsDto = AppSettingsDto(),
    val reminderGroups: List<ReminderGroupDto> = emptyList()
) {
    fun toDomain(): PersistedAppStateSnapshot = PersistedAppStateSnapshot(
        aquariums = aquariums.map { it.toDomain() },
        taskTemplates = taskTemplates.map { it.toDomain() },
        livestock = livestock.map { it.toDomain() },
        taskExecutions = taskExecutions.map { it.toDomain() },
        dosingLogs = dosingLogs.map { it.toDomain() },
        assets = assets.map { it.toDomain() },
        consumables = consumables.map { it.toDomain() },
        parameterLogs = parameterLogs.map { it.toDomain() },
        issues = issues.map { it.toDomain() },
        memos = memos.map { it.toDomain() },
        timeline = timeline.map { it.toDomain() },
        settings = settings.toDomain(),
        reminderGroups = reminderGroups.map { it.toDomain() }
    )

    companion object {
        fun fromDomain(domain: PersistedAppStateSnapshot): PersistedAppStateDto = PersistedAppStateDto(
            aquariums = domain.aquariums.map { AquariumDto.fromDomain(it) },
            taskTemplates = domain.taskTemplates.map { TaskTemplateDto.fromDomain(it) },
            livestock = domain.livestock.map { LivestockDto.fromDomain(it) },
            taskExecutions = domain.taskExecutions.map { TaskExecutionDto.fromDomain(it) },
            dosingLogs = domain.dosingLogs.map { DosingLogDto.fromDomain(it) },
            assets = domain.assets.map { AssetDto.fromDomain(it) },
            consumables = domain.consumables.map { ConsumableDto.fromDomain(it) },
            parameterLogs = domain.parameterLogs.map { WaterParameterLogDto.fromDomain(it) },
            issues = domain.issues.map { IssueDto.fromDomain(it) },
            memos = domain.memos.map { MemoDto.fromDomain(it) },
            timeline = domain.timeline.map { TimelineEventDto.fromDomain(it) },
            settings = AppSettingsDto.fromDomain(domain.settings),
            reminderGroups = domain.reminderGroups.map { ReminderGroupDto.fromDomain(it) }
        )
    }
}

@Serializable
private data class AquariumDto(
    val id: String,
    val name: String,
    val volumeLiters: Double,
    val dimensions: String = "",
    val waterType: String = "freshwater",
    val setupDate: String = "",
    val investmentCost: Double? = null,
    val photoUri: String? = null
) {
    fun toDomain(): Aquarium = Aquarium(
        id = id,
        name = name,
        volumeLiters = volumeLiters,
        dimensions = dimensions,
        waterType = parseWaterType(waterType),
        setupDate = setupDate,
        investmentCost = investmentCost,
        photoUri = photoUri
    )

    companion object {
        fun fromDomain(domain: Aquarium): AquariumDto = AquariumDto(
            id = domain.id,
            name = domain.name,
            volumeLiters = domain.volumeLiters,
            dimensions = domain.dimensions,
            waterType = domain.waterType.toCompatString(),
            setupDate = domain.setupDate,
            investmentCost = domain.investmentCost,
            photoUri = domain.photoUri
        )
    }
}

@Serializable
private data class TaskTemplateDto(
    val id: String,
    val title: String,
    val description: String? = null,
    val category: String? = null,
    val livestockId: String? = null,
    val frequency: String = "daily",
    val aquariumIds: List<String> = emptyList(),
    val startDate: String? = null,
    val timesPerDay: Int? = null,
    val reminderHours: List<Int>? = null,
    val reminderGroupId: String? = null
) {
    fun toDomain(): TaskTemplate = TaskTemplate(
        id = id,
        title = title,
        description = description,
        category = parseTaskCategory(category),
        livestockId = livestockId,
        frequency = TaskFrequency.parse(frequency),
        aquariumIds = aquariumIds,
        startDate = startDate,
        timesPerDay = timesPerDay,
        reminderHours = reminderHours.orEmpty(),
        reminderGroupId = reminderGroupId
    )

    companion object {
        fun fromDomain(domain: TaskTemplate): TaskTemplateDto = TaskTemplateDto(
            id = domain.id,
            title = domain.title,
            description = domain.description,
            category = domain.category?.toCompatString(),
            livestockId = domain.livestockId,
            frequency = domain.frequency.serialize(),
            aquariumIds = domain.aquariumIds,
            startDate = domain.startDate,
            timesPerDay = domain.timesPerDay,
            reminderHours = domain.reminderHours,
            reminderGroupId = domain.reminderGroupId
        )
    }
}

@Serializable
private data class LivestockDto(
    val id: String,
    val aquariumId: String,
    val kind: String = "fish",
    val name: String,
    val species: String = "",
    val quantity: Int = 1,
    val acquiredAt: String = "",
    val purchasePrice: Double? = null,
    val photoUri: String? = null,
    val dietaryNotes: String? = null,
    val parentId: String? = null,
    val status: String? = null
) {
    fun toDomain(): Livestock = Livestock(
        id = id,
        aquariumId = aquariumId,
        kind = parseLivestockKind(kind),
        name = name,
        species = species,
        quantity = quantity,
        acquiredAt = acquiredAt,
        purchasePrice = purchasePrice,
        photoUri = photoUri,
        dietaryNotes = dietaryNotes,
        parentId = parentId,
        status = parseLivestockStatus(status)
    )

    companion object {
        fun fromDomain(domain: Livestock): LivestockDto = LivestockDto(
            id = domain.id,
            aquariumId = domain.aquariumId,
            kind = domain.kind.toCompatString(),
            name = domain.name,
            species = domain.species,
            quantity = domain.quantity,
            acquiredAt = domain.acquiredAt,
            purchasePrice = domain.purchasePrice,
            photoUri = domain.photoUri,
            dietaryNotes = domain.dietaryNotes,
            parentId = domain.parentId,
            status = domain.status.toCompatString()
        )
    }
}

@Serializable
private data class TaskExecutionDto(
    val id: String,
    val taskTemplateId: String,
    val aquariumId: String,
    val completedAt: String,
    val note: String? = null
) {
    fun toDomain(): TaskExecution = TaskExecution(
        id = id,
        taskTemplateId = taskTemplateId,
        aquariumId = aquariumId,
        completedAt = completedAt,
        note = note
    )

    companion object {
        fun fromDomain(domain: TaskExecution): TaskExecutionDto = TaskExecutionDto(
            id = domain.id,
            taskTemplateId = domain.taskTemplateId,
            aquariumId = domain.aquariumId,
            completedAt = domain.completedAt,
            note = domain.note
        )
    }
}

@Serializable
private data class DosingLogDto(
    val id: String,
    val aquariumId: String,
    val product: String,
    val amountMl: Double,
    val createdAt: String,
    val note: String? = null
) {
    fun toDomain(): DosingLog = DosingLog(
        id = id,
        aquariumId = aquariumId,
        product = product,
        amountMl = amountMl,
        createdAt = createdAt,
        note = note
    )

    companion object {
        fun fromDomain(domain: DosingLog): DosingLogDto = DosingLogDto(
            id = domain.id,
            aquariumId = domain.aquariumId,
            product = domain.product,
            amountMl = domain.amountMl,
            createdAt = domain.createdAt,
            note = domain.note
        )
    }
}

@Serializable
private data class AssetDto(
    val id: String,
    val aquariumId: String,
    val category: String = "other",
    val brandModel: String,
    val purchasedAt: String? = null,
    val price: Double? = null,
    val maintenanceTaskTemplateIds: List<String>? = null,
    val photoUri: String? = null
) {
    fun toDomain(): Asset = Asset(
        id = id,
        aquariumId = aquariumId,
        category = parseAssetCategory(category),
        brandModel = brandModel,
        purchasedAt = purchasedAt,
        price = price,
        maintenanceTaskTemplateIds = maintenanceTaskTemplateIds.orEmpty(),
        photoUri = photoUri
    )

    companion object {
        fun fromDomain(domain: Asset): AssetDto = AssetDto(
            id = domain.id,
            aquariumId = domain.aquariumId,
            category = domain.category.toCompatString(),
            brandModel = domain.brandModel,
            purchasedAt = domain.purchasedAt,
            price = domain.price,
            maintenanceTaskTemplateIds = domain.maintenanceTaskTemplateIds,
            photoUri = domain.photoUri
        )
    }
}

@Serializable
private data class ConsumableDto(
    val id: String,
    val aquariumId: String,
    val name: String,
    val unit: String = "ml",
    val remaining: Double,
    val reorderAt: Double? = null,
    val updatedAt: String,
    val photoUri: String? = null
) {
    fun toDomain(): Consumable = Consumable(
        id = id,
        aquariumId = aquariumId,
        name = name,
        unit = parseConsumableUnit(unit),
        remaining = remaining,
        reorderAt = reorderAt,
        updatedAt = updatedAt,
        photoUri = photoUri
    )

    companion object {
        fun fromDomain(domain: Consumable): ConsumableDto = ConsumableDto(
            id = domain.id,
            aquariumId = domain.aquariumId,
            name = domain.name,
            unit = domain.unit.toCompatString(),
            remaining = domain.remaining,
            reorderAt = domain.reorderAt,
            updatedAt = domain.updatedAt,
            photoUri = domain.photoUri
        )
    }
}

@Serializable
private data class WaterParameterLogDto(
    val id: String,
    val aquariumId: String,
    val createdAt: String,
    val values: WaterParameters = WaterParameters()
) {
    fun toDomain(): WaterParameterLog = WaterParameterLog(
        id = id,
        aquariumId = aquariumId,
        createdAt = createdAt,
        values = values
    )

    companion object {
        fun fromDomain(domain: WaterParameterLog): WaterParameterLogDto = WaterParameterLogDto(
            id = domain.id,
            aquariumId = domain.aquariumId,
            createdAt = domain.createdAt,
            values = domain.values
        )
    }
}

@Serializable
private data class IssueDto(
    val id: String,
    val aquariumId: String,
    val title: String,
    val status: String = "open",
    val createdAt: String,
    val resolutionNote: String? = null
) {
    fun toDomain(): Issue = Issue(
        id = id,
        aquariumId = aquariumId,
        title = title,
        status = parseIssueStatus(status),
        createdAt = createdAt,
        resolutionNote = resolutionNote
    )

    companion object {
        fun fromDomain(domain: Issue): IssueDto = IssueDto(
            id = domain.id,
            aquariumId = domain.aquariumId,
            title = domain.title,
            status = domain.status.toCompatString(),
            createdAt = domain.createdAt,
            resolutionNote = domain.resolutionNote
        )
    }
}

@Serializable
private data class MemoDto(
    val id: String,
    val aquariumId: String,
    val content: String,
    val createdAt: String,
    val photoUri: String? = null
) {
    fun toDomain(): Memo = Memo(
        id = id,
        aquariumId = aquariumId,
        content = content,
        createdAt = createdAt,
        photoUri = photoUri
    )

    companion object {
        fun fromDomain(domain: Memo): MemoDto = MemoDto(
            id = domain.id,
            aquariumId = domain.aquariumId,
            content = domain.content,
            createdAt = domain.createdAt,
            photoUri = domain.photoUri
        )
    }
}

@Serializable
private data class TimelineEventDto(
    val id: String,
    val aquariumId: String,
    val type: String,
    val createdAt: String,
    val title: String,
    val description: String? = null,
    val photoUri: String? = null,
    val source: EntityRefDto? = null,
    val related: List<EntityRefDto>? = null
) {
    fun toDomain(): TimelineEvent = TimelineEvent(
        id = id,
        aquariumId = aquariumId,
        type = parseTimelineType(type),
        createdAt = createdAt,
        title = title,
        description = description,
        photoUri = photoUri,
        source = source?.toDomain(),
        related = related.orEmpty().map { it.toDomain() }
    )

    companion object {
        fun fromDomain(domain: TimelineEvent): TimelineEventDto = TimelineEventDto(
            id = domain.id,
            aquariumId = domain.aquariumId,
            type = domain.type.toCompatString(),
            createdAt = domain.createdAt,
            title = domain.title,
            description = domain.description,
            photoUri = domain.photoUri,
            source = domain.source?.let { EntityRefDto.fromDomain(it) },
            related = domain.related.map { EntityRefDto.fromDomain(it) }
        )
    }
}

@Serializable
private data class EntityRefDto(
    val kind: String,
    val id: String,
    val aquariumId: String? = null
) {
    fun toDomain(): EntityRef = EntityRef(
        kind = parseEntityKind(kind),
        id = id,
        aquariumId = aquariumId
    )

    companion object {
        fun fromDomain(domain: EntityRef): EntityRefDto = EntityRefDto(
            kind = domain.kind.toCompatString(),
            id = domain.id,
            aquariumId = domain.aquariumId
        )
    }
}

@Serializable
private data class ReminderGroupDto(
    val id: String,
    val name: String,
    val hours: List<Int> = emptyList()
) {
    fun toDomain(): ReminderGroup = ReminderGroup(
        id = id,
        name = name,
        hours = hours
    )

    companion object {
        fun fromDomain(domain: ReminderGroup): ReminderGroupDto = ReminderGroupDto(
            id = domain.id,
            name = domain.name,
            hours = domain.hours
        )
    }
}

@Serializable
private data class AppSettingsDto(
    val openRouterApiKey: String? = null,
    val aiModel: String? = null,
    val assistantMemoryModel: String? = null,
    val notificationsEnabled: Boolean? = null,
    val reminderHour: Int? = null,
    val reminderHours: List<Int>? = null,
    val assistantMemoryEnabled: Boolean? = null,
    val backupSyncEnabled: Boolean? = null,
    val backupSyncHour: Int? = null,
    val backupS3Endpoint: String? = null,
    val backupS3Region: String? = null,
    val backupS3Bucket: String? = null,
    val backupS3ObjectKey: String? = null,
    val backupS3ForcePathStyle: Boolean? = null,
    val backupUseVersionedKeys: Boolean? = null,
    val backupRetentionDays: Int? = null,
    val backupMasterKeySet: Boolean? = null,
    val backupS3CredentialsSet: Boolean? = null,
    val backupLastSyncedAt: String? = null,
    val backupLastRestoredAt: String? = null,
    val backupLastAutoSyncDate: String? = null,
    val backupLastError: String? = null,
    val themePreference: String? = null,
    val regionalPreferencesMode: String? = null,
    val defaultLocale: String? = null,
    val defaultTimezone: String? = null,
    val defaultCountryCode: String? = null,
    val defaultCountryName: String? = null,
    val defaultCurrency: String? = null
) {
    fun toDomain(defaults: AppSettings = AppSettings()): AppSettings {
        val normalizedHours = reminderHours
            ?.map { it.coerceIn(0, 23) }
            ?.distinct()
            ?.sorted()
            ?.takeIf { it.isNotEmpty() }
            ?: reminderHour?.let { listOf(it.coerceIn(0, 23)) }
            ?: defaults.reminderHours

        return AppSettings(
            openRouterApiKey = openRouterApiKey ?: defaults.openRouterApiKey,
            aiModel = aiModel ?: defaults.aiModel,
            assistantMemoryModel = assistantMemoryModel ?: aiModel ?: defaults.assistantMemoryModel,
            notificationsEnabled = notificationsEnabled ?: defaults.notificationsEnabled,
            reminderHours = normalizedHours,
            assistantMemoryEnabled = assistantMemoryEnabled ?: defaults.assistantMemoryEnabled,
            backupSyncEnabled = backupSyncEnabled ?: defaults.backupSyncEnabled,
            backupSyncHour = backupSyncHour ?: defaults.backupSyncHour,
            backupS3Endpoint = backupS3Endpoint ?: defaults.backupS3Endpoint,
            backupS3Region = backupS3Region ?: defaults.backupS3Region,
            backupS3Bucket = backupS3Bucket ?: defaults.backupS3Bucket,
            backupS3ObjectKey = backupS3ObjectKey ?: defaults.backupS3ObjectKey,
            backupS3ForcePathStyle = backupS3ForcePathStyle ?: defaults.backupS3ForcePathStyle,
            backupUseVersionedKeys = backupUseVersionedKeys ?: defaults.backupUseVersionedKeys,
            backupRetentionDays = backupRetentionDays ?: defaults.backupRetentionDays,
            backupMasterKeySet = backupMasterKeySet ?: defaults.backupMasterKeySet,
            backupS3CredentialsSet = backupS3CredentialsSet ?: defaults.backupS3CredentialsSet,
            backupLastSyncedAt = backupLastSyncedAt ?: defaults.backupLastSyncedAt,
            backupLastRestoredAt = backupLastRestoredAt ?: defaults.backupLastRestoredAt,
            backupLastAutoSyncDate = backupLastAutoSyncDate ?: defaults.backupLastAutoSyncDate,
            backupLastError = backupLastError ?: defaults.backupLastError,
            themePreference = parseThemePreference(themePreference) ?: defaults.themePreference,
            regionalPreferencesMode = parseRegionalMode(regionalPreferencesMode)
                ?: defaults.regionalPreferencesMode,
            defaultLocale = defaultLocale ?: defaults.defaultLocale,
            defaultTimezone = defaultTimezone ?: defaults.defaultTimezone,
            defaultCountryCode = defaultCountryCode ?: defaults.defaultCountryCode,
            defaultCountryName = defaultCountryName ?: defaults.defaultCountryName,
            defaultCurrency = defaultCurrency ?: defaults.defaultCurrency
        )
    }

    companion object {
        fun fromDomain(domain: AppSettings): AppSettingsDto = AppSettingsDto(
            openRouterApiKey = domain.openRouterApiKey,
            aiModel = domain.aiModel,
            assistantMemoryModel = domain.assistantMemoryModel,
            notificationsEnabled = domain.notificationsEnabled,
            reminderHour = domain.reminderHours.firstOrNull(),
            reminderHours = domain.reminderHours,
            assistantMemoryEnabled = domain.assistantMemoryEnabled,
            backupSyncEnabled = domain.backupSyncEnabled,
            backupSyncHour = domain.backupSyncHour,
            backupS3Endpoint = domain.backupS3Endpoint,
            backupS3Region = domain.backupS3Region,
            backupS3Bucket = domain.backupS3Bucket,
            backupS3ObjectKey = domain.backupS3ObjectKey,
            backupS3ForcePathStyle = domain.backupS3ForcePathStyle,
            backupUseVersionedKeys = domain.backupUseVersionedKeys,
            backupRetentionDays = domain.backupRetentionDays,
            backupMasterKeySet = domain.backupMasterKeySet,
            backupS3CredentialsSet = domain.backupS3CredentialsSet,
            backupLastSyncedAt = domain.backupLastSyncedAt,
            backupLastRestoredAt = domain.backupLastRestoredAt,
            backupLastAutoSyncDate = domain.backupLastAutoSyncDate,
            backupLastError = domain.backupLastError,
            themePreference = domain.themePreference.toCompatString(),
            regionalPreferencesMode = domain.regionalPreferencesMode.toCompatString(),
            defaultLocale = domain.defaultLocale,
            defaultTimezone = domain.defaultTimezone,
            defaultCountryCode = domain.defaultCountryCode,
            defaultCountryName = domain.defaultCountryName,
            defaultCurrency = domain.defaultCurrency
        )
    }
}

private fun parseWaterType(value: String?): WaterType = when (value?.lowercase()) {
    "marine" -> WaterType.MARINE
    "brackish" -> WaterType.BRACKISH
    else -> WaterType.FRESHWATER
}

private fun parseTaskCategory(value: String?): TaskCategory? = when (value?.lowercase()) {
    "maintenance" -> TaskCategory.MAINTENANCE
    "feeding" -> TaskCategory.FEEDING
    else -> null
}

private fun parseLivestockKind(value: String?): LivestockKind = when (value?.lowercase()) {
    "shrimp" -> LivestockKind.SHRIMP
    "snail" -> LivestockKind.SNAIL
    "coral" -> LivestockKind.CORAL
    "plant" -> LivestockKind.PLANT
    "other" -> LivestockKind.OTHER
    else -> LivestockKind.FISH
}

private fun parseLivestockStatus(value: String?): LivestockStatus = when (value?.lowercase()) {
    "ill" -> LivestockStatus.ILL
    "deceased" -> LivestockStatus.DECEASED
    else -> LivestockStatus.ACTIVE
}

private fun parseAssetCategory(value: String?): AssetCategory = when (value?.lowercase()) {
    "filter" -> AssetCategory.FILTER
    "heater" -> AssetCategory.HEATER
    "light" -> AssetCategory.LIGHT
    "co2" -> AssetCategory.CO2
    else -> AssetCategory.OTHER
}

private fun parseConsumableUnit(value: String?): ConsumableUnit = when (value?.lowercase()) {
    "g" -> ConsumableUnit.G
    "pcs" -> ConsumableUnit.PCS
    else -> ConsumableUnit.ML
}

private fun parseIssueStatus(value: String?): IssueStatus = when (value?.lowercase()) {
    "monitoring" -> IssueStatus.MONITORING
    "resolved" -> IssueStatus.RESOLVED
    else -> IssueStatus.OPEN
}

private fun parseTimelineType(value: String?): TimelineEventType = when (value?.lowercase()) {
    "parameter" -> TimelineEventType.PARAMETER
    "issue" -> TimelineEventType.ISSUE
    "livestock" -> TimelineEventType.LIVESTOCK
    "memo" -> TimelineEventType.MEMO
    "dosing" -> TimelineEventType.DOSING
    "asset" -> TimelineEventType.ASSET
    "consumable" -> TimelineEventType.CONSUMABLE
    else -> TimelineEventType.TASK
}

private fun parseEntityKind(value: String?): EntityKind = when (value?.lowercase()) {
    "aquarium" -> EntityKind.AQUARIUM
    "task" -> EntityKind.TASK
    "livestock" -> EntityKind.LIVESTOCK
    "asset" -> EntityKind.ASSET
    "consumable" -> EntityKind.CONSUMABLE
    "issue" -> EntityKind.ISSUE
    "memo" -> EntityKind.MEMO
    "dosing" -> EntityKind.DOSING
    "parameter-log" -> EntityKind.PARAMETER_LOG
    else -> EntityKind.AQUARIUM
}

private fun parseThemePreference(value: String?): AppThemePreference? = when (value?.lowercase()) {
    "system" -> AppThemePreference.SYSTEM
    "light" -> AppThemePreference.LIGHT
    "dark" -> AppThemePreference.DARK
    else -> null
}

private fun parseRegionalMode(value: String?): RegionalPreferencesMode? = when (value?.lowercase()) {
    "auto" -> RegionalPreferencesMode.AUTO
    "manual" -> RegionalPreferencesMode.MANUAL
    else -> null
}

private fun WaterType.toCompatString(): String = when (this) {
    WaterType.FRESHWATER -> "freshwater"
    WaterType.MARINE -> "marine"
    WaterType.BRACKISH -> "brackish"
}

private fun TaskCategory.toCompatString(): String = when (this) {
    TaskCategory.MAINTENANCE -> "maintenance"
    TaskCategory.FEEDING -> "feeding"
}

private fun LivestockKind.toCompatString(): String = when (this) {
    LivestockKind.FISH -> "fish"
    LivestockKind.SHRIMP -> "shrimp"
    LivestockKind.SNAIL -> "snail"
    LivestockKind.CORAL -> "coral"
    LivestockKind.PLANT -> "plant"
    LivestockKind.OTHER -> "other"
}

private fun LivestockStatus?.toCompatString(): String? = when (this) {
    LivestockStatus.ACTIVE -> "active"
    LivestockStatus.ILL -> "ill"
    LivestockStatus.DECEASED -> "deceased"
    null -> null
}

private fun AssetCategory.toCompatString(): String = when (this) {
    AssetCategory.FILTER -> "filter"
    AssetCategory.HEATER -> "heater"
    AssetCategory.LIGHT -> "light"
    AssetCategory.CO2 -> "co2"
    AssetCategory.OTHER -> "other"
}

private fun ConsumableUnit.toCompatString(): String = when (this) {
    ConsumableUnit.G -> "g"
    ConsumableUnit.ML -> "ml"
    ConsumableUnit.PCS -> "pcs"
}

private fun IssueStatus.toCompatString(): String = when (this) {
    IssueStatus.OPEN -> "open"
    IssueStatus.MONITORING -> "monitoring"
    IssueStatus.RESOLVED -> "resolved"
}

private fun TimelineEventType.toCompatString(): String = when (this) {
    TimelineEventType.TASK -> "task"
    TimelineEventType.PARAMETER -> "parameter"
    TimelineEventType.ISSUE -> "issue"
    TimelineEventType.LIVESTOCK -> "livestock"
    TimelineEventType.MEMO -> "memo"
    TimelineEventType.DOSING -> "dosing"
    TimelineEventType.ASSET -> "asset"
    TimelineEventType.CONSUMABLE -> "consumable"
}

private fun EntityKind.toCompatString(): String = when (this) {
    EntityKind.AQUARIUM -> "aquarium"
    EntityKind.TASK -> "task"
    EntityKind.LIVESTOCK -> "livestock"
    EntityKind.ASSET -> "asset"
    EntityKind.CONSUMABLE -> "consumable"
    EntityKind.ISSUE -> "issue"
    EntityKind.MEMO -> "memo"
    EntityKind.DOSING -> "dosing"
    EntityKind.PARAMETER_LOG -> "parameter-log"
}

private fun AppThemePreference.toCompatString(): String = when (this) {
    AppThemePreference.SYSTEM -> "system"
    AppThemePreference.LIGHT -> "light"
    AppThemePreference.DARK -> "dark"
}

private fun RegionalPreferencesMode.toCompatString(): String = when (this) {
    RegionalPreferencesMode.AUTO -> "auto"
    RegionalPreferencesMode.MANUAL -> "manual"
}
