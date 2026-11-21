package com.hmilyld.daytrail.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hmilyld.daytrail.ui.theme.TrailSpacing

/**
 * 空状态。图标是装饰性的（contentDescription = null），
 * 说明文字承担无障碍语义，因此整体不做语义合并。
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = TrailSpacing.xxl, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(56.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = TrailSpacing.lg),
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = TrailSpacing.sm),
        )
    }
}

/** 仅用于无障碍：把整块内容读成一句话，替代逐个节点朗读。 */
@Composable
fun MergeForAccessibility(
    description: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            modifier.clearAndSetSemantics {
                contentDescription = description
            },
    ) {
        content()
    }
}

/**
 * 地图的文字版概要。
 *
 * 地图折线对 TalkBack 完全不可用——读屏只会念一堆坐标。
 * 这里给出一句等价描述，并用 `clearAndSetSemantics` 合并成单个节点，
 * 避免读屏逐个念地图上的标记。
 */
@Composable
fun MapAccessibilityOverlay(
    text: String,
    modifier: Modifier = Modifier,
) {
    if (text.isBlank()) return

    Surface(
        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.88f),
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shape = MaterialTheme.shapes.small,
        modifier =
            modifier.clearAndSetSemantics {
                contentDescription = text
            },
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

/**
 * 全屏（immersive）时给地图浮层加安全区避让。
 *
 * 非全屏时外层 Scaffold 已经把 `safeDrawing` 消费掉了，这里再加会双重缩进；
 * 全屏时外层为了让地图真正铺到状态栏与手势条下面而不再消费 inset，
 * 浮层（状态胶囊、地图按钮、无障碍文字）就必须自己避让，否则会被系统栏压住。
 */
@Composable
fun Modifier.immersiveSafePadding(immersive: Boolean): Modifier =
    if (immersive) windowInsetsPadding(WindowInsets.safeDrawing) else this
