package com.hmilyld.daytrail.ui.screens.places

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmilyld.daytrail.core.GeoPoint
import com.hmilyld.daytrail.data.db.dao.PlaceSummary
import com.hmilyld.daytrail.data.repo.TrackRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 地点页 ViewModel。
 *
 * 地点是派生数据（由停留聚类而来），所以这里只做改名和「删除」两件事：
 * - 改名写进 place 表，所有引用它的停留一起显示新名字
 * - 删除只隐藏，不删行——直接删行的话下一次重算会立刻把同一个地点建回来
 */
class PlacesViewModel(
    private val repository: TrackRepository,
) : ViewModel() {
    val places: StateFlow<List<PlaceSummary>> =
        repository
            .observePlaces()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    fun rename(
        id: Long,
        label: String,
    ) {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { repository.renamePlace(id, trimmed) }
    }

    fun remove(id: Long) {
        viewModelScope.launch { repository.suppressPlace(id) }
    }

    /**
     * 在地图上手动新增一个地点。
     *
     * 是 suspend 而不是 launch 到 viewModelScope：调用方要**等写库完成再返回**，
     * 否则弹栈会清掉 ViewModel，写入可能被取消——地点就这样静默丢了。
     */
    suspend fun add(
        point: GeoPoint,
        label: String,
    ) {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return
        repository.addPlace(point, trimmed)
    }
}
