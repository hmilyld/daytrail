package com.hmilyld.daytrail.ui.update

/**
 * 更新说明的轻量 Markdown 解析。
 *
 * 为什么不用第三方库：更新说明只用到 CHANGELOG 里的极小子集
 * （`###` 小标题、`-` 列表、`**加粗**`、`` `代码` ``、`---` 分隔线），
 * 为它引入一个渲染库不划算，而且渲染库通常带着 WebView 或一大堆资源。
 *
 * 解析结果是纯数据（不带 Compose 类型），方便单测：
 * 文案与样式偏移分开给，UI 层再拼成 `AnnotatedString`。
 */
enum class NoteBlockKind {
    HEADING,
    BULLET,
    PARAGRAPH,
}

/** 行内样式。只保留更新说明里实际会出现的两种。 */
enum class NoteSpanKind {
    BOLD,
    CODE,
}

/** 一段行内样式的作用范围，下标针对 [NoteBlock.text]。 */
data class NoteSpan(
    val start: Int,
    val end: Int,
    val kind: NoteSpanKind,
)

/** 一块内容。[text] 里的 markdown 标记已经被去掉。 */
data class NoteBlock(
    val kind: NoteBlockKind,
    val text: String,
    val spans: List<NoteSpan> = emptyList(),
)

object UpdateNotes {
    private val HEADING = Regex("^#{1,6}\\s+")
    private val BULLET = Regex("^[-*+]\\s+")
    private val ORDERED = Regex("^\\d+[.)]\\s+")
    private val RULE = Regex("^(?:-{3,}|\\*{3,}|_{3,})$")

    /**
     * 行内标记：`**加粗**`、`` `代码` ``、`[文字](链接)`。
     *
     * 链接只保留文字：更新说明里不需要点得动，留着括号和 URL 反而难看。
     * 未闭合的标记按普通字符处理（正则匹配不到就不动它）。
     */
    private val INLINE = Regex("\\*\\*(.+?)\\*\\*|`([^`]+)`|\\[([^\\]]+)]\\([^)]*\\)")

    /** 把整段更新说明切成块。空输入返回空列表。 */
    fun parse(markdown: String): List<NoteBlock> {
        if (markdown.isBlank()) return emptyList()

        val blocks = mutableListOf<NoteBlock>()
        val paragraph = StringBuilder()
        var bullet: StringBuilder? = null

        fun flushParagraph() {
            if (paragraph.isNotEmpty()) {
                blocks += inlineBlock(NoteBlockKind.PARAGRAPH, paragraph.toString())
                paragraph.clear()
            }
        }

        fun flushBullet() {
            bullet?.let { blocks += inlineBlock(NoteBlockKind.BULLET, it.toString()) }
            bullet = null
        }

        markdown
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .lines()
            .forEach { raw ->
                // 行尾空格不参与判断；续行的缩进要保留下来单独判断
                var content = raw.trimEnd()
                var indented = content.startsWith(" ") || content.startsWith('\t')
                content = content.trim()

                if (content.startsWith(">")) {
                    // 引用的内容当作普通段落，去掉引用符号
                    content = content.trimStart('>').trim()
                    indented = false
                }

                when {
                    content.isEmpty() || RULE.matches(content) -> {
                        // 分隔线只当块之间的间隔，不单独占一行
                        flushBullet()
                        flushParagraph()
                    }

                    HEADING.containsMatchIn(content) -> {
                        flushBullet()
                        flushParagraph()
                        blocks +=
                            inlineBlock(
                                NoteBlockKind.HEADING,
                                content.replaceFirst(HEADING, "").trim(),
                            )
                    }

                    BULLET.containsMatchIn(content) -> {
                        flushBullet()
                        flushParagraph()
                        bullet = StringBuilder(content.replaceFirst(BULLET, "").trim())
                    }

                    ORDERED.containsMatchIn(content) -> {
                        flushBullet()
                        flushParagraph()
                        bullet = StringBuilder(content.replaceFirst(ORDERED, "").trim())
                    }

                    indented && bullet != null -> bullet?.append(' ')?.append(content)

                    indented && paragraph.isNotEmpty() ->
                        paragraph.append(' ').append(content)

                    else -> {
                        // 普通行：先收掉上一条列表项，再并进当前段落
                        flushBullet()
                        if (paragraph.isNotEmpty()) {
                            paragraph.append(' ').append(content)
                        } else {
                            paragraph.append(content)
                        }
                    }
                }
            }

        flushBullet()
        flushParagraph()
        return blocks
    }

    /**
     * 解析一行里的行内标记，返回去掉标记后的文字与样式偏移。
     *
     * 只处理一层嵌套：更新说明里不会出现 `**加粗里的 `代码`**` 这种写法。
     */
    fun inline(raw: String): Pair<String, List<NoteSpan>> {
        val text = StringBuilder()
        val spans = mutableListOf<NoteSpan>()
        var consumedUntil = 0

        INLINE.findAll(raw).forEach { match ->
            if (match.range.first > consumedUntil) {
                text.append(raw, consumedUntil, match.range.first)
            }
            val start = text.length
            val group = match.groups[1] ?: match.groups[2] ?: match.groups[3]
            val value = group?.value.orEmpty()
            text.append(value)
            when {
                match.groups[1] != null -> spans += NoteSpan(start, text.length, NoteSpanKind.BOLD)
                match.groups[2] != null -> spans += NoteSpan(start, text.length, NoteSpanKind.CODE)
                // 链接：只保留文字，不加样式
            }
            consumedUntil = match.range.last + 1
        }

        if (consumedUntil < raw.length) {
            text.append(raw, consumedUntil, raw.length)
        }
        return text.toString() to spans
    }

    private fun inlineBlock(
        kind: NoteBlockKind,
        raw: String,
    ): NoteBlock {
        val (text, spans) = inline(raw)
        return NoteBlock(kind = kind, text = text, spans = spans)
    }
}
