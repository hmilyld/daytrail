package com.hmilyld.daytrail.ui.components.map

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hmilyld.daytrail.R
import com.hmilyld.daytrail.ui.theme.TrailSpacing

/**
 * 高德地图原生库是否在当前 ABI 上可用。
 *
 * 高德只提供 arm64-v8a 与 armeabi-v7a 的 libAMapSDK_MAP_*.so，
 * 没有 x86 / x86_64。因此在 x86_64 模拟器上地图无法渲染。
 * 提前探测可以让界面显示说明，而不是一片黑屏。
 */
@Composable
fun rememberAmapAvailable(): Boolean {
    val context = LocalContext.current
    return remember(context) { isAmapNativeLibraryAvailable(context) }
}

private fun isAmapNativeLibraryAvailable(context: Context): Boolean {
    val abis = android.os.Build.SUPPORTED_ABIS
    val supported = abis.any { it == "arm64-v8a" || it == "armeabi-v7a" }
    if (!supported) return false

    return try {
        // 直接尝试加载：库存在但架构不匹配时也会在这里失败
        System.loadLibrary("AMapSDK_MAP_v10_0_600")
        true
    } catch (error: UnsatisfiedLinkError) {
        false
    } catch (error: SecurityException) {
        false
    }
}

/** 地图不可用时的占位说明。 */
@Composable
fun MapUnavailablePlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(TrailSpacing.xxl),
        ) {
            Icon(
                imageVector = Icons.Outlined.Map,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp),
            )
            Text(
                text = stringResource(R.string.map_unavailable_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.map_unavailable_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
