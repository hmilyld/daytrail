package com.hmilyld.daytrail.domain

import com.hmilyld.daytrail.core.GeoPoint
import com.hmilyld.daytrail.core.distanceMeters

/**
 * 一天的采样点怎么变成地图上的线。
 *
 * 入库和画线用两套精度门槛：
 * - **入库宁多勿少**：只给「大致位置」的用户也必须有点，否则整天一片空白
 * - **画线宁缺勿滥**：2 公里误差的点画上去就是一条横穿城市的乱线
 *
 * 如果一整天都没有足够精确的点，退回用粗点画——至少能看出「今天去了哪几个区」，
 * 比什么都不显示有用。
 *
 * 顺序很重要：**精度筛 → 毛刺修复 → 停留吸附 → 切段**。
 * 停留吸附放在切段之前，是因为它会把一整段停留压成一个点；
 * 先切段的话，吸附完还会剩下几段只有 1 个点的碎片。
 */
object TrackDisplay {
    /** 参与画线的精度上限（米）。超过这个值的点只入库、不上图。 */
    const val MAX_LINE_ACCURACY_METERS = 120f

    /** 地图上画停留标记的最短时长。 */
    const val MIN_STAY_MARKER_MS = 15 * 60_000L

    /**
     * 生成可直接交给地图的分段折线。
     *
     * @param stays 已识别的停留段。落在停留时间窗内的点会被**整体替换**成该段锚点——
     *   停留段内的点本来就被 [StayDetector] 判定为「同一个地方」，
     *   再画成一团来回的线是自相矛盾的。实测一天里两处停留能画出 728 m 的无效折线。
     *   不做「只吸附锚点附近 N 米」的部分吸附：实测那样会把几个离群点连成
     *   跨越上百米的三角形，比原样更难看。
     * @param edit 用户手动微调的结果。**优先级最高**：存在时直接用它，
     *   上面那些管线一步都不走（用户画的线不该再被抽稀或吸附改动），
     *   只有编辑之后新采到的点会接着画。
     */
    fun segments(
        points: List<TrackPointSample>,
        stays: List<StaySegment> = emptyList(),
        override: RouteOverride? = null,
        edit: TrackEdit? = null,
    ): List<List<GeoPoint>> {
        if (edit != null && edit.segments.isNotEmpty()) return withEditTail(edit, points, stays)

        if (points.isEmpty()) return emptyList()

        // 没有纠偏结果：完全按原始点画
        if (override == null || override.segments.isEmpty()) return draw(points, stays)

        // 有纠偏结果：只替换驾车时间窗内的部分。
        // 窗口外（在家、在公司、走下楼）仍按原始点画，不丢数据。
        val before = points.filter { it.timestampMs < override.startMs }
        val after = points.filter { it.timestampMs > override.endMs }

        return buildList {
            addAll(draw(before, stays))
            addAll(override.segments)
            addAll(draw(after, stays))
        }.filter { it.size >= 2 }
    }

    /**
     * 编辑线 + 编辑之后新采到的点。
     *
     * 录制中允许进编辑，所以「保存之后又来了新点」是常态：那一段必须是增量，
     * 不能烘进编辑结果，否则每次保存都要重画一遍。
     *
     * 接不接上只看 [TrackSegmenter] 的断点判据（时间 + 位移**同时**超阈值）：
     * 只差几分钟就直接连上，隔了几小时才留白——和自动画线的规则保持一致。
     */
    private fun withEditTail(
        edit: TrackEdit,
        points: List<TrackPointSample>,
        stays: List<StaySegment>,
    ): List<List<GeoPoint>> {
        val tail = points.filter { it.timestampMs > edit.cutoffMs }
        if (tail.isEmpty()) return edit.segments

        val tailSegments = draw(tail, stays)
        if (tailSegments.isEmpty()) return edit.segments

        val end = edit.segments.lastOrNull()?.lastOrNull()
        val first = tail.first()
        // 与自动画线同一套判据：时间和位移**同时**超阈值才算断开
        val jumped =
            end != null &&
                distanceMeters(end, first.point) > TrackSegmenter.GAP_MIN_DISPLACEMENT_METERS
        val waited = first.timestampMs - edit.cutoffMs > TrackSegmenter.GAP_MIN_MS

        // 连得上就并成一段：两段折线在同一个点上首尾相接会露出一条缝
        // （每段的端点都是平头），看起来像轨迹断了。
        if (end != null && !(jumped && waited)) {
            val merged = edit.segments.last() + tailSegments.first()
            return buildList {
                addAll(edit.segments.dropLast(1))
                add(merged)
                addAll(tailSegments.drop(1))
            }
        }

        return edit.segments + tailSegments
    }

    /** 精度筛 → 毛刺修复 → 停留吸附 → 切段。 */
    private fun draw(
        points: List<TrackPointSample>,
        stays: List<StaySegment>,
    ): List<List<GeoPoint>> {
        if (points.isEmpty()) return emptyList()

        val precise = points.filter { it.accuracyMeters <= MAX_LINE_ACCURACY_METERS }
        val usable = if (precise.size >= 2) precise else points

        val smoothed = TrackSmoother.smooth(usable)
        val placed =
            usable.mapIndexed { index, sample ->
                sample.copy(point = smoothed[index])
            }

        val anchored = snapToStays(placed, stays)
        return TrackSegmenter.segment(collapseRepeats(anchored))
    }

    /**
     * 停留时间窗内的点全部替换成锚点。
     *
     * 按时间戳匹配而不是按索引：`points` 可能已经被精度筛过滤过，
     * 索引和停留段对不上。
     */
    private fun snapToStays(
        samples: List<TrackPointSample>,
        stays: List<StaySegment>,
    ): List<TrackPointSample> {
        if (stays.isEmpty()) return samples

        return samples.map { sample ->
            val stay = stays.firstOrNull { sample.timestampMs in it.startMs..it.endMs }
            if (stay == null) sample else sample.copy(point = stay.point)
        }
    }

    /**
     * 合并坐标完全相同的相邻点，保留**后一个**（保住停留段的结束时间）。
     *
     * 吸附后一段停留会变成几十个同坐标的顶点，直接交给地图会白建几十个
     * 采样点标记，切段也会多出一堆零长度片段。
     */
    private fun collapseRepeats(samples: List<TrackPointSample>): List<TrackPointSample> {
        if (samples.size < 2) return samples

        val result = ArrayList<TrackPointSample>(samples.size)
        for (sample in samples) {
            val last = result.lastOrNull()
            if (last != null && last.point == sample.point) {
                result[result.lastIndex] = sample
            } else {
                result += sample
            }
        }
        return result
    }
}
