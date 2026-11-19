package com.hmilyld.daytrail.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hmilyld.daytrail.R
import com.hmilyld.daytrail.core.HeatLevel
import com.hmilyld.daytrail.ui.theme.TrailSpacing
import com.hmilyld.daytrail.ui.theme.TrailType

/**
 * 历史列表的一行。
 *
 * 左「日号 + 星期」、右「里程」，中间不塞图标：一屏能看到十几天，
 * 图标只会让每行都一样重、看不出哪天走得远。
 * 下方的里程条承担「哪天多、哪天少」的比较。
 */
@Composable
fun DayListRow(
    dayOfMonth: Int,
    weekday: String,
    distanceKm: Double,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val level = HeatLevel.of(distanceKm)
    val kmText = formatKm(distanceKm)
    val spoken =
        stringResource(
            R.string.day_row_spoken,
            weekday,
            dayOfMonth,
            kmText,
        )

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .semantics(mergeDescendants = true) { contentDescription = spoken }
                .heightIn(min = 64.dp)
                .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = dayOfMonth.toString(),
                style = TrailType.numericMd,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = weekday,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier
                        .padding(start = TrailSpacing.sm)
                        .weight(1f),
            )
            Text(
                text = kmText,
                style = TrailType.numericMd,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.unit_km),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier
                        .padding(start = 2.dp, bottom = 1.dp)
                        .align(Alignment.Bottom),
            )
        }
        HeatBar(level = level)
    }
}

/** 里程条。宽度按分档比例，避免所有日子看起来一样重。 */
@Composable
fun HeatBar(
    level: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = MaterialTheme.shapes.extraSmall,
                ),
    ) {
        val fraction = HeatLevel.fraction(level)
        if (fraction > 0f) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(3.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = MaterialTheme.shapes.extraSmall,
                        ),
            )
        }
    }
}

/** 里程格式化：10 公里以内保留一位小数，之上取整。 */
private fun formatKm(km: Double): String = if (km >= 10.0) "%.0f".format(km) else "%.1f".format(km)
