package com.keepaside.aquapt.feature.assistant

internal sealed interface AssistantMarkdownBlock {
    data class Heading(
        val level: Int,
        val content: String
    ) : AssistantMarkdownBlock

    data class Paragraph(
        val content: String
    ) : AssistantMarkdownBlock

    data class Quote(
        val content: String
    ) : AssistantMarkdownBlock

    data class CodeBlock(
        val language: String?,
        val code: String
    ) : AssistantMarkdownBlock

    data class ListBlock(
        val ordered: Boolean,
        val items: List<AssistantMarkdownListItem>
    ) : AssistantMarkdownBlock
}

internal data class AssistantMarkdownListItem(
    val content: String,
    val taskState: Boolean? = null
)

private data class ParsedListLine(
    val ordered: Boolean,
    val content: String,
    val taskState: Boolean? = null
)

private val headingPattern = Regex("""^(#{1,6})\s+(.+)$""")
private val fencedCodeStartPattern = Regex("""^```\s*([^`\s]+)?\s*$""")
private val fencedCodeEndPattern = Regex("""^```\s*$""")
private val unorderedTaskPattern = Regex("""^[-*+]\s+\[( |x|X)]\s+(.+)$""")
private val unorderedListPattern = Regex("""^[-*+]\s+(.+)$""")
private val orderedListPattern = Regex("""^\d+[.)]\s+(.+)$""")
private val linkPattern = Regex("""\[([^\]]+)]\((https?://[^)\s]+)\)""")
private val inlineCodePattern = Regex("""`([^`]+)`""")
private val strongPattern = Regex("""\*\*([^*]+)\*\*""")
private val emphasisPattern = Regex("""(?<!\*)\*([^*\n]+)\*(?!\*)""")
private val whitespacePattern = Regex("""\s+""")

internal fun parseAssistantMarkdownBlocks(markdown: String): List<AssistantMarkdownBlock> {
    if (markdown.isBlank()) return emptyList()

    val normalized = markdown
        .replace("\r\n", "\n")
        .replace('\r', '\n')

    val lines = normalized.split('\n')
    val blocks = mutableListOf<AssistantMarkdownBlock>()

    var index = 0
    while (index < lines.size) {
        val trimmed = lines[index].trim()

        if (trimmed.isEmpty()) {
            index += 1
            continue
        }

        val codeStartMatch = fencedCodeStartPattern.matchEntire(trimmed)
        if (codeStartMatch != null) {
            val language = codeStartMatch.groupValues[1].trim().ifBlank { null }
            val codeLines = mutableListOf<String>()
            index += 1

            while (index < lines.size) {
                val current = lines[index]
                if (fencedCodeEndPattern.matches(current.trim())) {
                    index += 1
                    break
                }
                codeLines += current
                index += 1
            }

            blocks += AssistantMarkdownBlock.CodeBlock(
                language = language,
                code = codeLines.joinToString("\n").trimEnd()
            )
            continue
        }

        val headingMatch = headingPattern.matchEntire(trimmed)
        if (headingMatch != null) {
            blocks += AssistantMarkdownBlock.Heading(
                level = headingMatch.groupValues[1].length,
                content = headingMatch.groupValues[2].trim()
            )
            index += 1
            continue
        }

        if (trimmed.startsWith(">")) {
            val quoteLines = mutableListOf<String>()
            while (index < lines.size) {
                val currentTrimmed = lines[index].trim()
                if (!currentTrimmed.startsWith(">")) {
                    break
                }

                val content = currentTrimmed.removePrefix(">").trim()
                if (content.isNotEmpty()) {
                    quoteLines += content
                }
                index += 1
            }

            val quoteContent = quoteLines.joinToString("\n").trim()
            if (quoteContent.isNotEmpty()) {
                blocks += AssistantMarkdownBlock.Quote(quoteContent)
            }
            continue
        }

        val firstListLine = parseListLine(trimmed)
        if (firstListLine != null) {
            val ordered = firstListLine.ordered
            val listItems = mutableListOf<AssistantMarkdownListItem>()

            while (index < lines.size) {
                val parsed = parseListLine(lines[index].trim()) ?: break
                if (parsed.ordered != ordered) {
                    break
                }

                listItems += AssistantMarkdownListItem(
                    content = parsed.content,
                    taskState = parsed.taskState
                )
                index += 1
            }

            if (listItems.isNotEmpty()) {
                blocks += AssistantMarkdownBlock.ListBlock(
                    ordered = ordered,
                    items = listItems
                )
            }
            continue
        }

        val paragraphLines = mutableListOf<String>()
        while (index < lines.size) {
            val currentTrimmed = lines[index].trim()
            if (currentTrimmed.isEmpty()) {
                break
            }

            val startsAnotherBlock =
                fencedCodeStartPattern.matches(currentTrimmed) ||
                    headingPattern.matches(currentTrimmed) ||
                    currentTrimmed.startsWith(">") ||
                    parseListLine(currentTrimmed) != null

            if (startsAnotherBlock) {
                break
            }

            paragraphLines += currentTrimmed
            index += 1
        }

        val paragraph = paragraphLines.joinToString(" ").normalizeWhitespace()
        if (paragraph.isNotEmpty()) {
            blocks += AssistantMarkdownBlock.Paragraph(paragraph)
        }

        if (index < lines.size && lines[index].trim().isEmpty()) {
            index += 1
        }
    }

    return blocks
}

