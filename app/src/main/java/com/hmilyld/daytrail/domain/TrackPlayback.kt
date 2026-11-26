package com.hmilyld.daytrail.domain

import com.hmilyld.daytrail.core.GeoPoint
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/** 回放用的一个原始采样点。必须由调用方按时间升序、精度过滤后传入。 */
data class PlaybackSample(
    val tsMs: Long,
    val point: GeoPoint,
)

/** 回放用的数据缺失区间。[startMs] 与 [endMs] 是断点前后的两个采样点时间。 */
data class PlaybackGap(
    val startMs: Long,
    val endMs: Long,
)

/** 一段连续数据。端点就是段内首末采样点的时间。 */
data class PlaybackSpan(
    val startMs: Long,
    val endMs: Long,
    val samples: List<PlaybackSample>,
)

/** 某一时刻回放指针的位置。 */
data class PlaybackFrame(
    val point: GeoPoint,
    val tsMs: Long,
    val spanIndex: Int,
    /** 正停在「数据缺失」的停顿上。界面据此显示提示，不把它当成真实轨迹。 */
    val inGap: Boolean,
    /** 正处在停留时间窗内：指针固定在锚点上不动，界面要说明这不是卡住。 */
    val inStay: Boolean = false,
)

/** 时间轴上的一个片段。时长已经是**墙上时间**，倍速已经折算进去。 */
sealed interface PlaybackPart {
    val startMs: Long
    val durationMs: Long
}

internal data class PlaybackSpanPart(
    override val startMs: Long,
    override val durationMs: Long,
    val span: PlaybackSpan,
    val spanIndex: Int,
    val stays: List<StaySegment> = emptyList(),
) : PlaybackPart

internal data class PlaybackGapPart(
    override val startMs: Long,
    override val durationMs: Long,
    val anchor: GeoPoint,
    val tsMs: Long,
    val spanIndex: Int,
) : PlaybackPart

/**
 * 回放时间轴。总时长 = 各段真实时长 / 倍速 + 段间固定停顿。
 *
 * 对外只暴露 [durationMs] 与 [frameAt]；引擎按墙上时间推进 elapsed 即可，
 * 倍速已经在构建时折算好，调用方不需要再乘一次。
 */
class PlaybackTimeline internal constructor(
    val durationMs: Long,
    private val parts: List<PlaybackPart>,
) {
    /** elapsed 超出范围时停在最后一帧，负时间按 0 处理。 */
    fun frameAt(elapsedMs: Long): PlaybackFrame? {
        val last = parts.lastOrNull() ?: return null
        val elapsed = elapsedMs.coerceAtLeast(0L)
        if (elapsed >= durationMs) return frameAtEnd(last)

        val part = parts.first { elapsed < it.startMs + it.durationMs }
        return when (part) {
            is PlaybackGapPart ->
                PlaybackFrame(part.anchor, part.tsMs, part.spanIndex, inGap = true)
            is PlaybackSpanPart -> frameInSpan(part, elapsed)
        }
    }

    private fun frameAtEnd(part: PlaybackPart): PlaybackFrame =
        when (part) {
            is PlaybackSpanPart -> {
                // 播完必须停在最后一个真实采样点，不能停在它前一毫秒的插值位置。
                val last = part.span.samples.last()
                PlaybackFrame(last.point, last.tsMs, part.spanIndex, inGap = false)
            }
            is PlaybackGapPart ->
                PlaybackFrame(part.anchor, part.tsMs, part.spanIndex, inGap = true)
        }

    /** 段内按真实时间线性插值；坐标在相邻两个采样点之间按时间比例取点。 */
    private fun frameInSpan(
        part: PlaybackSpanPart,
        elapsed: Long,
    ): PlaybackFrame {
        val samples = part.span.samples
        if (samples.size == 1) {
            val only = samples[0]
            return PlaybackFrame(
                only.point,
                only.tsMs,
                part.spanIndex,
                inGap = false,
                inStay = part.stays.any { only.tsMs in it.startMs..it.endMs },
            )
        }

        val fraction =
            ((elapsed - part.startMs).toDouble() / part.durationMs).coerceIn(0.0, 1.0)
        val realMs = (part.span.endMs - part.span.startMs).coerceAtLeast(0L)
        val targetTs = part.span.startMs + (fraction * realMs).roundToLong()
        val inStay = part.stays.any { targetTs in it.startMs..it.endMs }

        var index = 0
        while (index < samples.lastIndex && samples[index + 1].tsMs <= targetTs) index++
        val from = samples[index]
        val to = samples[min(index + 1, samples.lastIndex)]
        val between = to.tsMs - from.tsMs
        val ratio =
            if (between <= 0L) {
                0.0
            } else {
                ((targetTs - from.tsMs).toDouble() / between).coerceIn(0.0, 1.0)
            }
        val point =
            GeoPoint(
                latitude = from.point.latitude + (to.point.latitude - from.point.latitude) * ratio,
                longitude =
                    from.point.longitude + (to.point.longitude - from.point.longitude) * ratio,
            )
        return PlaybackFrame(
            point,
            targetTs,
            part.spanIndex,
            inGap = false,
            inStay = inStay,
        )
    }
}

/**
 * 单日轨迹回放的纯逻辑。
 *
 * **只读原始采样点**：手动微调与纠偏只影响地图上画出来的线，不参与回放，
 * 也不改任何统计。断点两侧不按真实时间匀速穿越，而是固定停顿 [GAP_PAUSE_MS]，
 * 否则 3 小时的缺失会被倍速压成一瞬间，用户根本看不出这里断过。
 */
