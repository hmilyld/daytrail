package com.hmilyld.daytrail.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.hmilyld.daytrail.R
import com.hmilyld.daytrail.core.HeatLevel
import com.hmilyld.daytrail.ui.theme.TrailColors
import com.hmilyld.daytrail.ui.theme.TrailSpacing
import java.time.LocalDate

/**
 * 月度热力日历。
 *
 * 每格是一个色块，日号写在块里：一屏就能看出哪几天有记录、哪天走得远，
 * 不需要再对着列表逐行比较。
 *
 * 色块上的字色按**块本身的亮度**决定，而不是按主题：
 * 深色主题下 1 档是深藏青、4 档是亮蓝，浅色主题下刚好相反，
 * 任何「固定白字 / 固定黑字」都会在其中一种主题里掉到 4.5:1 以下。
 *
 * 整个网格合并成一句语义描述——纯展示，具体里程在上方合计与下方日列表里。
 */
@Composable
fun HeatCalendar(
    year: Int,
    month: Int,
    monthKm: List<Double>,
    modifier: Modifier = Modifier,
) {
    val leading = leadingBlanks(year, month)
    val cells = cellCount(year, month)
    val activeDays = monthKm.count { it > 0.0 }
    val description = stringResource(R.string.heat_calendar_summary, year, month, activeDays)

    Column(
        modifier =
            modifier
                .widthIn(max = CALENDAR_MAX_WIDTH)
                .clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(TrailSpacing.xs),
    ) {
        repeat(cells / 7) { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(TrailSpacing.xs),
            ) {
                repeat(7) { column ->
                    val index = row * 7 + column
                    val day = index - leading
                    if (day < 0 || day >= monthKm.size) {
                        // 不在本月范围：留一个等大的空格，保持列对齐
                        Spacer(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .height(CELL_HEIGHT),
                        )
                    } else {
                        DayCell(
                            day = day + 1,
                            km = monthKm[day],
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 色块。
 *
 * 12dp 圆角 + 日号居中；没有记录的那天用中性底色、字色也压暗一档，
 * 避免「零里程」和「1 档」在视觉上一样重。
 */
@Composable
private fun DayCell(
    day: Int,
    km: Double,
    modifier: Modifier = Modifier,
) {
    val level = HeatLevel.of(km)
    val background = heatColor(level)
    val content =
        when {
            level == HeatLevel.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
            // 浅底容器 + 它自己的前景色，这一对由主题保证对比度
            level == HeatLevel.LOW -> MaterialTheme.colorScheme.onPrimaryContainer
            // 品牌蓝上白字，两侧主题都是 4.5:1
            level == HeatLevel.MAX -> MaterialTheme.colorScheme.onPrimary
            background.luminance() > DARK_TEXT_LUMINANCE -> TrailColors.OnHeatDark
            else -> TrailColors.OnHeatLight
        }

    Box(
        modifier =
            modifier
                .height(CELL_HEIGHT)
                .background(color = background, shape = MaterialTheme.shapes.extraSmall),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = day.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = content,
        )
    }
}

/**
 * 热力色阶。
 *
 * 从 `primaryContainer` 走到 `primary`，中间两档用 `lerp` 混出**不透明**的中间色：
 * 用透明度叠加的话，色块的最终颜色取决于卡片底色，
 * 而字色又要按块亮度决定，两者会算不到一起。
 */
@Composable
private fun heatColor(level: Int): Color {
    val card = MaterialTheme.colorScheme.surfaceContainerLow
    val accent = MaterialTheme.colorScheme.primary
    return when (level) {
        HeatLevel.NONE -> MaterialTheme.colorScheme.surfaceContainerHigh
        HeatLevel.LOW -> MaterialTheme.colorScheme.primaryContainer
        HeatLevel.MEDIUM -> lerp(card, accent, 0.45f)
        HeatLevel.HIGH -> lerp(card, accent, 0.72f)
        else -> accent
    }
}

/** 图例：少 → 多。与 [heatColor] 的分档一一对应。 */
@Composable
fun HeatLegend(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TrailSpacing.xs),
    ) {
        Text(
            text = stringResource(R.string.history_legend_less),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        listOf(HeatLevel.NONE, HeatLevel.LOW, HeatLevel.MEDIUM, HeatLevel.HIGH, HeatLevel.MAX)
            .forEach { level ->
                Box(
                    modifier =
                        Modifier
                            .size(10.dp)
                            .background(
                                color = heatColor(level),
                                shape = MaterialTheme.shapes.extraSmall,
                            ),
                )
            }
        Text(
            text = stringResource(R.string.history_legend_more),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 当月 1 号之前需要留白的格子数（周一为一周起点）。 */
private fun leadingBlanks(
    year: Int,
    month: Int,
): Int = LocalDate.of(year, month, 1).dayOfWeek.value - 1

/** 当月需要显示的总格子数，向上取整到整周。 */
private fun cellCount(
    year: Int,
    month: Int,
): Int {
    val total = leadingBlanks(year, month) + LocalDate.of(year, month, 1).lengthOfMonth()
    return ((total + 6) / 7) * 7
}

/**
 * 日历网格的最大宽度。
 *
 * 600dp 以上的卡片会被拉得很宽，格子跟着变成方块墙。
 * 限制到 440dp 后每格约 60dp，和手机上的观感接近。
 */
private val CALENDAR_MAX_WIDTH = 440.dp

/**
 * 格子高度。
 *
 * 比宽度矮（手机上一格约 46×30dp）：方格太厚会像日期选择器，
 * 一屏三十个实心方块压过了「本月合计」这个主角。
 */
private val CELL_HEIGHT = 30.dp

/** 超过这个亮度就用深色字。0.179 是黑字与白字对比度相等的分界。 */
private const val DARK_TEXT_LUMINANCE = 0.18f
