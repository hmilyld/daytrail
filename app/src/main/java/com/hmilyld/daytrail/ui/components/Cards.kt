package com.hmilyld.daytrail.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hmilyld.daytrail.ui.theme.TrailSpacing
import com.hmilyld.daytrail.ui.theme.TrailType

/**
 * 内容卡片。
 *
 * 这是全应用唯一的分组容器：标题 + 可选右侧动作 + 内容。
 * 不用阴影：`surfaceContainerLow` 与页面底色已经拉开一档，
 * 再叠阴影会让长列表看起来发灰。
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = TrailSpacing.lg, vertical = 14.dp)) {
            if (title != null || actionLabel != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title.orEmpty(),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    if (actionLabel != null && onAction != null) {
                        TextButton(onClick = onAction) {
                            Text(text = actionLabel, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(TrailSpacing.xs))
            }
            content()
        }
    }
}

/** 一条指标：数值 + 单位 + 标签。 */
data class Metric(
    val value: String,
    val label: String,
    val unit: String = "",
)

/**
 * 三宫格指标行。
 *
 * 三项等宽，中间一条 1px 分隔线而不是三个独立卡片：
 * 这三项是一组数据，拆成三块会让人以为彼此无关。
 */
@Composable
fun MetricRow(
    metrics: List<Metric>,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            metrics.forEachIndexed { index, metric ->
                if (index > 0) {
                    VerticalDivider(
                        modifier = Modifier.height(36.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = metric.value,
                            style = TrailType.numericMd,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (metric.unit.isNotEmpty()) {
                            Text(
                                text = metric.unit,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 3.dp),
                            )
                        }
                    }
                    Text(
                        text = metric.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

/** 提示条的语义。颜色只用来加强，文案本身必须说清发生了什么。 */
enum class CalloutTone { INFO, SUCCESS, WARNING, ERROR }

/**
 * 提示条。
 *
 * 取代散落在各家页面里的「琥珀色药丸 / 红色卡片」：
 * 数据缺失、休眠、权限异常都走这一个组件，颜色与留白才不会各写一套。
 */
@Composable
fun CalloutBanner(
    text: String,
    modifier: Modifier = Modifier,
    tone: CalloutTone = CalloutTone.INFO,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val container: Color
    val content: Color
    when (tone) {
        CalloutTone.INFO -> {
            container = MaterialTheme.colorScheme.surfaceContainerHigh
            content = MaterialTheme.colorScheme.onSurface
        }

        CalloutTone.SUCCESS -> {
            container = MaterialTheme.colorScheme.secondaryContainer
            content = MaterialTheme.colorScheme.onSecondaryContainer
        }

        CalloutTone.WARNING -> {
            container = MaterialTheme.colorScheme.tertiaryContainer
            content = MaterialTheme.colorScheme.onTertiaryContainer
        }

        CalloutTone.ERROR -> {
            container = MaterialTheme.colorScheme.errorContainer
            content = MaterialTheme.colorScheme.onErrorContainer
        }
    }

    Surface(
        color = container,
        contentColor = content,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, content.copy(alpha = 0.08f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TrailSpacing.sm),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) {
                    Text(text = actionLabel, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** 列表行右侧的 "›"，纯装饰。 */
@Composable
internal fun TrailingChevron(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.AutoMirrored.Outlined.ArrowForwardIos,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.size(14.dp),
    )
}
