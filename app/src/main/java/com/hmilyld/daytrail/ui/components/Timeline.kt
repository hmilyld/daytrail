package com.hmilyld.daytrail.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.hmilyld.daytrail.ui.theme.TrailSpacing
import com.hmilyld.daytrail.ui.theme.TrailType

/** 时间轴节点语义。 */
enum class TimelineTone {
    /** 开始记录 / 出发。 */
    START,

    /** 正常移动或普通事件。 */
    MOVE,

    /** 停留：空心圆，和实心的移动节点区分。 */
    STAY,

    /** 数据缺失：琥珀色，必须显式画出来。 */
    GAP,

    /** 结束记录。 */
    END,
}

/**
 * 时间轴的一行。
 *
 * 今日页与单日详情共用同一个组件：两处画出来的时间轴以前各写一套，
 * 圆点大小、连接线颜色、时间宽度都不一致。
 *
 * 整行合并成一句话供读屏：
 * 「09:41，公司，停留 18 分钟」。逐个节点朗读会打断时间顺序。
 */
@Composable
fun TimelineRow(
    time: String,
    title: String,
    tone: TimelineTone,
    modifier: Modifier = Modifier,
    detail: String = "",
    showConnector: Boolean = true,
) {
    val color = toneColor(tone)
    val spoken = if (detail.isBlank()) "$time，$title" else "$time，$title，$detail"

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .clearAndSetSemantics { contentDescription = spoken }
                .padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = time,
            style = TrailType.numericSm,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier
                    .width(42.dp)
                    .padding(top = 2.dp),
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                Modifier
                    .width(18.dp)
                    .fillMaxHeight(),
        ) {
            Box(
                modifier =
                    Modifier
                        .padding(top = 3.dp)
                        .size(10.dp)
                        .then(
                            if (tone == TimelineTone.STAY) {
                                Modifier.border(2.dp, color, CircleShape)
                            } else {
                                Modifier.background(color, CircleShape)
                            },
                        ),
            )
            if (showConnector) {
                Box(
                    modifier =
                        Modifier
                            .padding(top = 2.dp)
                            .width(1.dp)
                            .weight(1f)
                            .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
        }

        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = TrailSpacing.sm, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color =
                    if (tone == TimelineTone.GAP) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
            )
            if (detail.isNotBlank()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun toneColor(tone: TimelineTone): Color =
    when (tone) {
        TimelineTone.START -> MaterialTheme.colorScheme.secondary
        TimelineTone.MOVE -> MaterialTheme.colorScheme.onSurfaceVariant
        TimelineTone.STAY -> MaterialTheme.colorScheme.primary
        TimelineTone.GAP -> MaterialTheme.colorScheme.tertiary
        TimelineTone.END -> MaterialTheme.colorScheme.onSurface
    }
