package com.keepaside.aquapt.core.repository

import com.keepaside.aquapt.core.model.AssistantMemorySnippet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AssistantMemoryRankingTest {

    @Test
    fun `manual snippets outrank conversation turns for similar lexical match`() {
        val ranked = AssistantMemoryRanking.rank(
            snippets = listOf(
                AssistantMemorySnippet(
                    id = "turn-1",
                    content = "Use weekly water change reminders for reef aquarium husbandry.",
                    category = "conversation_turn",
                    createdAt = "2026-04-10T11:00:00Z"
                ),
                AssistantMemorySnippet(
                    id = "manual-1",
                    content = "Use weekly water change reminders for the display reef tank.",
                    category = "manual",
                    createdAt = "2026-04-08T11:00:00Z"
                )
            ),
            prompt = "Need a weekly water change reminder plan",
            limit = 2,
            now = Instant.parse("2026-04-11T12:00:00Z")
        )

        assertEquals(listOf("manual-1", "turn-1"), ranked.map { it.id })
        assertTrue((ranked[0].similarity ?: 0.0) > (ranked[1].similarity ?: 0.0))
    }

    @Test
    fun `same conversation snippets receive ranking boost`() {
        val ranked = AssistantMemoryRanking.rank(
            snippets = listOf(
                AssistantMemorySnippet(
                    id = "manual-other",
                    content = "Dose calcium every evening after lights out.",
                    category = "manual",
                    sourceConversationId = "conv-other",
                    createdAt = "2026-04-11T10:00:00Z"
                ),
                AssistantMemorySnippet(
                    id = "manual-active",
                    content = "Dose calcium every evening after lights out.",
                    category = "manual",
                    sourceConversationId = "conv-active",
                    createdAt = "2026-04-09T10:00:00Z"
                )
            ),
            prompt = "How should I dose calcium in the evening?",
            conversationId = "conv-active",
            limit = 2,
            now = Instant.parse("2026-04-11T12:00:00Z")
        )

        assertEquals(1, ranked.size)
        assertEquals("manual-active", ranked.first().id)
    }

    @Test
    fun `phrase continuity boosts exact context matches`() {
        val ranked = AssistantMemoryRanking.rank(
            snippets = listOf(
                AssistantMemorySnippet(
                    id = "exact-order",
                    content = "Track nitrate trend alert for display tank each week.",
                    category = "conversation_turn",
                    createdAt = "2026-04-11T09:00:00Z"
                ),
                AssistantMemorySnippet(
                    id = "shuffled-order",
                    content = "Track nitrate each week and review display alert trend.",
                    category = "conversation_turn",
                    createdAt = "2026-04-11T09:30:00Z"
                )
            ),
            prompt = "nitrate trend alert display tank",
            limit = 2,
            now = Instant.parse("2026-04-11T12:00:00Z")
        )

        assertEquals("exact-order", ranked.first().id)
        assertTrue((ranked.first().similarity ?: 0.0) > (ranked.last().similarity ?: 0.0))
    }

    @Test
    fun `ranking respects limit and filters unrelated snippets`() {
        val ranked = AssistantMemoryRanking.rank(
            snippets = listOf(
                AssistantMemorySnippet(
                    id = "relevant-1",
                    content = "Schedule freshwater reminder hours for Sunday maintenance.",
                    category = "manual",
                    createdAt = "2026-04-11T07:00:00Z"
                ),
                AssistantMemorySnippet(
                    id = "relevant-2",
                    content = "Sunday maintenance includes reminder checks and task review.",
                    category = "conversation_turn",
                    createdAt = "2026-04-11T08:00:00Z"
                ),
                AssistantMemorySnippet(
                    id = "unrelated",
                    content = "Camera battery for photo capture is low.",
                    category = "conversation_turn",
                    createdAt = "2026-04-11T11:00:00Z"
                )
            ),
            prompt = "Sunday reminder maintenance",
            limit = 2,
            now = Instant.parse("2026-04-11T12:00:00Z")
        )

        assertEquals(2, ranked.size)
        assertTrue(ranked.none { it.id == "unrelated" })
    }
}
