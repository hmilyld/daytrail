package com.hmilyld.daytrail.ui.screens.places

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmilyld.daytrail.core.GeoPoint
import com.hmilyld.daytrail.data.db.dao.PlaceSummary
import com.hmilyld.daytrail.data.repo.TrackRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    /** 「补全名称」的结果，一次性提示用。 */
    data class NamingOutcome(
        val attempted: Int,
        val named: Int,
    )

    private val _naming = MutableStateFlow(false)

    /** 是否正在联网补名字。用于禁用按钮、显示「补全中…」。 */
    val naming: StateFlow<Boolean> = _naming.asStateFlow()

    private val _outcome = MutableStateFlow<NamingOutcome?>(null)
    val outcome: StateFlow<NamingOutcome?> = _outcome.asStateFlow()

    /**
     * 给所有未命名地点补名字。
     *
     * 这是本应用里第二处**主动联网**（第一处是按需纠偏），所以只由用户点按钮触发，
     * 不做后台自动补——离线优先的原则不能被悄悄破坏。
     */
    fun backfillNames() {
        if (_naming.value) return
        viewModelScope.launch {
            val attempted = places.value.count { it.place.label.isNullOrBlank() }
            _naming.value = true
            val named = runCatching { repository.backfillPlaceNames() }.getOrDefault(0)
            _naming.value = false
            _outcome.value = NamingOutcome(attempted = attempted, named = named)
        }
    }

    fun consumeOutcome() {
        _outcome.value = null
    }

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
