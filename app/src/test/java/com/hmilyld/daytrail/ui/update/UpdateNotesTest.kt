package com.hmilyld.daytrail.ui.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 更新说明的 Markdown 解析。
 *
 * 这里断言的是「用户看到什么」：标记必须被吃掉、列表项要合并续行、
 * 加粗/代码的偏移要落在正确的文字上。渲染是 Compose 的事，不在这里测。
 */
class UpdateNotesTest {
    private fun blocks(markdown: String) = UpdateNotes.parse(markdown)

    @Test
    fun `heading is its own block with the marker stripped`() {
        val result = blocks("### 新增\n")

        assertEquals(1, result.size)
        assertEquals(NoteBlockKind.HEADING, result.first().kind)
        assertEquals("新增", result.first().text)
    }

    @Test
    fun `bullets become separate blocks`() {
        val result = blocks("- 第一条\n- 第二条\n")

        assertEquals(2, result.size)
        assertEquals(listOf("第一条", "第二条"), result.map { it.text })
        assertTrue(result.all { it.kind == NoteBlockKind.BULLET })
    }

    @Test
    fun `indented continuation lines join the previous bullet`() {
        // CHANGELOG 里的长条目就是这样折行的
        val result = blocks("- 手动微调轨迹：可以拖动顶点。\n  结果存为派生数据。\n  随时可还原。\n")

        assertEquals(1, result.size)
        assertEquals("手动微调轨迹：可以拖动顶点。 结果存为派生数据。 随时可还原。", result.first().text)
    }

    @Test
    fun `bold markers are removed and become a span`() {
        val block = blocks("- 结果**只影响显示**——里程不变").first()

        assertEquals("结果只影响显示——里程不变", block.text)
        assertEquals(1, block.spans.size)
        val span = block.spans.first()
        assertEquals(NoteSpanKind.BOLD, span.kind)
        assertEquals("只影响显示", block.text.substring(span.start, span.end))
    }

    @Test
    fun `inline code markers are removed and become a span`() {
        val block = blocks("- 写入 `track_edit` 表").first()

        assertEquals("写入 track_edit 表", block.text)
        val span = block.spans.first()
        assertEquals(NoteSpanKind.CODE, span.kind)
        assertEquals("track_edit", block.text.substring(span.start, span.end))
    }

    @Test
    fun `horizontal rules are treated as block separators`() {
        // awk 抽出来的小节末尾会带一条 ---
        val result = blocks("### 新增\n\n- 一条\n\n---\n")

        assertEquals(2, result.size)
        assertEquals(listOf(NoteBlockKind.HEADING, NoteBlockKind.BULLET), result.map { it.kind })
    }

    @Test
    fun `blockquote marker is stripped into a paragraph`() {
        // 未签名包由 CI 追加的一段引用
        val result = blocks("> 本版 APK 由 CI 构建，未签名。")

        assertEquals(1, result.size)
        assertEquals(NoteBlockKind.PARAGRAPH, result.first().kind)
        assertEquals("本版 APK 由 CI 构建，未签名。", result.first().text)
    }

    @Test
    fun `link keeps only the label`() {
        val block = blocks("详见 [更新说明](https://example.com/notes)").first()

        assertEquals("详见 更新说明", block.text)
        assertTrue(block.spans.isEmpty())
    }

    @Test
    fun `consecutive plain lines form one paragraph`() {
        val result = blocks("第一行\n第二行\n")

        assertEquals(1, result.size)
        assertEquals("第一行 第二行", result.first().text)
    }

    @Test
    fun `ordered list items are rendered as bullets`() {
        val result = blocks("1. 第一步\n2. 第二步\n")

        assertEquals(listOf("第一步", "第二步"), result.map { it.text })
        assertTrue(result.all { it.kind == NoteBlockKind.BULLET })
    }

    @Test
    fun `windows line endings parse the same way`() {
        val result = blocks("### 新增\r\n\r\n- 一条\r\n")

        assertEquals(2, result.size)
        assertEquals("一条", result.last().text)
    }

    @Test
    fun `blank input produces nothing`() {
        assertEquals(emptyList<NoteBlock>(), blocks(""))
        assertEquals(emptyList<NoteBlock>(), blocks("   \n\n\t\n"))
    }

    @Test
    fun `real 0_5_0 notes have no markdown markers left`() {
        // update.json 里的原文（末尾那条 --- 是 awk 抽小节带出来的）
        val notes =
            "### 新增\n\n" +
                "- 手动微调轨迹：单日详情里可以拖动顶点、点线段加一个点、删点，" +
                "把没贴合道路的折线拖到正确位置。\n" +
                "  结果存为派生数据（`track_edit`，数据库 v5），**只影响显示**——里程、时长、停留、\n" +
                "  地点与导出仍按原始采样点计算，随时可还原\n\n---"

        val result = blocks(notes)

        assertEquals(2, result.size)
        assertEquals("新增", result.first().text)
        val body = result.last().text
        assertTrue(body, !body.contains("**"))
        assertTrue(body, !body.contains("`"))
        assertTrue(body, body.endsWith("随时可还原"))
        // 两个行内样式：track_edit 与 只影响显示
        assertEquals(
            listOf(NoteSpanKind.CODE, NoteSpanKind.BOLD),
            result.last().spans.map { it.kind },
        )
    }
}
