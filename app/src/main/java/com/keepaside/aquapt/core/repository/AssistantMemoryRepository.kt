package com.keepaside.aquapt.core.repository

import android.content.Context
import android.content.SharedPreferences
import com.keepaside.aquapt.core.model.AssistantMemoryCompactionPreview
import com.keepaside.aquapt.core.model.AssistantMemorySnippet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

interface AssistantMemoryStore {
    val snippets: StateFlow<List<AssistantMemorySnippet>>

    suspend fun rememberTurn(
        conversationId: String,
        userMessageId: String,
        userPrompt: String,
        assistantText: String
    )

    suspend fun rememberManualSnippet(
        conversationId: String,
        sourceMessageId: String,
        content: String
    ): String?

    suspend fun forgetManualSnippet(
        conversationId: String,
        sourceMessageId: String
    )

    suspend fun forgetSnippet(id: String)

    suspend fun clearAllSnippets()

    suspend fun queryRelevantSnippets(
        prompt: String,
        limit: Int = 4,
        conversationId: String? = null
    ): List<AssistantMemorySnippet>

    suspend fun previewCompaction(
        maxFacts: Int = 10
    ): AssistantMemoryCompactionPreview

    suspend fun applyCompaction(
        precomputedFacts: List<String> = emptyList(),
        maxFacts: Int = 10
    ): AssistantMemoryCompactionPreview
}

