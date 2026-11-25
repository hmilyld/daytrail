package com.hmilyld.daytrail.data.geocode

/**
 * 「补全名称」的失败原因。
 *
 * 只用来驱动界面文案：界面按枚举取 [com.hmilyld.daytrail.R.string]，
 * 不在这里存中文。所有原因都不会影响记录主流程，失败就是保持「未命名」。
 */
enum class NamingFailure {
    /** 没配 Web Key，请求根本没发出去。 */
    NO_KEY,

    /** 高德拒绝了请求：Key 无效、类型不对或配额用完。 */
    API_REJECTED,

    /** HTTP 错误或网络异常（超时、断网、代理返回非 JSON 等）。 */
    NETWORK,

    /** 坐标无效，请求根本没发出去。 */
    INVALID_POINT,

    /** 请求成功，但这个位置实在取不到名字。 */
    NO_NAME,
}
