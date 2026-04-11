package com.keepaside.aquapt.core.repository

import com.keepaside.aquapt.core.model.AssistantMemorySnippet
import java.time.Duration
import java.time.Instant

internal object AssistantMemoryRanking {

    fun rank(
        snippets: List<AssistantMemorySnippet>,
        prompt: String,
        limit: Int = 4,
        conversationId: String? = null,
        now: Instant = Instant.now()
    ): List<AssistantMemorySnippet> {
        val normalizedPrompt = normalizeText(prompt)
        if (normalizedPrompt.isBlank()) {
            return emptyList()
        }

        val queryTokens = tokenize(normalizedPrompt)
        if (queryTokens.isEmpty()) {
            return emptyList()
        }

        val queryTokenSet = queryTokens.toSet()
        val queryBigrams = toNgrams(queryTokens, 2)
        val queryCanonicalText = queryTokens.joinToString(" ")
        val normalizedConversationId = conversationId?.trim()?.takeIf { it.isNotEmpty() }
        val effectiveLimit = limit.coerceIn(1, 12)

        return snippets
            .mapNotNull { snippet ->
                scoreSnippet(
                    snippet = snippet,
                    queryTokens = queryTokens,
                    queryTokenSet = queryTokenSet,
                    queryBigrams = queryBigrams,
                    queryCanonicalText = queryCanonicalText,
                    conversationId = normalizedConversationId,
                    now = now
                )
            }
            .groupBy { snippet -> normalizeText(snippet.content).lowercase() }
            .values
            .mapNotNull { candidates ->
                candidates.maxWithOrNull(
                    compareBy<AssistantMemorySnippet> { it.similarity ?: 0.0 }
                        .thenBy { it.createdAt.orEmpty() }
                )
            }
            .sortedWith(
                compareByDescending<AssistantMemorySnippet> { it.similarity ?: 0.0 }
                    .thenByDescending { it.createdAt.orEmpty() }
            )
            .take(effectiveLimit)
    }

    private fun scoreSnippet(
        snippet: AssistantMemorySnippet,
        queryTokens: List<String>,
        queryTokenSet: Set<String>,
        queryBigrams: Set<String>,
        queryCanonicalText: String,
        conversationId: String?,
        now: Instant
    ): AssistantMemorySnippet? {
        val normalizedContent = normalizeText(snippet.content)
        if (normalizedContent.isBlank()) {
            return null
        }

        val candidateTokens = tokenize(normalizedContent)
        if (candidateTokens.isEmpty()) {
            return null
        }

        val candidateTokenSet = candidateTokens.toSet()
        val overlapCount = queryTokenSet.count { token -> token in candidateTokenSet }
        val candidateBigrams = toNgrams(candidateTokens, 2)

        val normalizedCategory = snippet.category?.trim()?.lowercase().orEmpty()
        val isManualSnippet = normalizedCategory == "manual"
        val sameConversation = conversationId != null &&
            snippet.sourceConversationId?.trim() == conversationId

        val phraseBonus = computePhraseBonus(
            queryTokens = queryTokens,
            queryCanonicalText = queryCanonicalText,
            candidateCanonicalText = candidateTokens.joinToString(" ")
        )

        val bigramCoverage = if (queryBigrams.isEmpty()) {
            0.0
        } else {
            queryBigrams.count { bigram -> bigram in candidateBigrams }
                .toDouble() / queryBigrams.size.toDouble()
        }

        val hasLexicalSignal = overlapCount > 0 || phraseBonus > 0.0 || bigramCoverage > 0.0
        if (!hasLexicalSignal && !(sameConversation && isManualSnippet)) {
            return null
        }

        val coverage = overlapCount.toDouble() / queryTokenSet.size.toDouble()
        val precision = overlapCount.toDouble() / candidateTokenSet.size.toDouble()
        val jaccard = overlapCount.toDouble() /
            queryTokenSet.union(candidateTokenSet).size.coerceAtLeast(1).toDouble()

        val baseScore =
            (coverage * 0.52) +
                (precision * 0.20) +
                (jaccard * 0.14) +
                (bigramCoverage * 0.14)

        val score = baseScore +
            phraseBonus +
            categoryBoost(normalizedCategory) +
            (if (sameConversation) 0.12 else 0.0) +
            recencyBoost(snippet.createdAt, now)

        val minimumScore = if (sameConversation) 0.08 else 0.14
        if (score < minimumScore) {
            return null
        }

        return snippet.copy(similarity = score)
    }

