package com.keepaside.aquapt.feature.entity

import com.keepaside.aquapt.core.model.Aquarium
import com.keepaside.aquapt.core.model.EntityKind
import com.keepaside.aquapt.core.model.WaterType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class EntityFormViewModelTest {

    @Test
    fun `issue form state auto-selects first aquarium and is savable`() {
        val draft = EntityFormDraft(
            aquariumId = null,
            createdAtInput = "2026-04-11 18:30",
            issueTitle = "Cloudy water"
        )

        val state = assembleEntityFormUiState(
            kind = EntityKind.ISSUE,
            draft = draft,
            aquariums = listOf(
                Aquarium(
                    id = "a-2",
                    name = "Zulu",
                    volumeLiters = 120.0,
                    waterType = WaterType.FRESHWATER
                ),
                Aquarium(
                    id = "a-1",
                    name = "Alpha",
                    volumeLiters = 80.0,
                    waterType = WaterType.FRESHWATER
                )
            ),
            isSaving = false,
            statusMessage = null,
            zoneId = ZoneOffset.UTC
        )

        assertEquals("a-1", state.aquariumId)
        assertEquals("Alpha", state.aquariumName)
        assertEquals("Save issue", state.saveButtonLabel)
        assertTrue(state.canSave)
    }

    @Test
    fun `unsupported kind is flagged and save is disabled`() {
        val state = assembleEntityFormUiState(
            kind = EntityKind.AQUARIUM,
            draft = EntityFormDraft(
                aquariumId = "a-1",
                createdAtInput = "2026-04-11 18:30"
            ),
            aquariums = listOf(
                Aquarium(
                    id = "a-1",
                    name = "Display",
                    volumeLiters = 180.0,
                    waterType = WaterType.FRESHWATER
                )
            ),
            isSaving = false,
            statusMessage = null,
            zoneId = ZoneOffset.UTC
        )

        assertTrue(state.isUnsupportedKind)
        assertFalse(state.canSave)
        assertEquals("This form is not available for this entity type yet.", state.statusMessage)
    }

    @Test
    fun `issue validation requires title`() {
        val error = validateEntityFormDraft(
            kind = EntityKind.ISSUE,
            draft = EntityFormDraft(
                aquariumId = "a-1",
                createdAtInput = "2026-04-11 18:30",
                issueTitle = "   "
            ),
            aquariumId = "a-1",
            zoneId = ZoneOffset.UTC
        )

        assertEquals("Name the issue before saving.", error)
    }

    @Test
    fun `memo validation enforces date and content`() {
        val invalidDateError = validateEntityFormDraft(
            kind = EntityKind.MEMO,
            draft = EntityFormDraft(
                aquariumId = "a-1",
                createdAtInput = "tomorrow",
                memoContent = "Observed new behavior"
            ),
            aquariumId = "a-1",
            zoneId = ZoneOffset.UTC
        )
        val invalidContentError = validateEntityFormDraft(
            kind = EntityKind.MEMO,
            draft = EntityFormDraft(
                aquariumId = "a-1",
                createdAtInput = "2026-04-11 18:30",
                memoContent = "  "
            ),
            aquariumId = "a-1",
            zoneId = ZoneOffset.UTC
        )

        assertEquals(entityFormDateTimeErrorMessage, invalidDateError)
        assertEquals("Write a memo before saving.", invalidContentError)
    }

    @Test
    fun `dosing validation enforces product and positive amount`() {
        val missingProductError = validateEntityFormDraft(
            kind = EntityKind.DOSING,
            draft = EntityFormDraft(
                aquariumId = "a-1",
                createdAtInput = "2026-04-11 18:30",
                dosingProduct = "  ",
                dosingAmountMl = "5"
            ),
            aquariumId = "a-1",
            zoneId = ZoneOffset.UTC
        )
        val invalidAmountError = validateEntityFormDraft(
            kind = EntityKind.DOSING,
            draft = EntityFormDraft(
                aquariumId = "a-1",
                createdAtInput = "2026-04-11 18:30",
                dosingProduct = "Microbe Lift",
                dosingAmountMl = "0"
            ),
            aquariumId = "a-1",
            zoneId = ZoneOffset.UTC
        )

        assertEquals("Name the dosing product before saving.", missingProductError)
        assertEquals(entityFormDosingAmountErrorMessage, invalidAmountError)
    }

    @Test
    fun `parameter validation requires at least one finite value`() {
        val missingValuesError = validateEntityFormDraft(
            kind = EntityKind.PARAMETER_LOG,
            draft = EntityFormDraft(
                aquariumId = "a-1",
                createdAtInput = "2026-04-11 18:30"
            ),
            aquariumId = "a-1",
            zoneId = ZoneOffset.UTC
        )
        val invalidValueError = validateEntityFormDraft(
            kind = EntityKind.PARAMETER_LOG,
            draft = EntityFormDraft(
                aquariumId = "a-1",
                createdAtInput = "2026-04-11 18:30",
                ammonia = "oops"
            ),
            aquariumId = "a-1",
            zoneId = ZoneOffset.UTC
        )
        val validStateError = validateEntityFormDraft(
            kind = EntityKind.PARAMETER_LOG,
            draft = EntityFormDraft(
                aquariumId = "a-1",
                createdAtInput = "2026-04-11 18:30",
                nitrate = "15.5",
                ph = "7.2"
            ),
            aquariumId = "a-1",
            zoneId = ZoneOffset.UTC
        )

        assertEquals(entityFormParameterErrorMessage, missingValuesError)
        assertEquals(entityFormParameterErrorMessage, invalidValueError)
        assertEquals(null, validStateError)
    }

    @Test
    fun `entity form supports dosing and parameter kinds`() {
        assertTrue(isEntityFormSupported(EntityKind.DOSING))
        assertTrue(isEntityFormSupported(EntityKind.PARAMETER_LOG))
    }

    @Test
    fun `entity form date parser accepts friendly local input`() {
        val parsed = parseEntityFormDateTimeInput("2026-04-11 18:30", ZoneOffset.UTC)

        assertEquals(Instant.parse("2026-04-11T18:30:00Z"), parsed)
    }

    @Test
    fun `photo uri normalization trims and drops blank values`() {
        assertEquals("content://memo-photo", normalizeEntityFormPhotoUri("  content://memo-photo  "))
        assertEquals(null, normalizeEntityFormPhotoUri("   "))
    }
}
