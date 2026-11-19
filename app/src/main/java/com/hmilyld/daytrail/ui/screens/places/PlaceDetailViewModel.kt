package com.hmilyld.daytrail.ui.screens.places

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hmilyld.daytrail.data.db.dao.PlaceSummary
import com.hmilyld.daytrail.data.repo.TrackRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 单个地点的详情。 */
class PlaceDetailViewModel(
    private val repository: TrackRepository,
) : ViewModel() {
    private val _place = MutableStateFlow<PlaceSummary?>(null)
    val place: StateFlow<PlaceSummary?> = _place.asStateFlow()

    private var loadedId: Long? = null
    private var job: Job? = null

    fun load(id: Long) {
        if (loadedId == id) return
        loadedId = id
        job?.cancel()
        job =
            viewModelScope.launch {
                repository.observePlace(id).collect { _place.value = it }
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

    /** 删除地点。suspend：调用方要等写库完成再返回，避免弹栈取消写入。 */
    suspend fun remove(id: Long) {
        repository.suppressPlace(id)
    }
}
