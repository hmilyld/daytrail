package com.hmilyld.daytrail.data.geocode

/**
 * 一次「补全名称」的结构化结果。
 *
 * 早期接口只返回成功个数，调用方无法区分「没配 Key」「高德拒绝」「断网」
 * 「坐标无效」和「查不到名字」，一律只能提示「可能没网」——用户明明有网却
 * 看到这个文案。现在把每个失败点的原因带回来，界面挑最值得说的一条展示。
 *
 * [attempted] 取自实际查询的未命名地点数（而不是界面上的快照），
 * 避免界面与数据库的口径在点击瞬间不一致。
 */
data class NamingResult(
    val attempted: Int,
    val named: Int,
    val failures: List<NamingFailure> = emptyList(),
) {
    val failedCount: Int get() = attempted - named
}

/** 失败原因的展示优先级：越靠前越该先告诉用户。 */
private val FAILURE_PRIORITY =
    listOf(
        NamingFailure.NO_KEY,
        NamingFailure.API_REJECTED,
        NamingFailure.NETWORK,
        NamingFailure.INVALID_POINT,
        NamingFailure.NO_NAME,
    )

/** 多条失败只挑一条展示，避免一次弹出一串提示。 */
internal fun Collection<NamingFailure>.primary(): NamingFailure? =
    distinct().minByOrNull { FAILURE_PRIORITY.indexOf(it) }