    private fun computePhraseBonus(
        queryTokens: List<String>,
        queryCanonicalText: String,
        candidateCanonicalText: String
    ): Double {
        if (queryCanonicalText.length >= 14 &&
            candidateCanonicalText.contains(queryCanonicalText, ignoreCase = true)
        ) {
            return 0.18
        }

        val trigrams = toNgrams(queryTokens, 3)
        if (trigrams.isNotEmpty() &&
            trigrams.any { phrase -> candidateCanonicalText.contains(phrase, ignoreCase = true) }
        ) {
            return 0.12
        }

        val bigrams = toNgrams(queryTokens, 2)
        if (bigrams.isNotEmpty() &&
            bigrams.any { phrase -> candidateCanonicalText.contains(phrase, ignoreCase = true) }
        ) {
            return 0.08
        }

        return 0.0
    }

    private fun categoryBoost(category: String): Double = when (category) {
        "manual" -> 0.18
        "compacted_fact" -> 0.12
        "conversation_turn" -> 0.06
        else -> 0.0
    }

    private fun recencyBoost(createdAt: String?, now: Instant): Double {
        val instant = createdAt
            ?.trim()
            ?.takeIf { value -> value.isNotEmpty() }
            ?.let { value -> runCatching { Instant.parse(value) }.getOrNull() }
            ?: return 0.0

        val ageHours = Duration.between(instant, now).toHours().coerceAtLeast(0)
        return when {
            ageHours <= 24 -> 0.08
            ageHours <= (24 * 7) -> 0.05
            ageHours <= (24 * 30) -> 0.02
            else -> 0.0
        }
    }

    private fun tokenize(value: String): List<String> =
        Regex("[a-z0-9]{2,}", RegexOption.IGNORE_CASE)
            .findAll(value.lowercase())
            .map { match -> canonicalizeToken(match.value) }
            .filter { token -> token.length >= 2 && token !in stopWords }
            .toList()

    private fun canonicalizeToken(token: String): String {
        var value = token.lowercase()

        value = when {
            value.endsWith("ies") && value.length > 4 -> value.dropLast(3) + "y"
            value.endsWith("es") && value.length > 4 -> value.dropLast(2)
            value.endsWith("s") && value.length > 3 -> value.dropLast(1)
            else -> value
        }

        value = when {
            value.endsWith("ing") && value.length > 6 -> value.dropLast(3)
            value.endsWith("ed") && value.length > 5 -> value.dropLast(2)
            else -> value
        }

        return value
    }

    private fun toNgrams(tokens: List<String>, size: Int): Set<String> {
        if (size <= 1 || tokens.size < size) {
            return emptySet()
        }

        return tokens.windowed(size, 1)
            .map { window -> window.joinToString(" ") }
            .toSet()
    }

    private fun normalizeText(value: String): String =
        value
            .replace(Regex("\\s+"), " ")
            .replace(Regex("[\\u0000-\\u001F]"), "")
            .trim()

    private val stopWords = setOf(
        "a", "an", "and", "are", "as", "at", "be", "by", "for", "from",
        "how", "i", "in", "is", "it", "my", "of", "on", "or", "our", "the",
        "this", "that", "to", "was", "we", "what", "when", "where", "which",
        "who", "why", "with", "you", "your"
    )
}
