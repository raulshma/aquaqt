package com.keepaside.aquapt.feature.livestock

import com.keepaside.aquapt.core.model.Aquarium
import com.keepaside.aquapt.core.model.Livestock
import com.keepaside.aquapt.core.model.LivestockKind
import com.keepaside.aquapt.core.model.LivestockStatus
import com.keepaside.aquapt.core.model.TaskCategory
import com.keepaside.aquapt.core.model.TaskFrequency
import com.keepaside.aquapt.core.model.TaskTemplate
import com.keepaside.aquapt.core.model.WaterType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class LivestockViewModelTest {

    private val now: Instant = Instant.parse("2026-04-11T12:00:00Z")

    @Test
    fun `empty aquarium state is surfaced`() {
        val state = assembleLivestockUiState(
            aquariums = emptyList(),
            livestock = emptyList(),
            taskTemplates = emptyList(),
            interaction = LivestockInteractionState(),
            now = now,
            zoneId = ZoneOffset.UTC
        )

        assertTrue(state.isEmpty)
        assertEquals("Add your first tank before tracking residents.", state.headline)
        assertEquals(0, state.summary.aquariumCount)
    }

    @Test
    fun `summary counts resident statuses and feeding tasks`() {
        val aquarium = Aquarium(
            id = "a-1",
            name = "Display",
            volumeLiters = 120.0,
            waterType = WaterType.FRESHWATER
        )
        val active = Livestock(
            id = "l-1",
            aquariumId = aquarium.id,
            name = "Ember",
            species = "Betta",
            status = LivestockStatus.ACTIVE
        )
        val ill = Livestock(
            id = "l-2",
            aquariumId = aquarium.id,
            name = "Scout",
            species = "Corydoras",
            status = LivestockStatus.ILL
        )
        val task = TaskTemplate(
            id = "t-1",
            title = "Feed Ember",
            category = TaskCategory.FEEDING,
            livestockId = active.id,
            frequency = TaskFrequency.DAILY,
            aquariumIds = listOf(aquarium.id)
        )

        val state = assembleLivestockUiState(
            aquariums = listOf(aquarium),
            livestock = listOf(active, ill),
            taskTemplates = listOf(task),
            interaction = LivestockInteractionState(),
            now = now,
            zoneId = ZoneOffset.UTC
        )

        assertFalse(state.isEmpty)
        assertEquals(2, state.summary.residentCount)
        assertEquals(1, state.summary.activeCount)
        assertEquals(1, state.summary.illCount)
        assertEquals(1, state.summary.feedingTaskCount)
    }

    @Test
    fun `aquarium filter narrows visible residents`() {
        val display = Aquarium(
            id = "a-display",
            name = "Display",
            volumeLiters = 120.0,
            waterType = WaterType.FRESHWATER
        )
        val nano = Aquarium(
            id = "a-nano",
            name = "Nano",
            volumeLiters = 40.0,
            waterType = WaterType.BRACKISH
        )
        val residents = listOf(
            Livestock(id = "l-display", aquariumId = display.id, name = "Ember"),
            Livestock(id = "l-nano", aquariumId = nano.id, name = "Pin")
        )

        val state = assembleLivestockUiState(
            aquariums = listOf(display, nano),
            livestock = residents,
            taskTemplates = emptyList(),
            interaction = LivestockInteractionState(selectedAquariumId = nano.id),
            now = now,
            zoneId = ZoneOffset.UTC
        )

        assertEquals(1, state.residents.size)
        assertEquals("Pin", state.residents.single().name)
        assertEquals(nano.id, state.selectedAquariumId)
    }

    @Test
    fun `selected resident includes parent offspring feeding tasks and transfer targets`() {
        val display = Aquarium(
            id = "a-display",
            name = "Display",
            volumeLiters = 120.0,
            waterType = WaterType.FRESHWATER
        )
        val quarantine = Aquarium(
            id = "a-quarantine",
            name = "Quarantine",
            volumeLiters = 30.0,
            waterType = WaterType.FRESHWATER
        )
        val parent = Livestock(
            id = "l-parent",
            aquariumId = display.id,
            name = "Ember",
            species = "Betta",
            kind = LivestockKind.FISH,
            acquiredAt = "2026-03-28"
        )
        val child = Livestock(
            id = "l-child",
            aquariumId = display.id,
            name = "Spark",
            parentId = parent.id
        )
        val feedingTask = TaskTemplate(
            id = "t-feed",
            title = "Target feed Ember",
            category = TaskCategory.FEEDING,
            livestockId = parent.id,
            frequency = TaskFrequency.DAILY,
            aquariumIds = listOf(display.id),
            timesPerDay = 2
        )

        val state = assembleLivestockUiState(
            aquariums = listOf(display, quarantine),
            livestock = listOf(parent, child),
            taskTemplates = listOf(feedingTask),
            interaction = LivestockInteractionState(
                selectedLivestockId = parent.id,
                feedingNoteDraft = "Frozen food only"
            ),
            now = now,
            zoneId = ZoneOffset.UTC
        )

        val detail = state.selectedResident ?: error("Expected selected resident")

        assertEquals("Ember", detail.name)
        assertEquals("2 weeks", detail.ageLabel)
        assertEquals("Spark", detail.offspring.single().name)
        assertEquals("Target feed Ember", detail.feedingTasks.single().title)
        assertEquals(quarantine.id, detail.transferTargets.single().aquariumId)
        assertEquals("Frozen food only", state.feedingNoteDraft)
    }

    @Test
    fun `resident editor draft builds parent options and excludes self`() {
        val display = Aquarium(
            id = "a-display",
            name = "Display",
            volumeLiters = 120.0,
            waterType = WaterType.FRESHWATER
        )
        val breeder = Aquarium(
            id = "a-breeder",
            name = "Breeder",
            volumeLiters = 45.0,
            waterType = WaterType.FRESHWATER
        )
        val ember = Livestock(
            id = "l-ember",
            aquariumId = display.id,
            name = "Ember",
            species = "Betta"
        )
        val spark = Livestock(
            id = "l-spark",
            aquariumId = breeder.id,
            name = "Spark",
            species = "Betta"
        )

        val state = assembleLivestockUiState(
            aquariums = listOf(display, breeder),
            livestock = listOf(ember, spark),
            taskTemplates = emptyList(),
            interaction = LivestockInteractionState(
                residentDraft = LivestockResidentDraft(
                    id = ember.id,
                    aquariumId = display.id,
                    parentId = ember.id,
                    name = ember.name
                )
            ),
            now = now,
            zoneId = ZoneOffset.UTC
        )

        assertEquals(1, state.residentParentOptions.size)
        assertEquals("l-spark", state.residentParentOptions.single().id)
        assertNull(state.residentEditorDraft?.parentId)
    }

    @Test
    fun `resident draft validation catches missing and invalid values`() {
        val aquariumFilters = listOf(
            LivestockAquariumFilter(aquariumId = "a-1", aquariumName = "Display")
        )
        val parentOptions = listOf(
            LivestockParentOption(
                id = "l-parent",
                label = "Ember",
                aquariumId = "a-1",
                aquariumName = "Display"
            )
        )

        val missingName = LivestockResidentDraft(aquariumId = "a-1", name = "   ")
        val invalidQuantity = LivestockResidentDraft(aquariumId = "a-1", name = "Ember", quantity = "0")
        val invalidAcquiredAt = LivestockResidentDraft(
            aquariumId = "a-1",
            name = "Ember",
            acquiredAtInput = "tomorrow"
        )
        val invalidPrice = LivestockResidentDraft(
            aquariumId = "a-1",
            name = "Ember",
            purchasePriceInput = "-10"
        )

        assertEquals(
            "Name the resident before saving.",
            validateResidentDraft(missingName, aquariumFilters, parentOptions, ZoneOffset.UTC)
        )
        assertEquals(
            "Quantity must be at least 1.",
            validateResidentDraft(invalidQuantity, aquariumFilters, parentOptions, ZoneOffset.UTC)
        )
        assertEquals(
            "Use a valid acquired date/time like 2026-04-11 18:30.",
            validateResidentDraft(invalidAcquiredAt, aquariumFilters, parentOptions, ZoneOffset.UTC)
        )
        assertEquals(
            "Purchase price must be a valid non-negative number.",
            validateResidentDraft(invalidPrice, aquariumFilters, parentOptions, ZoneOffset.UTC)
        )
    }

    @Test
    fun `livestock date time input accepts friendly local value`() {
        val parsed = parseLivestockDateTimeInput("2026-04-11 18:30", ZoneOffset.UTC)

        assertEquals(Instant.parse("2026-04-11T18:30:00Z"), parsed)
    }
}
