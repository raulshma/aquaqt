package com.keepaside.aquapt.feature.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantMarkdownParserTest {

    @Test
    fun `parse assistant markdown blocks supports headings lists quote and code`() {
        val markdown = """
            # Weekly plan

            Nitrate is **high** today.

            - [x] Water change complete
            - [ ] Dose conditioner

            1. Review filter floss
            2. Clean skimmer cup

            > Watch pH overnight.

            ```json
            {"status":"ok"}
            ```
        """.trimIndent()

        val blocks = parseAssistantMarkdownBlocks(markdown)

        assertEquals(6, blocks.size)
        assertTrue(blocks[0] is AssistantMarkdownBlock.Heading)
        assertTrue(blocks[1] is AssistantMarkdownBlock.Paragraph)
        assertTrue(blocks[2] is AssistantMarkdownBlock.ListBlock)
        assertTrue(blocks[3] is AssistantMarkdownBlock.ListBlock)
        assertTrue(blocks[4] is AssistantMarkdownBlock.Quote)
        assertTrue(blocks[5] is AssistantMarkdownBlock.CodeBlock)

        val taskList = blocks[2] as AssistantMarkdownBlock.ListBlock
        assertFalse(taskList.ordered)
        assertEquals(2, taskList.items.size)
        assertEquals(true, taskList.items[0].taskState)
        assertEquals(false, taskList.items[1].taskState)

        val orderedList = blocks[3] as AssistantMarkdownBlock.ListBlock
        assertTrue(orderedList.ordered)
        assertEquals("Review filter floss", orderedList.items[0].content)

        val codeBlock = blocks[5] as AssistantMarkdownBlock.CodeBlock
        assertEquals("json", codeBlock.language)
        assertTrue(codeBlock.code.contains("status"))
    }

    @Test
    fun `preview text strips inline markdown and links`() {
        val markdown = """
            ## Tank summary
            Track **nitrate** and *pH* in the [dashboard](https://example.com).
            `dose` note for tonight.
        """.trimIndent()

        val preview = assistantMarkdownPreviewText(markdown, maxLength = 200)

        assertTrue(preview.contains("Tank summary"))
        assertTrue(preview.contains("nitrate"))
        assertTrue(preview.contains("dashboard"))
        assertTrue(preview.contains("dose"))
        assertFalse(preview.contains("**"))
        assertFalse(preview.contains("https://"))
        assertFalse(preview.contains("`"))
    }

    @Test
    fun `preview text truncates and appends ellipsis`() {
        val markdown = """
            This is a very long markdown message with multiple details about maintenance,
            feeding plans, and reminder windows across all aquariums.
        """.trimIndent()

        val preview = assistantMarkdownPreviewText(markdown, maxLength = 36)

        assertTrue(preview.length <= 37)
        assertTrue(preview.endsWith("…"))
    }
}
