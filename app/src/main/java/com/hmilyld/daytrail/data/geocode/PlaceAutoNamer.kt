package com.hmilyld.daytrail.data.geocode

import android.util.Log
import com.hmilyld.daytrail.core.GeoPoint
import com.hmilyld.daytrail.data.db.dao.PlaceDao
import com.hmilyld.daytrail.data.db.entity.LabelSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 新建地点时的「顺手取个名字」。
 *
 * 为什么是 fire-and-forget：`recomputeDay` 每 60 秒跑一次，录制期间会被频繁调用；
 * 在它里面等一次网络（最长 8 秒超时）会拖慢整条记录链路。这里只把请求丢到自己的
 * scope 里，失败静默——地点仍是「未命名」，功能一点不受影响。
 *
 * 只写**还没有名字**的地点（`renameIfUnnamed`），所以：
 * - 用户起过的名字永远不会被覆盖；
 * - 同一次重算里不会重复命名。
 */
class PlaceAutoNamer(
    private val client: ReverseGeocodeClient,
    private val placeDao: PlaceDao,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    val enabled: Boolean get() = client.configured

    /** 给一个新地点补名字。立即返回，不阻塞调用方。 */
    fun enrich(
        placeId: Long,
        point: GeoPoint,
    ) {
        if (!enabled) return
        scope.launch {
            runCatching {
                client.lookup(point).getOrNull()?.let { name ->
                    placeDao.renameIfUnnamed(placeId, name, LabelSource.AUTO)
                }
            }.onFailure { Log.d(TAG, "地点 $placeId 自动命名失败：${it.message}") }
        }
    }

    /**
     * 给所有未命名地点补名字，返回补成功的个数。
     *
     * 逐个查而不是并发：地点通常只有个位数，串行更容易观察，也不会触发配额。
     */
    suspend fun backfill(): Int {
        if (!enabled) return 0
        return placeDao.unnamed().count { place ->
            val point = GeoPoint(place.latitude, place.longitude)
            val name = runCatching { client.lookup(point).getOrNull() }.getOrNull()
            if (name.isNullOrBlank()) {
                false
            } else {
                placeDao.renameIfUnnamed(place.id, name, LabelSource.AUTO)
                true
            }
        }
    }

    private companion object {
        const val TAG = "PlaceAutoNamer"
    }
}
