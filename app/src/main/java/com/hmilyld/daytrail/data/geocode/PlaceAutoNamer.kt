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
     * 给所有未命名地点补名字，返回结构化结果：成功几个、其余为什么没成功。
     *
     * 早期只返回成功个数，界面无法区分「没配 Key」「高德拒绝」「断网」，一律
     * 提示「可能没网」。现在把每个失败点的原因带回去，让界面说人话。
     *
     * 逐个查而不是并发：地点通常只有个位数，串行更容易观察，也不会触发配额。
     */
    suspend fun backfill(): NamingResult {
        if (!enabled) {
            return NamingResult(attempted = 0, named = 0, failures = listOf(NamingFailure.NO_KEY))
        }
        val places = placeDao.unnamed()
        val failures = mutableListOf<NamingFailure>()
        var named = 0
        for (place in places) {
            val point = GeoPoint(place.latitude, place.longitude)
            val result = client.lookup(point)
            val name = result.getOrNull()
            if (name.isNullOrBlank()) {
                failures +=
                    (result.exceptionOrNull() as? ReverseGeocodeException)?.failure
                        ?: NamingFailure.NO_NAME
                continue
            }
            placeDao.renameIfUnnamed(place.id, name, LabelSource.AUTO)
            named++
        }
        return NamingResult(attempted = places.size, named = named, failures = failures)
    }

    private companion object {
        const val TAG = "PlaceAutoNamer"
    }
}
