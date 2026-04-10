package com.keepaside.aquapt.core.backup

import com.keepaside.aquapt.core.model.AppSettings
import com.keepaside.aquapt.core.repository.AquariumRepository
import com.keepaside.aquapt.core.repository.AssetRepository
import com.keepaside.aquapt.core.repository.ConsumableRepository
import com.keepaside.aquapt.core.repository.DosingLogRepository
import com.keepaside.aquapt.core.repository.IssueRepository
import com.keepaside.aquapt.core.repository.LivestockRepository
import com.keepaside.aquapt.core.repository.MemoRepository
import com.keepaside.aquapt.core.repository.ReminderGroupRepository
import com.keepaside.aquapt.core.repository.TaskExecutionRepository
import com.keepaside.aquapt.core.repository.TaskTemplateRepository
import com.keepaside.aquapt.core.repository.TimelineEventRepository
import com.keepaside.aquapt.core.repository.WaterParameterLogRepository
import kotlinx.coroutines.flow.first

data class AppStateImportResult(
    val snapshot: PersistedAppStateSnapshot,
    val skippedCounts: Map<String, Int> = emptyMap()
)

class AppStateBackupCompatibilityService(
    private val aquariumRepository: AquariumRepository,
    private val livestockRepository: LivestockRepository,
    private val taskTemplateRepository: TaskTemplateRepository,
    private val taskExecutionRepository: TaskExecutionRepository,
    private val reminderGroupRepository: ReminderGroupRepository,
    private val dosingLogRepository: DosingLogRepository,
    private val assetRepository: AssetRepository,
    private val consumableRepository: ConsumableRepository,
    private val waterParameterLogRepository: WaterParameterLogRepository,
    private val issueRepository: IssueRepository,
    private val memoRepository: MemoRepository,
    private val timelineEventRepository: TimelineEventRepository
) {
    suspend fun exportCurrentSnapshot(settings: AppSettings = AppSettings()): PersistedAppStateSnapshot =
        PersistedAppStateSnapshot(
            aquariums = aquariumRepository.getAll().first(),
            taskTemplates = taskTemplateRepository.getAll().first(),
            livestock = livestockRepository.getAll().first(),
            taskExecutions = taskExecutionRepository.getAll().first(),
            dosingLogs = dosingLogRepository.getAll().first(),
            assets = assetRepository.getAll().first(),
            consumables = consumableRepository.getAll().first(),
            parameterLogs = waterParameterLogRepository.getAll().first(),
            issues = issueRepository.getAll().first(),
            memos = memoRepository.getAll().first(),
            timeline = timelineEventRepository.getAll().first(),
            settings = settings,
            reminderGroups = reminderGroupRepository.getAll().first()
        )

    suspend fun exportCurrentStateJson(
        settings: AppSettings = AppSettings(),
        pretty: Boolean = true
    ): String = AppStateJsonCompatibility.encode(exportCurrentSnapshot(settings), pretty)

    suspend fun importFromJson(payload: String, replaceExisting: Boolean = true): AppStateImportResult {
        val snapshot = AppStateJsonCompatibility.decode(payload)
        return importSnapshot(snapshot, replaceExisting)
    }

    suspend fun importSnapshot(
        snapshot: PersistedAppStateSnapshot,
        replaceExisting: Boolean = true
    ): AppStateImportResult {
        if (replaceExisting) {
            clearExistingState()
        }

        snapshot.aquariums.forEach { aquariumRepository.upsert(it) }
        snapshot.reminderGroups.forEach { reminderGroupRepository.upsert(it) }

        val aquariumIds = snapshot.aquariums.map { it.id }.toSet()
        val skipped = mutableMapOf<String, Int>()

        val importedTemplates = mutableListOf<com.keepaside.aquapt.core.model.TaskTemplate>()
        var skippedTemplates = 0
        for (template in snapshot.taskTemplates) {
            val validAquariumIds = template.aquariumIds.distinct().filter { it in aquariumIds }
            val primaryAquariumId = validAquariumIds.firstOrNull()
            if (primaryAquariumId == null) {
                skippedTemplates += 1
                continue
            }

            val normalizedTemplate = template.copy(aquariumIds = validAquariumIds)
            taskTemplateRepository.upsert(normalizedTemplate, primaryAquariumId)
            importedTemplates += normalizedTemplate
        }
        if (skippedTemplates > 0) {
            skipped["taskTemplates"] = skippedTemplates
        }

        val templateIds = importedTemplates.map { it.id }.toSet()

        skipped["livestock"] = importByAquarium(snapshot.livestock, aquariumIds, { it.aquariumId }) {
            livestockRepository.upsert(it)
        }
        skipped["dosingLogs"] = importByAquarium(snapshot.dosingLogs, aquariumIds, { it.aquariumId }) {
            dosingLogRepository.upsert(it)
        }
        skipped["assets"] = importByAquarium(snapshot.assets, aquariumIds, { it.aquariumId }) {
            assetRepository.upsert(it)
        }
        skipped["consumables"] = importByAquarium(snapshot.consumables, aquariumIds, { it.aquariumId }) {
            consumableRepository.upsert(it)
        }
        skipped["parameterLogs"] = importByAquarium(snapshot.parameterLogs, aquariumIds, { it.aquariumId }) {
            waterParameterLogRepository.upsert(it)
        }
        skipped["issues"] = importByAquarium(snapshot.issues, aquariumIds, { it.aquariumId }) {
            issueRepository.upsert(it)
        }
        skipped["memos"] = importByAquarium(snapshot.memos, aquariumIds, { it.aquariumId }) {
            memoRepository.upsert(it)
        }
        skipped["timeline"] = importByAquarium(snapshot.timeline, aquariumIds, { it.aquariumId }) {
            timelineEventRepository.upsert(it)
        }

        var skippedExecutions = 0
        for (execution in snapshot.taskExecutions) {
            if (execution.aquariumId !in aquariumIds || execution.taskTemplateId !in templateIds) {
                skippedExecutions += 1
                continue
            }
            taskExecutionRepository.upsert(execution)
        }
        if (skippedExecutions > 0) {
            skipped["taskExecutions"] = skippedExecutions
        }

        return AppStateImportResult(
            snapshot = snapshot,
            skippedCounts = skipped.filterValues { it > 0 }
        )
    }

    private suspend fun clearExistingState() {
        val existingReminderGroups = reminderGroupRepository.getAll().first()
        existingReminderGroups.forEach { reminderGroupRepository.deleteById(it.id) }

        val existingAquariums = aquariumRepository.getAll().first()
        existingAquariums.forEach { aquariumRepository.deleteById(it.id) }
    }

    private suspend fun <T> importByAquarium(
        entries: List<T>,
        validAquariumIds: Set<String>,
        aquariumId: (T) -> String,
        import: suspend (T) -> Unit
    ): Int {
        var skipped = 0
        for (entry in entries) {
            if (aquariumId(entry) !in validAquariumIds) {
                skipped += 1
                continue
            }
            import(entry)
        }
        return skipped
    }

}
