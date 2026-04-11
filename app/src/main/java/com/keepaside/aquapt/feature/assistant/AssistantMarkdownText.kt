package com.keepaside.aquapt.feature.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

private const val markdownUrlAnnotationTag = "assistant_markdown_url"

private val inlineTokenPattern = Regex(
    """\[([^\]]+)]\((https?://[^)\s]+)\)|`([^`]+)`|\*\*([^*]+)\*\*|(?<!\*)\*([^*\n]+)\*(?!\*)"""
)

@Composable
internal fun AssistantMarkdownText(
    content: String,
    modifier: Modifier = Modifier
) {
    val blocks = remember(content) { parseAssistantMarkdownBlocks(content) }

    if (blocks.isEmpty()) {
        Text(
            text = content,
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier
        )
        return
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is AssistantMarkdownBlock.Heading -> {
                    MarkdownInlineText(
                        text = block.content,
                        style = when {
                            block.level <= 1 -> MaterialTheme.typography.headlineSmall
                            block.level == 2 -> MaterialTheme.typography.titleLarge
                            block.level == 3 -> MaterialTheme.typography.titleMedium
                            else -> MaterialTheme.typography.titleSmall
                        }
                    )
                }

                is AssistantMarkdownBlock.Paragraph -> {
                    MarkdownInlineText(
                        text = block.content,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                is AssistantMarkdownBlock.Quote -> {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .heightIn(min = 28.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                        shape = MaterialTheme.shapes.extraSmall
                                    )
                            )

                            MarkdownInlineText(
                                text = block.content,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                is AssistantMarkdownBlock.CodeBlock -> {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            block.language
                                ?.takeIf { it.isNotBlank() }
                                ?.let { language ->
                                    Text(
                                        text = language,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                            Text(
                                text = block.code.ifBlank { " " },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                            )
                        }
                    }
                }

                is AssistantMarkdownBlock.ListBlock -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        block.items.forEachIndexed { index, item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = listPrefix(
                                        ordered = block.ordered,
                                        index = index,
                                        taskState = item.taskState
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(top = 1.dp)
                                )

                                MarkdownInlineText(
                                    text = item.content,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
@Composable
private fun MarkdownInlineText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier
) {
    if (text.isBlank()) {
        Text(
            text = text,
            style = style,
            modifier = modifier
        )
        return
    }

    val uriHandler = LocalUriHandler.current
    val primary = MaterialTheme.colorScheme.primary
    val textColor = LocalContentColor.current
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    val annotated = remember(text, primary, textColor, surfaceVariant) {
        buildInlineMarkdownAnnotatedString(
            text = text,
            linkColor = primary,
            textColor = textColor,
            inlineCodeBackground = surfaceVariant
        )
    }

    ClickableText(
        text = annotated,
        style = style,
        modifier = modifier,
        onClick = { offset ->
            annotated
                .getStringAnnotations(
                    tag = markdownUrlAnnotationTag,
                    start = offset,
                    end = offset
                )
                .firstOrNull()
                ?.let { annotation ->
                    runCatching { uriHandler.openUri(annotation.item) }
                }
        }
    )
}

private fun buildInlineMarkdownAnnotatedString(
    text: String,
    linkColor: androidx.compose.ui.graphics.Color,
    textColor: androidx.compose.ui.graphics.Color,
    inlineCodeBackground: androidx.compose.ui.graphics.Color
): AnnotatedString = buildAnnotatedString {
    var cursor = 0

    inlineTokenPattern.findAll(text).forEach { match ->
        if (match.range.first > cursor) {
            append(text.substring(cursor, match.range.first))
        }

        val linkLabel = match.groups[1]?.value
        val linkUrl = match.groups[2]?.value
        val code = match.groups[3]?.value
        val bold = match.groups[4]?.value
        val italic = match.groups[5]?.value

        when {
            linkLabel != null && linkUrl != null -> {
                pushStringAnnotation(
                    tag = markdownUrlAnnotationTag,
                    annotation = linkUrl
                )
                withStyle(
                    SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline,
                        fontWeight = FontWeight.Medium
                    )
                ) {
                    append(linkLabel)
                }
                pop()
            }

            code != null -> {
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = inlineCodeBackground,
                        color = textColor
                    )
                ) {
                    append(code)
                }
            }

            bold != null -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = textColor)) {
                    append(bold)
                }
            }

            italic != null -> {
                withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = textColor)) {
                    append(italic)
                }
            }

            else -> {
                append(match.value)
            }
        }

        cursor = match.range.last + 1
    }

    if (cursor < text.length) {
        append(text.substring(cursor))
    }
}

private fun listPrefix(
    ordered: Boolean,
    index: Int,
    taskState: Boolean?
): String = when {
    taskState == true -> "☑"
    taskState == false -> "☐"
    ordered -> "${index + 1}."
    else -> "•"
}