internal fun assistantMarkdownPreviewText(markdown: String, maxLength: Int = 72): String {
    if (maxLength <= 0 || markdown.isBlank()) return ""

    val fromBlocks = parseAssistantMarkdownBlocks(markdown)
        .joinToString(" ") { block ->
            when (block) {
                is AssistantMarkdownBlock.Heading -> block.content
                is AssistantMarkdownBlock.Paragraph -> block.content
                is AssistantMarkdownBlock.Quote -> block.content
                is AssistantMarkdownBlock.CodeBlock -> block.code
                    .lineSequence()
                    .firstOrNull()
                    .orEmpty()
                is AssistantMarkdownBlock.ListBlock ->
                    block.items.joinToString(" ") { item -> item.content }
            }
        }

    val normalized = stripInlineMarkdown(
        if (fromBlocks.isNotBlank()) {
            fromBlocks
        } else {
            markdown
        }
    ).normalizeWhitespace()

    if (normalized.length <= maxLength) {
        return normalized
    }

    return normalized
        .take(maxLength)
        .trimEnd()
        .trimEnd('.', ',', ';', ':') + "…"
}

private fun parseListLine(trimmedLine: String): ParsedListLine? {
    val taskMatch = unorderedTaskPattern.matchEntire(trimmedLine)
    if (taskMatch != null) {
        val checked = taskMatch.groupValues[1].equals("x", ignoreCase = true)
        val content = taskMatch.groupValues[2].trim()
        return ParsedListLine(
            ordered = false,
            content = content,
            taskState = checked
        )
    }

    val unorderedMatch = unorderedListPattern.matchEntire(trimmedLine)
    if (unorderedMatch != null) {
        return ParsedListLine(
            ordered = false,
            content = unorderedMatch.groupValues[1].trim()
        )
    }

    val orderedMatch = orderedListPattern.matchEntire(trimmedLine)
    if (orderedMatch != null) {
        return ParsedListLine(
            ordered = true,
            content = orderedMatch.groupValues[1].trim()
        )
    }

    return null
}

private fun stripInlineMarkdown(value: String): String {
    if (value.isBlank()) return ""

    var stripped = value
    stripped = linkPattern.replace(stripped) { matchResult ->
        matchResult.groupValues[1]
    }
    stripped = inlineCodePattern.replace(stripped, "$1")
    stripped = strongPattern.replace(stripped, "$1")
    stripped = emphasisPattern.replace(stripped, "$1")

    return stripped
}

private fun String.normalizeWhitespace(): String =
    replace(whitespacePattern, " ").trim()
