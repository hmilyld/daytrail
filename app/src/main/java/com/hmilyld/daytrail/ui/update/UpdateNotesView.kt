package com.hmilyld.daytrail.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.hmilyld.daytrail.ui.theme.TrailSpacing

/**
 * 渲染更新说明。
 *
 * 之前这里直接把 `update.json` 的 `notes` 丢进 `Text`，用户看到的是
 * `### 新增`、`- 条目`、`**加粗**` 这些 markdown 原文。
 *
 * 解析在 [UpdateNotes]（纯数据 + 单测），这里只负责排版：
 * 小标题略大、列表带项目符号、行内加粗与代码用对应字重/等宽字体。
 */
@Composable
fun UpdateNotesView(
    notes: String,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(notes) { UpdateNotes.parse(notes) }
    if (blocks.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(TrailSpacing.xs),
    ) {
        blocks.forEach { block ->
            when (block.kind) {
                NoteBlockKind.HEADING ->
                    Text(
                        text = block.toAnnotated(),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        // 标题和上面的段落拉开一点，否则整段像一句话
                        modifier = Modifier.padding(top = TrailSpacing.xs),
                    )

                NoteBlockKind.BULLET ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = TrailSpacing.sm),
                        )
                        Text(
                            text = block.toAnnotated(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                NoteBlockKind.PARAGRAPH ->
                    Text(
                        text = block.toAnnotated(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
            }
        }
    }
}

/** 把纯文本 + 样式偏移拼成 Compose 的富文本。 */
private fun NoteBlock.toAnnotated(): AnnotatedString =
    buildAnnotatedString {
        append(text)
        spans.forEach { span ->
            addStyle(
                when (span.kind) {
                    NoteSpanKind.BOLD -> SpanStyle(fontWeight = FontWeight.SemiBold)
                    NoteSpanKind.CODE -> SpanStyle(fontFamily = FontFamily.Monospace)
                },
                span.start,
                span.end,
            )
        }
    }
