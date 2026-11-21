package com.hmilyld.daytrail.data.update

import java.io.IOException

/**
 * 主源（静态 update.json）与备源（GitHub API）的取舍规则。
 *
 * 抽出来单独测：规则本身容易写错，而写错的后果是**每次启动都去打限流接口**。
 *
 * - 资产**不存在**（404）或内容不可用 → 回退 API：老版本的 Release 本来就没有这份清单；
 * - 请求**本身失败**（DNS、超时） → **不**回退：换一个地址也是同一个网络问题，
 *   只会多一次失败的等待。
 */
internal object UpdateFallback {
    sealed interface ManifestOutcome {
        data class Found(
            val info: UpdateInfo,
        ) : ManifestOutcome

        data object Unusable : ManifestOutcome

        data class Failed(
            val error: IOException,
        ) : ManifestOutcome
    }

    suspend fun resolve(
        manifest: ManifestOutcome,
        api: suspend () -> UpdateInfo?,
    ): UpdateInfo? =
        when (manifest) {
            is ManifestOutcome.Found -> manifest.info
            is ManifestOutcome.Unusable -> api()
            is ManifestOutcome.Failed -> throw manifest.error
        }
}
