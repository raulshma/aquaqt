package com.keepaside.aquapt.feature.settings

import com.keepaside.aquapt.core.assistant.OpenRouterModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsModelBrowserViewModelTest {

    @Test
    fun `filterModels returns all models when query is empty`() {
        val models = listOf(
            OpenRouterModel(id = "openai/gpt-4o", name = "GPT-4o"),
            OpenRouterModel(id = "anthropic/claude-3", name = "Claude 3")
        )
        val result = filterModels(models, "")
        assertEquals(2, result.size)
    }

    @Test
    fun `filterModels matches against id and name case-insensitively`() {
        val models = listOf(
            OpenRouterModel(id = "openai/gpt-4o", name = "GPT-4o"),
            OpenRouterModel(id = "anthropic/claude-3", name = "Claude 3"),
            OpenRouterModel(id = "meta/llama-3", name = "LLaMA 3")
        )
        val result = filterModels(models, "GPT")
        assertEquals(1, result.size)
        assertEquals("openai/gpt-4o", result[0].id)
    }

    @Test
    fun `filterModels matches against id when name is null`() {
        val models = listOf(
            OpenRouterModel(id = "openai/gpt-4o", name = null),
            OpenRouterModel(id = "anthropic/claude-3", name = null)
        )
        val result = filterModels(models, "claude")
        assertEquals(1, result.size)
        assertEquals("anthropic/claude-3", result[0].id)
    }

    @Test
    fun `sortModels by name sorts alphabetically using name or id fallback`() {
        val models = listOf(
            OpenRouterModel(id = "z-model", name = "Zeta"),
            OpenRouterModel(id = "a-model", name = "Alpha"),
            OpenRouterModel(id = "m-model", name = null)
        )
        val result = sortModels(models, ModelBrowserSort.NAME)
        assertEquals("Alpha", result[0].name)
        assertEquals("m-model", result[1].id)
        assertEquals("Zeta", result[2].name)
    }

    @Test
    fun `sortModels by created sorts newest first`() {
        val models = listOf(
            OpenRouterModel(id = "old", created = 1000L),
            OpenRouterModel(id = "new", created = 9999L),
            OpenRouterModel(id = "mid", created = 5000L)
        )
        val result = sortModels(models, ModelBrowserSort.CREATED)
        assertEquals("new", result[0].id)
        assertEquals("mid", result[1].id)
        assertEquals("old", result[2].id)
    }

    @Test
    fun `sortModels by context sorts largest first`() {
        val models = listOf(
            OpenRouterModel(id = "small", contextLength = 4096L),
            OpenRouterModel(id = "large", contextLength = 128000L),
            OpenRouterModel(id = "medium", contextLength = 32768L)
        )
        val result = sortModels(models, ModelBrowserSort.CONTEXT)
        assertEquals("large", result[0].id)
        assertEquals("medium", result[1].id)
        assertEquals("small", result[2].id)
    }

    @Test
    fun `isFreeModel identifies free models by id suffix`() {
        val model = OpenRouterModel(id = "meta/llama-3:free")
        assertTrue(isFreeModel(model))
    }

    @Test
    fun `isFreeModel identifies free models by zero pricing`() {
        val model = OpenRouterModel(
            id = "meta/llama-3",
            promptPrice = 0.0,
            completionPrice = 0.0
        )
        assertTrue(isFreeModel(model))
    }

    @Test
    fun `isFreeModel identifies paid models`() {
        val model = OpenRouterModel(
            id = "openai/gpt-4o",
            promptPrice = 0.00001,
            completionPrice = 0.00003
        )
        assertFalse(isFreeModel(model))
    }

    @Test
    fun `isFreeModel treats null pricing as paid`() {
        val model = OpenRouterModel(id = "some/model")
        assertFalse(isFreeModel(model))
    }

    @Test
    fun `partitionByPricing splits free and paid correctly`() {
        val models = listOf(
            OpenRouterModel(id = "free-1", promptPrice = 0.0, completionPrice = 0.0),
            OpenRouterModel(id = "paid-1", promptPrice = 0.01, completionPrice = 0.03),
            OpenRouterModel(id = "free-2:free"),
            OpenRouterModel(id = "paid-2", promptPrice = 0.001, completionPrice = 0.0)
        )
        val (free, paid) = partitionByPricing(models)
        assertEquals(2, free.size)
        assertEquals(2, paid.size)
        assertEquals("free-1", free[0].id)
        assertEquals("free-2:free", free[1].id)
    }

    @Test
    fun `ModelBrowserUiState groupedModels applies filter sort and truncation`() {
        val models = (1..50).map { i ->
            OpenRouterModel(
                id = "model-$i:free",
                name = "Model $i",
                created = i.toLong(),
                contextLength = (i * 1000).toLong()
            )
        } + (1..50).map { i ->
            OpenRouterModel(
                id = "paid-$i",
                name = "Paid $i",
                promptPrice = 0.01,
                completionPrice = 0.03
            )
        }

        val state = ModelBrowserUiState(models = models, query = "model", sort = ModelBrowserSort.NAME)
        val grouped = state.groupedModels

        assertTrue(grouped.free.size <= MAX_MODELS_PER_GROUP)
        assertTrue(grouped.paid.size <= MAX_MODELS_PER_GROUP)
    }

    @Test
    fun `ModelBrowserUiState isTruncated reflects group caps`() {
        val models = (1..45).map { i ->
            OpenRouterModel(id = "free-$i:free")
        }
        val state = ModelBrowserUiState(models = models)
        assertTrue(state.isTruncated)
    }

    @Test
    fun `ModelBrowserUiState isTruncated false when under cap`() {
        val models = listOf(
            OpenRouterModel(id = "free-1:free"),
            OpenRouterModel(id = "paid-1", promptPrice = 0.01)
        )
        val state = ModelBrowserUiState(models = models)
        assertFalse(state.isTruncated)
    }

    @Test
    fun `ModelBrowserUiState summaryLabel includes counts and sort label`() {
        val state = ModelBrowserUiState(
            models = listOf(
                OpenRouterModel(id = "free-1:free"),
                OpenRouterModel(id = "paid-1", promptPrice = 0.01)
            ),
            sort = ModelBrowserSort.CONTEXT
        )
        val label = state.summaryLabel
        assertTrue(label.contains("2 models shown"))
        assertTrue(label.contains("1 free"))
        assertTrue(label.contains("1 paid"))
        assertTrue(label.contains("sort: context"))
    }

    @Test
    fun `formatModelCreatedDate returns dash for null`() {
        assertEquals("-", formatModelCreatedDate(null))
    }

    @Test
    fun `formatModelCreatedDate handles seconds epoch`() {
        val result = formatModelCreatedDate(1704067200L)
        assertTrue(result.isNotEmpty())
        assertFalse(result.startsWith("-"))
    }

    @Test
    fun `formatModelCreatedDate handles millis epoch`() {
        val result = formatModelCreatedDate(1704067200000L)
        assertTrue(result.isNotEmpty())
        assertFalse(result.startsWith("-"))
    }

    @Test
    fun `ModelBrowserTarget enum has assistant and memory entries`() {
        assertEquals(2, ModelBrowserTarget.entries.size)
        assertEquals(ModelBrowserTarget.ASSISTANT, ModelBrowserTarget.valueOf("ASSISTANT"))
        assertEquals(ModelBrowserTarget.MEMORY, ModelBrowserTarget.valueOf("MEMORY"))
    }

    @Test
    fun `ModelBrowserSort enum has name created context entries`() {
        assertEquals(3, ModelBrowserSort.entries.size)
    }

    @Test
    fun `filterModels handles trimmed whitespace query`() {
        val models = listOf(
            OpenRouterModel(id = "openai/gpt-4o", name = "GPT-4o"),
            OpenRouterModel(id = "anthropic/claude-3", name = "Claude 3")
        )
        val result = filterModels(models, "  gpt  ")
        assertEquals(1, result.size)
        assertEquals("openai/gpt-4o", result[0].id)
    }
}
