package com.keepaside.aquapt.feature.timeline

import com.keepaside.aquapt.core.model.Aquarium
import com.keepaside.aquapt.core.model.TimelineEvent
import com.keepaside.aquapt.core.model.TimelineEventType
import com.keepaside.aquapt.core.model.WaterType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class TimelineViewModelTest {

    @Test
    fun `empty aquarium state is surfaced`() {
        val state = assembleTimelineUiState(
            aquariums = emptyList(),
            events = emptyList(),
            selectedAquariumId = null,
            selectedType = null,
            quickLogDraft = TimelineQuickLogDraft(),
            zoneId = ZoneOffset.UTC,
            statusMessage = null
        )

        assertTrue(state.isEmpty)
        assertEquals("Add your first tank to start building a care history.", state.headline)
        assertEquals(0, state.summary.aquariumCount)
    }

    @Test
    fun `timeline groups events by date in latest first order`() {
        val aquarium = Aquarium(
            id = "a-1",
            name = "Display",
            volumeLiters = 120.0,
            waterType = WaterType.FRESHWATER,
            setupDate = "2026-01-01"
        )
        val older = TimelineEvent(
            id = "event-old",
            aquariumId = aquarium.id,
            type = TimelineEventType.TASK,
            createdAt = "2026-04-10T08:00:00Z",
            title = "Water change"
        )
        val newer = TimelineEvent(
            id = "event-new",
            aquariumId = aquarium.id,
            type = TimelineEventType.MEMO,
            createdAt = "2026-04-11T09:30:00Z",
            title = "Memo",
            description = "Fish are active."
        )

        val state = assembleTimelineUiState(
            aquariums = listOf(aquarium),
            events = listOf(older, newer),
            selectedAquariumId = null,
            selectedType = null,
            quickLogDraft = TimelineQuickLogDraft(),
            zoneId = ZoneOffset.UTC,
            statusMessage = null
        )

        assertFalse(state.isEmpty)
        assertEquals(2, state.dayGroups.size)
        assertEquals("2026-04-11", state.dayGroups.first().dateLabel)
        assertEquals("Memo", state.dayGroups.first().events.first().title)
        assertEquals("Display", state.dayGroups.first().events.first().aquariumName)
    }

    @Test
    fun `timeline applies aquarium and type filters`() {
        val display = Aquarium(
            id = "a-display",
            name = "Display",
            volumeLiters = 180.0,
            waterType = WaterType.FRESHWATER
        )
        val nano = Aquarium(
            id = "a-nano",
            name = "Nano",
            volumeLiters = 38.0,
            waterType = WaterType.BRACKISH
        )
        val events = listOf(
            TimelineEvent(
                id = "event-1",
                aquariumId = display.id,
                type = TimelineEventType.TASK,
                createdAt = "2026-04-11T07:00:00Z",
                title = "Feed fish"
            ),
            TimelineEvent(
                id = "event-2",
                aquariumId = display.id,
                type = TimelineEventType.MEMO,
                createdAt = "2026-04-11T08:00:00Z",
                title = "Memo"
            ),
            TimelineEvent(
                id = "event-3",
                aquariumId = nano.id,
                type = TimelineEventType.MEMO,
                createdAt = "2026-04-11T09:00:00Z",
                title = "Nano memo"
            )
        )

        val state = assembleTimelineUiState(
            aquariums = listOf(display, nano),
            events = events,
            selectedAquariumId = display.id,
            selectedType = TimelineEventType.MEMO,
            quickLogDraft = TimelineQuickLogDraft(aquariumId = display.id),
            zoneId = ZoneOffset.UTC,
            statusMessage = "Memo added to Display."
        )

        assertEquals(1, state.summary.visibleEventCount)
        assertEquals("Memo", state.dayGroups.single().events.single().title)
        assertEquals(display.id, state.quickLogDraft.aquariumId)
        assertEquals("Memo added to Display.", state.statusMessage)
    }

    @Test
    fun `quick memo draft carries custom timestamp and photo uri`() {
        val aquarium = Aquarium(
            id = "a-display",
            name = "Display",
            volumeLiters = 180.0,
            waterType = WaterType.FRESHWATER
        )
        val draft = TimelineQuickLogDraft(
            aquariumId = aquarium.id,
            memoContent = "Trim plants after water change.",
            createdAtInput = "2026-04-11 18:30",
            photoUri = "content://photos/memo-1"
        )

        val state = assembleTimelineUiState(
            aquariums = listOf(aquarium),
            events = emptyList(),
            selectedAquariumId = aquarium.id,
            selectedType = null,
            quickLogDraft = draft,
            zoneId = ZoneOffset.UTC,
            statusMessage = null
        )

        assertEquals("2026-04-11 18:30", state.quickLogDraft.createdAtInput)
        assertEquals("content://photos/memo-1", state.quickLogDraft.photoUri)
    }

    @Test
    fun `timeline memo date-time input accepts friendly and iso formats`() {
        assertEquals(
            Instant.parse("2026-04-11T18:30:00Z"),
            parseTimelineDateTimeInput("2026-04-11 18:30", ZoneOffset.UTC)
        )
        assertEquals(
            Instant.parse("2026-04-11T18:30:00Z"),
            parseTimelineDateTimeInput("2026-04-11T18:30:00Z", ZoneOffset.UTC)
        )
        assertEquals(
            Instant.parse("2026-04-11T00:00:00Z"),
            parseTimelineDateTimeInput("2026-04-11", ZoneOffset.UTC)
        )
        assertNull(parseTimelineDateTimeInput("not a date", ZoneOffset.UTC))
    }

    @Test
    fun `unknown aquarium labels gracefully`() {
        val state = assembleTimelineUiState(
            aquariums = emptyList(),
            events = listOf(
                TimelineEvent(
                    id = "event-orphan",
                    aquariumId = "missing",
                    type = TimelineEventType.ISSUE,
                    createdAt = "not-a-date",
                    title = "Orphaned issue"
                )
            ),
            selectedAquariumId = null,
            selectedType = null,
            quickLogDraft = TimelineQuickLogDraft(),
            zoneId = ZoneOffset.UTC,
            statusMessage = null
        )

        assertEquals("Unknown date", state.dayGroups.single().dateLabel)
        assertEquals("Unknown tank", state.dayGroups.single().events.single().aquariumName)
    }

    @Test
    fun `quick parameter draft parses valid finite values only`() {
        val draft = TimelineQuickLogDraft(
            type = TimelineQuickLogType.PARAMETER,
            nitrate = "12.5",
            ph = "7.4",
            temperatureC = "26"
        )

        val values = draft.toWaterParameters()

        assertEquals(12.5, values?.nitrate)
        assertEquals(7.4, values?.ph)
        assertEquals(26.0, values?.temperatureC)
        assertEquals(parameterLogErrorMessage, validateQuickLogDraft(draft.copy(ph = "NaN")))
        assertEquals(parameterLogErrorMessage, validateQuickLogDraft(TimelineQuickLogDraft(type = TimelineQuickLogType.PARAMETER)))
    }

    @Test
    fun `quick dosing draft requires positive finite amount`() {
        assertEquals(2.5, parsePositiveAmountMl("2.5"))
        assertNull(parsePositiveAmountMl("0"))
        assertNull(parsePositiveAmountMl("-1"))
        assertNull(parsePositiveAmountMl("NaN"))

        val draft = TimelineQuickLogDraft(
            type = TimelineQuickLogType.DOSING,
            dosingProduct = "Flourish",
            dosingAmountMl = "1.5"
        )

        assertNull(validateQuickLogDraft(draft))
        assertEquals(dosingAmountErrorMessage, validateQuickLogDraft(draft.copy(dosingAmountMl = "not a number")))
    }
}
