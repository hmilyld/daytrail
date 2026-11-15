package com.hmilyld.daytrail.data.repo

import com.hmilyld.daytrail.data.db.entity.TrackPointEntity

/**
 * 云端同步网关。
 *
 * 这是**云端接缝 ②**。现在只有空实现：数据全部留在本机，
 * 但接口与数据模型已经就位（`track_point.sync_state` / `device_id`），
 * 二期接入服务器时只需实现这个接口并替换容器里的实例。
 */
interface SyncGateway {
    /** 是否有待同步数据。 */
    suspend fun hasPending(): Boolean

    /** 批量上传未同步的点。返回成功上传的条数。 */
    suspend fun upload(points: List<TrackPointEntity>): Int
}

/** 空实现：永远没有待同步数据，上传恒为 0。 */
class NoOpSyncGateway : SyncGateway {
    override suspend fun hasPending(): Boolean = false

    override suspend fun upload(points: List<TrackPointEntity>): Int = 0
}