object TrackPlayback {
    /** 自动倍速的目标：全程约 40 秒。 */
    const val TARGET_PLAYBACK_MS = 40_000L

    /** 数据缺失的停顿（墙上时间，不随倍速缩放）。 */
    const val GAP_PAUSE_MS = 600L

    /** 一段至少显示的墙上时间，单点段也要看得见。 */
    const val MIN_SPAN_DISPLAY_MS = 200L

    const val MIN_SPEED = 1.0

    /** 手动倍速档位。自动档不设上限（见 DON'T 章节：长轨迹需要几百倍才能 40 秒走完）。 */
    val MANUAL_SPEEDS = listOf(1.0, 2.0, 4.0, 8.0)

    /**
     * 按断点切段。切点是断点的 [PlaybackGap.endMs]：它之后（含）的点属于下一段。
     * 传进来的 gaps 必须已经按时间排好，调用方直接用 `observeGaps` 的结果。
     */
    fun spans(
        samples: List<PlaybackSample>,
        gaps: List<PlaybackGap>,
    ): List<PlaybackSpan> = spans(samples, gaps, emptyList())

    /**
     * 带停留吸附的切段。
     *
     * **这是关键**：人在家/公司时 GPS 会在几十到几百米内来回漂移
     * （实测同一处停留的点在 113.8056–113.8061、34.6752–34.6761 之间反复跳）。
     * 如果按原始点插值，指针会跟着漂移点来回窜，看起来像「不知道跑哪去了」，
     * 而用户其实一直没动。地图折线早就做了停留吸附（`TrackDisplay.snapToStays`），
     * 回放必须走同一套：时间窗内的点整体换成该停留的锚点，指针停在原地。
     *
     * 吸附在切段**之前**做，和 `TrackDisplay` 的顺序一致。
     */
    fun spans(
        samples: List<PlaybackSample>,
        gaps: List<PlaybackGap>,
        stays: List<StaySegment>,
    ): List<PlaybackSpan> {
        if (samples.isEmpty()) return emptyList()
        val sorted =
            samples.sortedBy { it.tsMs }.let { list ->
                if (stays.isEmpty()) {
                    list
                } else {
                    list.map { sample ->
                        val stay = stays.firstOrNull { sample.tsMs in it.startMs..it.endMs }
                        if (stay == null) sample else sample.copy(point = stay.point)
                    }
                }
            }
        val result = mutableListOf<PlaybackSpan>()
        var from = 0

        gaps.sortedBy { it.startMs }.forEach { gap ->
            val next =
                sorted.indexOfFirst { it.tsMs >= gap.endMs }.let {
                    if (it < 0) sorted.size else it
                }
            if (next > from) result += spanOf(sorted.subList(from, next))
            from = next
        }
        if (from < sorted.size) result += spanOf(sorted.subList(from, sorted.size))
        return result
    }

    private fun spanOf(points: List<PlaybackSample>): PlaybackSpan =
        PlaybackSpan(
            startMs = points.first().tsMs,
            endMs = points.last().tsMs,
            samples = points,
        )

    /**
     * 自动倍速：让各段真实时长加起来约等于 [TARGET_PLAYBACK_MS] 扣除固定停顿。
     *
     * **不设上限**：一整天跨度常有几小时，卡在 8× 会让「约 40 秒」永远不成立。
     * 界面在自动档只显示「自动」两个字，不显示这个倍数。
     */
    fun autoSpeed(spans: List<PlaybackSpan>): Double {
        if (spans.isEmpty()) return MIN_SPEED
        val realMs = spans.sumOf { (it.endMs - it.startMs).coerceAtLeast(0L) }
        val fixedMs = (spans.size - 1) * GAP_PAUSE_MS + spans.size * MIN_SPAN_DISPLAY_MS
        val budget = TARGET_PLAYBACK_MS - fixedMs
        if (budget <= 0 || realMs <= 0L) return MIN_SPEED
        return max(MIN_SPEED, realMs.toDouble() / budget)
    }

    /** 折算倍速并构造时间轴。倍速小于 [MIN_SPEED] 时按 [MIN_SPEED] 处理。 */
    fun timeline(
        spans: List<PlaybackSpan>,
        speed: Double,
    ): PlaybackTimeline = timeline(spans, speed, emptyList())

    /** 带上停留段，界面才能知道「指针不动」是因为人在停留，而不是卡住。 */
    fun timeline(
        spans: List<PlaybackSpan>,
        speed: Double,
        stays: List<StaySegment>,
    ): PlaybackTimeline {
        val effective = max(MIN_SPEED, speed)
        val parts = mutableListOf<PlaybackPart>()
        var cursor = 0L

        spans.forEachIndexed { index, span ->
            if (index > 0) {
                val previous = spans[index - 1]
                parts +=
                    PlaybackGapPart(
                        startMs = cursor,
                        durationMs = GAP_PAUSE_MS,
                        anchor = previous.samples.last().point,
                        tsMs = previous.endMs,
                        spanIndex = index - 1,
                    )
                cursor += GAP_PAUSE_MS
            }
            val real = (span.endMs - span.startMs).coerceAtLeast(0L)
            val duration = max(MIN_SPAN_DISPLAY_MS, (real / effective).toLong())
            parts +=
                PlaybackSpanPart(
                    startMs = cursor,
                    durationMs = duration,
                    span = span,
                    spanIndex = index,
                    stays = stays,
                )
            cursor += duration
        }
        return PlaybackTimeline(cursor, parts)
    }
}
