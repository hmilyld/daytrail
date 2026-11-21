package com.hmilyld.daytrail.ui.screens.places

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmilyld.daytrail.core.GeoPoint
import com.hmilyld.daytrail.data.db.dao.PlaceSummary
import com.hmilyld.daytrail.data.repo.TrackRepository
import com.hmilyld.daytrail.domain.PlaceNeighbors
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** 合并候选：另一个地点，以及它离当前地点多远。 */
data class PlaceMergeTarget(
    val id: Long,
    val label: String?,
    val distanceMeters: Double,
)

/** 单个地点的详情。 */
class PlaceDetailViewModel(
    private val repository: TrackRepository,
) : ViewModel() {
    private val _place = MutableStateFlow<PlaceSummary?>(null)
    val place: StateFlow<PlaceSummary?> = _place.asStateFlow()

    /**
     * 可以合并进来的地点，按距离升序。
     *
     * 只列 [PlaceNeighbors.PICK_RADIUS_METERS] 以内的：相隔一公里的两个地点
     * 合并没有意义，列出来只会把选择列表拉长。
     */
    private val _targets = MutableStateFlow<List<PlaceMergeTarget>>(emptyList())
    val targets: StateFlow<List<PlaceMergeTarget>> = _targets.asStateFlow()

    private var loadedId: Long? = null
    private var job: Job? = null

    fun load(id: Long) {
        if (loadedId == id) return
        loadedId = id
        job?.cancel()
        job =
            viewModelScope.launch {
                // 同时订阅单个地点和全部地点：合并之后两边都要跟着刷新，
                // 而候选列表本来就依赖全部地点。
                combine(repository.observePlace(id), repository.observePlaces()) { self, all ->
                    self to all
                }.collect { (self, all) ->
                    _place.value = self
                    _targets.value = buildTargets(id, all)
                }
            }
    }

    private fun buildTargets(
        id: Long,
        all: List<PlaceSummary>,
    ): List<PlaceMergeTarget> {
        val entries =
            all.filter { !it.place.suppressed }.map { summary ->
                PlaceNeighbors.Entry(
                    id = summary.place.id,
                    point = GeoPoint(summary.place.latitude, summary.place.longitude),
                    radiusMeters = summary.place.radiusMeters,
                )
            }
        val labels = all.associate { it.place.id to it.place.label }

        // 距离排序、半径过滤都在 PlaceNeighbors 里，和列表页的「疑似重复」共用同一套，
        // 免得两处半径各写一份（这正是 0.3.2 那次 500/1000 不一致的来由）。
        return PlaceNeighbors
            .around(entries, fromId = id, radiusMeters = PlaceNeighbors.PICK_RADIUS_METERS)
            .map { neighbor ->
                PlaceMergeTarget(
                    id = neighbor.id,
                    label = labels[neighbor.id],
                    distanceMeters = neighbor.distanceMeters,
                )
            }
    }

    fun rename(
        id: Long,
        label: String,
    ) {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { repository.renamePlace(id, trimmed) }
    }

    /**
     * 把当前地点合并进 [toId]。
     *
     * suspend：调用方要等写库完成再返回，否则弹栈会取消写入，合并只做了一半
     * （停留改判了、源地点还没删）。
     */
    suspend fun merge(
        fromId: Long,
        toId: Long,
    ): Boolean = repository.mergePlaces(fromId, toId)

    /** 删除地点。suspend：调用方要等写库完成再返回，避免弹栈取消写入。 */
    suspend fun remove(id: Long) {
        repository.suppressPlace(id)
    }
}