class AssistantMemoryRepository(
    context: Context
) : AssistantMemoryStore {

    private val preferences: SharedPreferences = context.getSharedPreferences(
        preferencesFile,
        Context.MODE_PRIVATE
    )

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val _snippets = MutableStateFlow(readSnippets())
    override val snippets: StateFlow<List<AssistantMemorySnippet>> = _snippets.asStateFlow()

    override suspend fun rememberTurn(
        conversationId: String,
        userMessageId: String,
        userPrompt: String,
        assistantText: String
    ) {
        val normalizedConversationId = conversationId.trim()
        val normalizedUserMessageId = userMessageId.trim()
        if (normalizedConversationId.isEmpty() || normalizedUserMessageId.isEmpty()) {
            return
        }

        val document = buildTurnMemoryDocument(
            userPrompt = userPrompt,
            assistantText = assistantText
        )
        if (document.isBlank()) {
            return
        }

        val snippetId = buildTurnSnippetId(normalizedConversationId, normalizedUserMessageId)
        upsertSnippet(
            AssistantMemorySnippet(
                id = snippetId,
                content = document,
                category = categoryConversationTurn,
                createdAt = nowIso(),
                sourceConversationId = normalizedConversationId,
                sourceMessageId = normalizedUserMessageId
            )
        )
    }

    override suspend fun rememberManualSnippet(
        conversationId: String,
        sourceMessageId: String,
        content: String
    ): String? {
        val normalizedConversationId = conversationId.trim()
        val normalizedSourceMessageId = sourceMessageId.trim()
        if (normalizedConversationId.isEmpty() || normalizedSourceMessageId.isEmpty()) {
            return null
        }

        val normalizedContent = clamp(normalizeText(content), maxMemoryChars)
        if (normalizedContent.isBlank()) {
            return null
        }

        val snippetId = buildManualSnippetId(
            conversationId = normalizedConversationId,
            sourceMessageId = normalizedSourceMessageId
        )

        upsertSnippet(
            AssistantMemorySnippet(
                id = snippetId,
                content = normalizedContent,
                category = categoryManual,
                createdAt = nowIso(),
                sourceConversationId = normalizedConversationId,
                sourceMessageId = normalizedSourceMessageId
            )
        )

        return snippetId
    }

    override suspend fun forgetManualSnippet(
        conversationId: String,
        sourceMessageId: String
    ) {
        val snippetId = buildManualSnippetId(conversationId.trim(), sourceMessageId.trim())
        forgetSnippet(snippetId)
    }

    override suspend fun forgetSnippet(id: String) {
        val normalized = id.trim()
        if (normalized.isEmpty()) return

        writeSnippets(
            snippets.value.filterNot { snippet -> snippet.id == normalized }
        )
    }

    override suspend fun clearAllSnippets() {
        writeSnippets(emptyList())
    }

    override suspend fun queryRelevantSnippets(
        prompt: String,
        limit: Int,
        conversationId: String?
    ): List<AssistantMemorySnippet> {
        val normalizedPrompt = normalizeText(prompt)
        if (normalizedPrompt.isEmpty()) {
            return emptyList()
        }

        val effectiveLimit = limit.coerceIn(1, 12)

        return AssistantMemoryRanking.rank(
            snippets = snippets.value,
            prompt = normalizedPrompt,
            limit = effectiveLimit,
            conversationId = conversationId
        )
    }

    override suspend fun previewCompaction(maxFacts: Int): AssistantMemoryCompactionPreview {
        val current = snippets.value
        if (current.isEmpty()) {
            return AssistantMemoryCompactionPreview(
                beforeCount = 0,
                afterCount = 0,
                facts = emptyList()
            )
        }

        val effectiveMaxFacts = maxFacts.coerceIn(1, 20)
        val facts = extractCompactFactCandidates(current)
            .take(effectiveMaxFacts)

        return AssistantMemoryCompactionPreview(
            beforeCount = current.size,
            afterCount = facts.size,
            facts = facts
        )
    }

    override suspend fun applyCompaction(
        precomputedFacts: List<String>,
        maxFacts: Int
    ): AssistantMemoryCompactionPreview {
        val current = snippets.value
        if (current.isEmpty()) {
            return AssistantMemoryCompactionPreview(
                beforeCount = 0,
                afterCount = 0,
                facts = emptyList()
            )
        }

        val effectiveMaxFacts = maxFacts.coerceIn(1, 20)

        val normalizedPrecomputedFacts = precomputedFacts
            .map { fact -> clamp(normalizeText(fact), maxCompactFactChars) }
            .filter { fact -> fact.isNotEmpty() }
            .distinct()
            .take(effectiveMaxFacts)

        val finalFacts = if (normalizedPrecomputedFacts.isNotEmpty()) {
            normalizedPrecomputedFacts
        } else {
            extractCompactFactCandidates(current)
                .take(effectiveMaxFacts)
        }

        if (finalFacts.isEmpty()) {
            return AssistantMemoryCompactionPreview(
                beforeCount = current.size,
                afterCount = 0,
                facts = emptyList()
            )
        }

        val compactedAt = nowIso()
        val compactedSnippets = finalFacts.mapIndexed { index, fact ->
            AssistantMemorySnippet(
                id = buildCompactedSnippetId(compactedAt, index),
                content = fact,
                category = categoryCompactedFact,
                createdAt = compactedAt
            )
        }

        writeSnippets(compactedSnippets)

        return AssistantMemoryCompactionPreview(
            beforeCount = current.size,
            afterCount = compactedSnippets.size,
            facts = compactedSnippets.map { snippet -> snippet.content }
        )
    }

    private fun upsertSnippet(snippet: AssistantMemorySnippet) {
        val normalized = snippet.copy(
            content = clamp(normalizeText(snippet.content), maxMemoryChars)
        )

        if (normalized.content.isBlank()) {
            return
        }

        val current = snippets.value
        val updated = current.filterNot { existing -> existing.id == normalized.id } + normalized
        writeSnippets(updated)
    }

    private fun readSnippets(): List<AssistantMemorySnippet> {
        val raw = preferences.getString(keySnippets, null) ?: return emptyList()

        return runCatching {
            json.decodeFromString(
                ListSerializer(AssistantMemorySnippet.serializer()),
                raw
            )
        }.getOrDefault(emptyList())
            .normalizeAndSort()
    }

    private fun writeSnippets(next: List<AssistantMemorySnippet>) {
        val normalized = next.normalizeAndSort()

        preferences.edit()
            .putString(
                keySnippets,
                json.encodeToString(
                    ListSerializer(AssistantMemorySnippet.serializer()),
                    normalized
                )
            )
            .apply()

        _snippets.update { normalized }
    }

    private fun List<AssistantMemorySnippet>.normalizeAndSort(): List<AssistantMemorySnippet> =
        mapNotNull { snippet ->
            val normalizedId = snippet.id.trim()
            val normalizedContent = clamp(normalizeText(snippet.content), maxMemoryChars)

            if (normalizedId.isEmpty() || normalizedContent.isEmpty()) {
                null
            } else {
                snippet.copy(
                    id = normalizedId,
                    content = normalizedContent
                )
            }
        }.distinctBy { snippet -> snippet.id }
            .sortedByDescending { snippet -> snippet.createdAt.orEmpty() }

    private fun buildTurnMemoryDocument(
        userPrompt: String,
        assistantText: String
    ): String {
        val normalizedPrompt = normalizeText(userPrompt)
        val normalizedReply = normalizeText(assistantText)

        val promptSentences = splitSentences(userPrompt)
        val assistantSentences = splitSentences(assistantText)

        val preferenceHints = promptSentences.filter { sentence ->
            preferenceRegex.containsMatchIn(sentence)
        }

        val actionableHints = assistantSentences.filter { sentence ->
            actionableRegex.containsMatchIn(sentence)
        }

        val structuredLines = (preferenceHints.take(3) + actionableHints.take(2))
            .take(4)

        val document = if (structuredLines.isNotEmpty()) {
            buildString {
                appendLine("Memory facts:")
                structuredLines.forEachIndexed { index, line ->
                    appendLine("${index + 1}. $line")
                }
                if (normalizedPrompt.isNotBlank()) {
                    append("Source question: $normalizedPrompt")
                }
            }
        } else {
            "User asked: $normalizedPrompt\nAssistant answered: $normalizedReply"
        }

        return clamp(normalizeText(document), maxMemoryChars)
    }

    private fun extractCompactFactCandidates(snippets: List<AssistantMemorySnippet>): List<String> {
        val deduped = LinkedHashSet<String>()

        snippets.forEach { snippet ->
            snippet.content
                .split(Regex("\\n+"))
                .map { line ->
                    normalizeText(
                        line
                            .replace(Regex("^\\d+[.)]\\s*"), "")
                            .replace(Regex("^[-*•]\\s*"), "")
                            .replace(Regex("^memory facts:\\s*", RegexOption.IGNORE_CASE), "")
                            .replace(Regex("^source question:\\s*", RegexOption.IGNORE_CASE), "")
                            .replace(Regex("^user asked:\\s*", RegexOption.IGNORE_CASE), "")
                            .replace(Regex("^assistant answered:\\s*", RegexOption.IGNORE_CASE), "")
                    )
                }
                .filter { fact -> fact.length >= 12 }
                .map { fact -> clamp(fact, maxCompactFactChars) }
                .forEach { fact -> deduped += fact }
        }

        return deduped.toList()
    }

    private fun splitSentences(text: String): List<String> =
        text.split(Regex("(?<=[.!?])\\s+"))
            .map(::normalizeText)
            .filter { it.isNotEmpty() }

    private fun normalizeText(value: String): String =
        value
            .replace(Regex("\\s+"), " ")
            .replace(Regex("[\\u0000-\\u001F]"), "")
            .trim()

    private fun clamp(value: String, max: Int): String =
        if (value.length <= max) {
            value
        } else {
            value.take(max - 1) + "…"
        }

    private fun buildTurnSnippetId(
        conversationId: String,
        userMessageId: String
    ): String = "conv:$conversationId:msg:$userMessageId"

    private fun buildManualSnippetId(
        conversationId: String,
        sourceMessageId: String
    ): String = "$manualMemoryPrefix:conv:$conversationId:msg:$sourceMessageId"

    private fun buildCompactedSnippetId(
        compactedAtIso: String,
        index: Int
    ): String = "$compactedMemoryPrefix:fact:$compactedAtIso:$index"

    private fun nowIso(): String = java.time.Instant.now().toString()

    companion object {
        private const val preferencesFile = "aquapt_assistant_memory"
        private const val keySnippets = "assistant_memory_snippets"

        private const val maxMemoryChars = 1200
        private const val maxCompactFactChars = 220

        private const val categoryConversationTurn = "conversation_turn"
        private const val categoryManual = "manual"
        private const val categoryCompactedFact = "compacted_fact"
        private const val manualMemoryPrefix = "manual"
        private const val compactedMemoryPrefix = "compact"

        private val preferenceRegex = Regex(
            "(prefer|usually|always|never|only|schedule|reminder|tank|aquarium|shrimp|marine|freshwater)",
            RegexOption.IGNORE_CASE
        )

        private val actionableRegex = Regex(
            "(recommend|should|avoid|next step|watch|monitor|dose|change|maintain)",
            RegexOption.IGNORE_CASE
        )
    }
}
