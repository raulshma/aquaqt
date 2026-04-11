package com.keepaside.aquapt.feature.entity

import com.keepaside.aquapt.core.model.Aquarium
import com.keepaside.aquapt.core.model.Asset
import com.keepaside.aquapt.core.model.AssetCategory
import com.keepaside.aquapt.core.model.Consumable
import com.keepaside.aquapt.core.model.ConsumableUnit
import com.keepaside.aquapt.core.model.EntityKind
import com.keepaside.aquapt.core.model.EntityRef
import com.keepaside.aquapt.core.model.Issue
import com.keepaside.aquapt.core.model.IssueStatus
import com.keepaside.aquapt.core.model.Livestock
import com.keepaside.aquapt.core.model.LivestockStatus
import com.keepaside.aquapt.core.model.Memo
import com.keepaside.aquapt.core.model.TaskCategory
import com.keepaside.aquapt.core.model.TaskExecution
import com.keepaside.aquapt.core.model.TaskFrequency
import com.keepaside.aquapt.core.model.TaskTemplate
import com.keepaside.aquapt.core.model.TimelineEvent
import com.keepaside.aquapt.core.model.TimelineEventType
import com.keepaside.aquapt.core.model.WaterType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class EntityDetailViewModelTest {

    @Test
    fun `invalid deep link returns missing-state guidance`() {
        val state = assembleEntityDetailUiState(
            kind = null,
            entityId = "",
            routeAquariumId = null,
            aquariums = emptyList(),
            taskTemplates = emptyList(),
            taskExecutions = emptyList(),
            livestock = emptyList(),
            assets = emptyList(),
            consumables = emptyList(),
            issues = emptyList(),
            memos = emptyList(),
            dosingLogs = emptyList(),
            parameterLogs = emptyList(),
            timelineEvents = emptyList(),
            zoneId = ZoneOffset.UTC
        )

        assertTrue(state.isNotFound)
        assertEquals("This deep link is missing entity details.", state.headline)
        assertEquals("Entity", state.kindLabel)
    }

    @Test
    fun `task detail includes completion and linked-event summary`() {
        val aquarium = Aquarium(
            id = "a-display",
            name = "Display",
            volumeLiters = 180.0,
            waterType = WaterType.FRESHWATER
        )
        val task = TaskTemplate(
            id = "task-trim",
            title = "Trim stems",
            category = TaskCategory.MAINTENANCE,
            frequency = TaskFrequency.WEEKLY,
            aquariumIds = listOf(aquarium.id),
            startDate = "2026-04-01",
            timesPerDay = 1
        )
        val executions = listOf(
            TaskExecution(
                id = "exec-old",
                taskTemplateId = task.id,
                aquariumId = aquarium.id,
                completedAt = "2026-04-10T08:00:00Z"
            ),
            TaskExecution(
                id = "exec-new",
                taskTemplateId = task.id,
                aquariumId = aquarium.id,
                completedAt = "2026-04-11T09:30:00Z"
            )
        )
        val timelineEvents = listOf(
            TimelineEvent(
                id = "event-1",
                aquariumId = aquarium.id,
                type = TimelineEventType.TASK,
                createdAt = "2026-04-11T10:00:00Z",
                title = "Trim complete",
                source = EntityRef(EntityKind.TASK, task.id, aquarium.id)
            ),
            TimelineEvent(
                id = "event-2",
                aquariumId = aquarium.id,
                type = TimelineEventType.MEMO,
                createdAt = "2026-04-11T11:00:00Z",
                title = "Unrelated memo"
            )
        )

        val state = assembleEntityDetailUiState(
            kind = EntityKind.TASK,
            entityId = task.id,
            routeAquariumId = aquarium.id,
            aquariums = listOf(aquarium),
            taskTemplates = listOf(task),
            taskExecutions = executions,
            livestock = emptyList(),
            assets = emptyList(),
            consumables = emptyList(),
            issues = emptyList(),
            memos = emptyList(),
            dosingLogs = emptyList(),
            parameterLogs = emptyList(),
            timelineEvents = timelineEvents,
            zoneId = ZoneOffset.UTC
        )

        val metricByLabel = state.metrics.associate { it.label to it.value }
        val fieldByLabel = state.fields.associate { it.label to it.value }

        assertEquals("Task details", state.headline)
        assertEquals("Trim stems", state.title)
        assertEquals("Display", state.aquariumName)
        assertEquals("2", metricByLabel["Completions"])
        assertEquals("1", metricByLabel["Assigned tanks"])
        assertEquals("1", metricByLabel["Linked events"])
        assertEquals("Maintenance", fieldByLabel["Category"])
        assertEquals("Weekly", fieldByLabel["Frequency"])
        assertEquals("2026-04-11 09:30", fieldByLabel["Latest completion"])
        assertEquals(1, state.relatedEvents.size)
        assertEquals(2, state.taskExecutionHistory.size)
        assertEquals("exec-new", state.taskExecutionHistory.first().id)
        assertEquals("Display", state.taskExecutionHistory.first().aquariumName)
    }

    @Test
    fun `task detail surfaces linked entity navigation targets`() {
        val aquarium = Aquarium(
            id = "a-task-links",
            name = "Task Links",
            volumeLiters = 90.0,
            waterType = WaterType.FRESHWATER
        )
        val resident = Livestock(
            id = "l-task-target",
            aquariumId = aquarium.id,
            name = "Neon group"
        )
        val task = TaskTemplate(
            id = "task-feed-neons",
            title = "Feed neons",
            frequency = TaskFrequency.DAILY,
            aquariumIds = listOf(aquarium.id),
            livestockId = resident.id
        )
        val memo = Memo(
            id = "memo-linked",
            aquariumId = aquarium.id,
            content = "Feeding response looked strong.",
            createdAt = "2026-04-11T09:15:00Z"
        )
        val events = listOf(
            TimelineEvent(
                id = "event-task-link",
                aquariumId = aquarium.id,
                type = TimelineEventType.TASK,
                createdAt = "2026-04-11T10:00:00Z",
                title = "Feed task completed",
                source = EntityRef(EntityKind.TASK, task.id, aquarium.id),
                related = listOf(EntityRef(EntityKind.MEMO, memo.id, aquarium.id))
            )
        )

        val state = assembleEntityDetailUiState(
            kind = EntityKind.TASK,
            entityId = task.id,
            routeAquariumId = aquarium.id,
            aquariums = listOf(aquarium),
            taskTemplates = listOf(task),
            taskExecutions = emptyList(),
            livestock = listOf(resident),
            assets = emptyList(),
            consumables = emptyList(),
            issues = emptyList(),
            memos = listOf(memo),
            dosingLogs = emptyList(),
            parameterLogs = emptyList(),
            timelineEvents = events,
            zoneId = ZoneOffset.UTC
        )

        assertTrue(
            state.linkedEntities.any {
                it.kind == EntityKind.AQUARIUM && it.entityId == aquarium.id
            }
        )
        assertTrue(
            state.linkedEntities.any {
                it.kind == EntityKind.LIVESTOCK && it.entityId == resident.id
            }
        )
        assertTrue(
            state.linkedEntities.any {
                it.kind == EntityKind.MEMO && it.entityId == memo.id
            }
        )
        assertTrue(
            state.linkedEntities.none {
                it.kind == EntityKind.TASK && it.entityId == task.id
            }
        )
    }

    @Test
    fun `aquarium detail surfaces aggregate metrics`() {
        val aquarium = Aquarium(
            id = "a-community",
            name = "Community",
            volumeLiters = 240.0,
            waterType = WaterType.FRESHWATER,
            setupDate = "2026-01-10"
        )
        val residents = listOf(
            Livestock(id = "l-1", aquariumId = aquarium.id, name = "Ember"),
            Livestock(id = "l-2", aquariumId = aquarium.id, name = "Scout")
        )
        val tasks = listOf(
            TaskTemplate(
                id = "task-feed",
                title = "Feed fish",
                frequency = TaskFrequency.DAILY,
                aquariumIds = listOf(aquarium.id)
            )
        )
        val issues = listOf(
            Issue(
                id = "issue-open",
                aquariumId = aquarium.id,
                title = "Cloudy water",
                status = IssueStatus.OPEN,
                createdAt = "2026-04-11T08:00:00Z"
            ),
            Issue(
                id = "issue-closed",
                aquariumId = aquarium.id,
                title = "Old algae bloom",
                status = IssueStatus.RESOLVED,
                createdAt = "2026-03-01T08:00:00Z"
            )
        )
        val events = listOf(
            TimelineEvent(
                id = "event-a",
                aquariumId = aquarium.id,
                type = TimelineEventType.MEMO,
                createdAt = "2026-04-11T12:00:00Z",
                title = "Observation"
            )
        )

        val state = assembleEntityDetailUiState(
            kind = EntityKind.AQUARIUM,
            entityId = aquarium.id,
            routeAquariumId = null,
            aquariums = listOf(aquarium),
            taskTemplates = tasks,
            taskExecutions = emptyList(),
            livestock = residents,
            assets = emptyList(),
            consumables = emptyList(),
            issues = issues,
            memos = emptyList(),
            dosingLogs = emptyList(),
            parameterLogs = emptyList(),
            timelineEvents = events,
            zoneId = ZoneOffset.UTC
        )

        val metricByLabel = state.metrics.associate { it.label to it.value }
        val fieldByLabel = state.fields.associate { it.label to it.value }

        assertEquals("Community", state.title)
        assertEquals("Freshwater", state.subtitle)
        assertEquals("2", metricByLabel["Residents"])
        assertEquals("1", metricByLabel["Tasks"])
        assertEquals("1", metricByLabel["Open issues"])
        assertEquals("1", metricByLabel["Linked events"])
        assertEquals("240 L", fieldByLabel["Volume"])
        assertEquals("2026-01-10", fieldByLabel["Setup date"])
    }

    @Test
    fun `aquarium detail includes linked collection shortcuts`() {
        val aquarium = Aquarium(
            id = "a-collections",
            name = "Collection Tank",
            volumeLiters = 150.0,
            waterType = WaterType.FRESHWATER
        )
        val task = TaskTemplate(
            id = "task-maintenance",
            title = "Weekly maintenance",
            frequency = TaskFrequency.WEEKLY,
            aquariumIds = listOf(aquarium.id)
        )
        val resident = Livestock(
            id = "l-collection",
            aquariumId = aquarium.id,
            name = "Otocinclus"
        )
        val asset = Asset(
            id = "asset-collection",
            aquariumId = aquarium.id,
            category = AssetCategory.FILTER,
            brandModel = "Flow Pro"
        )
        val consumable = Consumable(
            id = "consumable-collection",
            aquariumId = aquarium.id,
            name = "Fertilizer",
            unit = ConsumableUnit.ML,
            remaining = 120.0,
            updatedAt = "2026-04-11T09:00:00Z"
        )
        val issue = Issue(
            id = "issue-collection",
            aquariumId = aquarium.id,
            title = "Minor algae",
            status = IssueStatus.OPEN,
            createdAt = "2026-04-11T08:30:00Z"
        )
        val memo = Memo(
            id = "memo-collection",
            aquariumId = aquarium.id,
            content = "Observed improved flow after filter clean.",
            createdAt = "2026-04-11T10:30:00Z"
        )

        val state = assembleEntityDetailUiState(
            kind = EntityKind.AQUARIUM,
            entityId = aquarium.id,
            routeAquariumId = null,
            aquariums = listOf(aquarium),
            taskTemplates = listOf(task),
            taskExecutions = emptyList(),
            livestock = listOf(resident),
            assets = listOf(asset),
            consumables = listOf(consumable),
            issues = listOf(issue),
            memos = listOf(memo),
            dosingLogs = emptyList(),
            parameterLogs = emptyList(),
            timelineEvents = emptyList(),
            zoneId = ZoneOffset.UTC
        )

        val linkedKinds = state.linkedEntities.map { it.kind }.toSet()
        assertTrue(linkedKinds.contains(EntityKind.TASK))
        assertTrue(linkedKinds.contains(EntityKind.LIVESTOCK))
        assertTrue(linkedKinds.contains(EntityKind.ASSET))
        assertTrue(linkedKinds.contains(EntityKind.CONSUMABLE))
        assertTrue(linkedKinds.contains(EntityKind.ISSUE))
        assertTrue(linkedKinds.contains(EntityKind.MEMO))
    }

    @Test
    fun `missing entity kind returns not found state with tank context`() {
        val aquarium = Aquarium(
            id = "a-quarantine",
            name = "Quarantine",
            volumeLiters = 30.0,
            waterType = WaterType.FRESHWATER
        )

        val state = assembleEntityDetailUiState(
            kind = EntityKind.ISSUE,
            entityId = "issue-missing",
            routeAquariumId = aquarium.id,
            aquariums = listOf(aquarium),
            taskTemplates = emptyList(),
            taskExecutions = emptyList(),
            livestock = emptyList(),
            assets = emptyList(),
            consumables = emptyList(),
            issues = emptyList(),
            memos = emptyList(),
            dosingLogs = emptyList(),
            parameterLogs = emptyList(),
            timelineEvents = emptyList(),
            zoneId = ZoneOffset.UTC
        )

        assertTrue(state.isNotFound)
        assertEquals("Issue not found.", state.headline)
        assertEquals("Quarantine", state.aquariumName)
        assertNull(state.subtitle)
    }

    @Test
    fun `issue detail exposes issue editor state`() {
        val aquarium = Aquarium(
            id = "a-hospital",
            name = "Hospital",
            volumeLiters = 60.0,
            waterType = WaterType.FRESHWATER
        )
        val issue = Issue(
            id = "issue-fin-rot",
            aquariumId = aquarium.id,
            title = "Fin rot signs",
            status = IssueStatus.MONITORING,
            createdAt = "2026-04-11T08:00:00Z",
            resolutionNote = "Salt + observation"
        )

        val state = assembleEntityDetailUiState(
            kind = EntityKind.ISSUE,
            entityId = issue.id,
            routeAquariumId = aquarium.id,
            aquariums = listOf(aquarium),
            taskTemplates = emptyList(),
            taskExecutions = emptyList(),
            livestock = emptyList(),
            assets = emptyList(),
            consumables = emptyList(),
            issues = listOf(issue),
            memos = emptyList(),
            dosingLogs = emptyList(),
            parameterLogs = emptyList(),
            timelineEvents = emptyList(),
            zoneId = ZoneOffset.UTC
        )

        val editor = state.issueEditor
        assertNotNull(editor)
        assertEquals(issue.id, editor?.id)
        assertEquals(issue.title, editor?.title)
        assertEquals(IssueStatus.MONITORING, editor?.status)
        assertEquals("Salt + observation", editor?.resolutionNote)
    }

    @Test
    fun `memo detail exposes memo editor state`() {
        val aquarium = Aquarium(
            id = "a-betta",
            name = "Betta",
            volumeLiters = 25.0,
            waterType = WaterType.FRESHWATER
        )
        val memo = Memo(
            id = "memo-1",
            aquariumId = aquarium.id,
            content = "Fish is eating better after lights dimmed.",
            createdAt = "2026-04-11T07:30:00Z",
            photoUri = "content://memo-photo"
        )

        val state = assembleEntityDetailUiState(
            kind = EntityKind.MEMO,
            entityId = memo.id,
            routeAquariumId = aquarium.id,
            aquariums = listOf(aquarium),
            taskTemplates = emptyList(),
            taskExecutions = emptyList(),
            livestock = emptyList(),
            assets = emptyList(),
            consumables = emptyList(),
            issues = emptyList(),
            memos = listOf(memo),
            dosingLogs = emptyList(),
            parameterLogs = emptyList(),
            timelineEvents = emptyList(),
            zoneId = ZoneOffset.UTC
        )

        val editor = state.memoEditor
        assertNotNull(editor)
        assertEquals(memo.id, editor?.id)
        assertEquals(memo.content, editor?.content)
        assertEquals(memo.photoUri, editor?.photoUri)
    }

    @Test
    fun `livestock detail exposes livestock editor state`() {
        val aquarium = Aquarium(
            id = "a-residents",
            name = "Residents Tank",
            volumeLiters = 180.0,
            waterType = WaterType.FRESHWATER
        )
        val resident = Livestock(
            id = "l-ember",
            aquariumId = aquarium.id,
            name = "Ember",
            species = "Betta",
            quantity = 2,
            status = LivestockStatus.ILL,
            dietaryNotes = "Soft pellets and frozen food",
            acquiredAt = "2026-04-11T08:45:00Z",
            photoUri = "content://resident-photo"
        )

        val state = assembleEntityDetailUiState(
            kind = EntityKind.LIVESTOCK,
            entityId = resident.id,
            routeAquariumId = aquarium.id,
            aquariums = listOf(aquarium),
            taskTemplates = emptyList(),
            taskExecutions = emptyList(),
            livestock = listOf(resident),
            assets = emptyList(),
            consumables = emptyList(),
            issues = emptyList(),
            memos = emptyList(),
            dosingLogs = emptyList(),
            parameterLogs = emptyList(),
            timelineEvents = emptyList(),
            zoneId = ZoneOffset.UTC
        )

        val editor = state.livestockEditor
        val fieldByLabel = state.fields.associate { it.label to it.value }

        assertNotNull(editor)
        assertEquals(resident.id, editor?.id)
        assertEquals("Ember", editor?.name)
        assertEquals("Betta", editor?.species)
        assertEquals("2", editor?.quantityInput)
        assertEquals(LivestockStatus.ILL, editor?.status)
        assertEquals("Soft pellets and frozen food", editor?.dietaryNotes)
        assertEquals("2026-04-11 08:45", fieldByLabel["Acquired"])
    }

    @Test
    fun `asset detail exposes asset editor state`() {
        val aquarium = Aquarium(
            id = "a-tech",
            name = "Tech Tank",
            volumeLiters = 120.0,
            waterType = WaterType.FRESHWATER
        )
        val asset = Asset(
            id = "asset-filter",
            aquariumId = aquarium.id,
            category = AssetCategory.FILTER,
            brandModel = "Canister X2",
            purchasedAt = "2026-04-11T09:15:00Z",
            price = 189.5,
            photoUri = "content://asset-photo"
        )

        val state = assembleEntityDetailUiState(
            kind = EntityKind.ASSET,
            entityId = asset.id,
            routeAquariumId = aquarium.id,
            aquariums = listOf(aquarium),
            taskTemplates = emptyList(),
            taskExecutions = emptyList(),
            livestock = emptyList(),
            assets = listOf(asset),
            consumables = emptyList(),
            issues = emptyList(),
            memos = emptyList(),
            dosingLogs = emptyList(),
            parameterLogs = emptyList(),
            timelineEvents = emptyList(),
            zoneId = ZoneOffset.UTC
        )

        val editor = state.assetEditor
        val fieldByLabel = state.fields.associate { it.label to it.value }

        assertNotNull(editor)
        assertEquals(asset.id, editor?.id)
        assertEquals(AssetCategory.FILTER, editor?.category)
        assertEquals("Canister X2", editor?.brandModel)
        assertEquals("2026-04-11 09:15", editor?.purchasedAtInput)
        assertEquals("189.5", editor?.priceInput)
        assertEquals("2026-04-11 09:15", fieldByLabel["Purchased"])
    }

    @Test
    fun `consumable detail exposes consumable editor state`() {
        val aquarium = Aquarium(
            id = "a-dosing",
            name = "Dosing Tank",
            volumeLiters = 180.0,
            waterType = WaterType.FRESHWATER
        )
        val consumable = Consumable(
            id = "consumable-ferts",
            aquariumId = aquarium.id,
            name = "All-in-one Fert",
            unit = ConsumableUnit.ML,
            remaining = 350.0,
            reorderAt = 100.0,
            updatedAt = "2026-04-11T10:00:00Z",
            photoUri = "content://consumable-photo"
        )

        val state = assembleEntityDetailUiState(
            kind = EntityKind.CONSUMABLE,
            entityId = consumable.id,
            routeAquariumId = aquarium.id,
            aquariums = listOf(aquarium),
            taskTemplates = emptyList(),
            taskExecutions = emptyList(),
            livestock = emptyList(),
            assets = emptyList(),
            consumables = listOf(consumable),
            issues = emptyList(),
            memos = emptyList(),
            dosingLogs = emptyList(),
            parameterLogs = emptyList(),
            timelineEvents = emptyList(),
            zoneId = ZoneOffset.UTC
        )

        val editor = state.consumableEditor

        assertNotNull(editor)
        assertEquals(consumable.id, editor?.id)
        assertEquals("All-in-one Fert", editor?.name)
        assertEquals(ConsumableUnit.ML, editor?.unit)
        assertEquals("350", editor?.remainingInput)
        assertEquals("100", editor?.reorderAtInput)
    }

    @Test
    fun `issue detail gallery includes linked event and entity photos without duplicates`() {
        val aquarium = Aquarium(
            id = "a-reef",
            name = "Reef",
            volumeLiters = 320.0,
            waterType = WaterType.MARINE,
            photoUri = "content://tank-photo"
        )
        val issue = Issue(
            id = "issue-ph",
            aquariumId = aquarium.id,
            title = "pH swing",
            status = IssueStatus.OPEN,
            createdAt = "2026-04-11T08:00:00Z"
        )
        val resident = Livestock(
            id = "l-clown",
            aquariumId = aquarium.id,
            name = "Clown pair",
            photoUri = "content://resident-photo"
        )
        val memo = Memo(
            id = "memo-coral",
            aquariumId = aquarium.id,
            content = "Coral extension looked strong after dosing.",
            createdAt = "2026-04-11T09:00:00Z",
            photoUri = "content://memo-photo"
        )

        val timelineEvents = listOf(
            TimelineEvent(
                id = "event-new",
                aquariumId = aquarium.id,
                type = TimelineEventType.ISSUE,
                createdAt = "2026-04-11T11:00:00Z",
                title = "Issue noted",
                photoUri = "content://event-photo",
                source = EntityRef(EntityKind.ISSUE, issue.id, aquarium.id),
                related = listOf(
                    EntityRef(EntityKind.MEMO, memo.id, aquarium.id),
                    EntityRef(EntityKind.LIVESTOCK, resident.id, aquarium.id),
                    EntityRef(EntityKind.AQUARIUM, aquarium.id, aquarium.id)
                )
            ),
            TimelineEvent(
                id = "event-old",
                aquariumId = aquarium.id,
                type = TimelineEventType.ISSUE,
                createdAt = "2026-04-11T10:00:00Z",
                title = "Duplicate image link",
                photoUri = "content://event-photo",
                source = EntityRef(EntityKind.ISSUE, issue.id, aquarium.id),
                related = listOf(
                    EntityRef(EntityKind.MEMO, memo.id, aquarium.id)
                )
            )
        )

        val state = assembleEntityDetailUiState(
            kind = EntityKind.ISSUE,
            entityId = issue.id,
            routeAquariumId = aquarium.id,
            aquariums = listOf(aquarium),
            taskTemplates = emptyList(),
            taskExecutions = emptyList(),
            livestock = listOf(resident),
            assets = emptyList(),
            consumables = emptyList(),
            issues = listOf(issue),
            memos = listOf(memo),
            dosingLogs = emptyList(),
            parameterLogs = emptyList(),
            timelineEvents = timelineEvents,
            zoneId = ZoneOffset.UTC
        )

        val uris = state.relatedPhotos.map { it.uri }
        assertEquals(4, uris.size)
        assertEquals("content://event-photo", uris[0])
        assertTrue(uris.contains("content://memo-photo"))
        assertTrue(uris.contains("content://resident-photo"))
        assertTrue(uris.contains("content://tank-photo"))
    }

    @Test
    fun `issue update description summarizes status and note changes`() {
        val previous = Issue(
            id = "issue-1",
            aquariumId = "a-1",
            title = "Cloudy water",
            status = IssueStatus.OPEN,
            createdAt = "2026-04-11T08:00:00Z",
            resolutionNote = null
        )
        val updated = previous.copy(
            status = IssueStatus.RESOLVED,
            resolutionNote = "Resolved after large water change"
        )

        val description = buildIssueUpdateDescription(previous, updated)
        assertEquals("Status Open → Resolved • Resolution note updated", description)
    }

    @Test
    fun `asset update description summarizes changed fields`() {
        val previous = Asset(
            id = "asset-1",
            aquariumId = "a-1",
            category = AssetCategory.FILTER,
            brandModel = "Filter A",
            purchasedAt = "2026-04-01T09:00:00Z",
            price = 120.0
        )
        val updated = previous.copy(
            category = AssetCategory.HEATER,
            brandModel = "Heater Z",
            purchasedAt = null,
            price = 89.0
        )

        val description = buildAssetUpdateDescription(previous, updated)
        assertEquals(
            "Category Filter → Heater • Brand/model updated • Purchase date cleared • Price updated",
            description
        )
    }

    @Test
    fun `consumable update description summarizes inventory changes`() {
        val previous = Consumable(
            id = "consumable-1",
            aquariumId = "a-1",
            name = "Fertilizer",
            unit = ConsumableUnit.ML,
            remaining = 300.0,
            reorderAt = 80.0,
            updatedAt = "2026-04-01T09:00:00Z"
        )
        val updated = previous.copy(
            name = "Macro Fert",
            unit = ConsumableUnit.G,
            remaining = 250.0,
            reorderAt = null
        )

        val description = buildConsumableUpdateDescription(previous, updated)
        assertEquals(
            "Name updated • Unit ml → g • Remaining 300 → 250 • Reorder threshold cleared",
            description
        )
    }

    @Test
    fun `livestock update description summarizes profile changes`() {
        val previous = Livestock(
            id = "l-1",
            aquariumId = "a-1",
            name = "Ember",
            species = "Betta",
            quantity = 1,
            status = LivestockStatus.ACTIVE,
            dietaryNotes = null
        )
        val updated = previous.copy(
            name = "Ember Prime",
            species = "",
            quantity = 3,
            status = LivestockStatus.DECEASED,
            dietaryNotes = "Archived after lifecycle completion"
        )

        val description = buildLivestockUpdateDescription(previous, updated)
        assertEquals(
            "Name updated • Species cleared • Quantity 1 → 3 • Status Active → Deceased • Dietary notes updated",
            description
        )
    }
}