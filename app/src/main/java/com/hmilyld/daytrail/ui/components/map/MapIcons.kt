package com.hmilyld.daytrail.ui.components.map

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import com.amap.api.maps.model.BitmapDescriptor
import com.amap.api.maps.model.BitmapDescriptorFactory

/**
 * 地图图标。
 *
 * 为什么不用 `BitmapDescriptorFactory.defaultMarker`：
 * 那是水滴形图钉，起点、终点、停留全用它，一天下来满屏图钉，
 * 反而看不出轨迹走向。圆点更接近地图惯例，也不会盖住线。
 *
 * 图标按像素绘制，**不会自动按屏幕密度缩放**，所以调用方要用 dp 换算成 px。
 */
object MapIcons {
    /** 白色描边宽度（像素）。深色地图上保证圆点不糊在底图里。 */
    private const val RING_WIDTH_PX = 3

    private val cache = mutableMapOf<Long, BitmapDescriptor>()

    /** 一个带白色描边的实心圆点。 */
    fun dot(
        color: Int,
        diameterPx: Int,
    ): BitmapDescriptor {
        val size = diameterPx.coerceAtLeast(2)
        val key = (color.toLong() shl 16) or size.toLong()
        return cache.getOrPut(key) { createDot(color, size) }
    }

    private fun createDot(
        color: Int,
        diameterPx: Int,
    ): BitmapDescriptor {
        val size = diameterPx + RING_WIDTH_PX * 2
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        val center = size / 2f
        val radius = diameterPx / 2f

        canvas.drawCircle(center, center, radius + RING_WIDTH_PX, ringPaint())
        canvas.drawCircle(center, center, radius, fillPaint(color))

        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    private fun ringPaint(): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            style = Paint.Style.FILL
        }

    private fun fillPaint(color: Int): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
}
