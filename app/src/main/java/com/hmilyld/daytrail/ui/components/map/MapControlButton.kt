package com.hmilyld.daytrail.ui.components.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * 地图上的圆形控件按钮。
 *
 * 视觉直径 40dp、触达区域 48dp：高 DPI 手机上 48dp 的实心白圆看着过大，
 * 但触达区域不能再缩，所以把外圈 4dp 做成透明命中区。
 *
 * 用白底 + 阴影而不是 `FilledTonalIconButton`：tonal 容器是
 * `surfaceContainerHigh`，压在浅色地图瓦片上和底图几乎同色，按钮会「消失」。
 */
@Composable
fun MapControlButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Box(
        modifier =
            modifier
                .size(TOUCH_SIZE)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) {
                    this.contentDescription = contentDescription
                },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp,
            modifier = Modifier.size(VISUAL_SIZE),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(ICON_SIZE),
                )
            }
        }
    }
}

/** 触达区域。低于 48dp 就不符合无障碍要求。 */
private val TOUCH_SIZE = 48.dp

/** 可见的白圆直径。 */
private val VISUAL_SIZE = 38.dp

/** 图标尺寸。 */
private val ICON_SIZE = 20.dp
