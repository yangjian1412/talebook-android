package com.talebook.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.talebook.app.data.local.ReaderDatabase
import com.talebook.app.data.local.ReadingProgressEntity
import com.talebook.app.data.local.RecentReadingEntity
import com.talebook.app.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class RecentReadingItem(
    val entry: RecentReadingEntity,
    val progress: ReadingProgressEntity?
) {
    val serverId: String get() = entry.serverId
    val bookId: Int get() = entry.bookId
    val title: String get() = entry.title.ifBlank { "未命名图书" }
    val author: String get() = entry.author.ifBlank { "未知作者" }
    val cover: String get() = entry.cover.ifBlank { entry.img.ifBlank { entry.thumb } }
    val progression: Double get() = entry.progression
    val pinned: Boolean get() = entry.pinned
    val sourceKind: String get() = entry.sourceKind
    val sourceLabel: String get() = entry.sourceLabel
    val isLocal: Boolean get() = sourceKind == RecentReadingEntity.SOURCE_KIND_LOCAL
}

data class RecentReadingUiState(
    val isLoading: Boolean = false,
    val items: List<RecentReadingItem> = emptyList(),
    val error: String? = null
)

class RecentReadingViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsRepository = SettingsRepository(application)
    private val readerDao = ReaderDatabase.get(application).readerDao()
    private val _uiState = MutableStateFlow(RecentReadingUiState(isLoading = true))
    val uiState: StateFlow<RecentReadingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    fun load(showLoading: Boolean = true) {
        viewModelScope.launch {
            if (showLoading || _uiState.value.items.isEmpty()) {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            } else {
                _uiState.value = _uiState.value.copy(error = null)
            }
            runCatching {
                val libraryServerId = settingsRepository.activeLibraryServerId.first()
                val localServerId = "local"
                val libraryEntries = readerDao.getRecentReading(libraryServerId)
                val localEntries = readerDao.getRecentReading(localServerId)
                (libraryEntries + localEntries).map { entry ->
                    RecentReadingItem(
                        entry = entry,
                        progress = readerDao.getProgress(entry.serverId, entry.bookId)
                    )
                }
            }.fold(
                onSuccess = { items ->
                    _uiState.value = RecentReadingUiState(items = sortItems(items))
                },
                onFailure = { e ->
                    _uiState.value = RecentReadingUiState(error = e.message ?: "加载失败")
                }
            )
        }
    }

    fun remove(item: RecentReadingItem) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(items = _uiState.value.items.filterNot { it.bookId == item.bookId && it.serverId == item.serverId })
            readerDao.deleteRecentEntry(item.serverId, item.bookId)
        }
    }

    fun reorder(items: List<RecentReadingItem>) {
        viewModelScope.launch {
            val baseSort = System.currentTimeMillis()
            val updated = items.mapIndexed { index, item ->
                val newSort = baseSort + (items.size - index)
                readerDao.updateRecentSortIndex(item.serverId, item.bookId, newSort)
                item.copy(entry = item.entry.copy(sortIndex = newSort))
            }
            _uiState.value = _uiState.value.copy(items = sortItems(updated))
        }
    }

    fun moveToTop(item: RecentReadingItem) {
        val items = _uiState.value.items.toMutableList()
        val index = items.indexOfFirst { it.bookId == item.bookId && it.serverId == item.serverId }
        if (index <= 0) return
        val moved = items.removeAt(index)
        val firstNormalIndex = items.indexOfFirst { !it.pinned }.let { if (it >= 0) it else items.size }
        val targetIndex = if (moved.pinned) 0 else firstNormalIndex
        items.add(targetIndex, moved)
        reorder(items)
    }

    fun togglePinned(item: RecentReadingItem) {
        viewModelScope.launch {
            val pinnedAt = if (item.pinned) 0L else System.currentTimeMillis()
            readerDao.updateRecentPinned(item.serverId, item.bookId, !item.pinned, pinnedAt)
            val updated = _uiState.value.items.map { current ->
                if (current.bookId == item.bookId && current.serverId == item.serverId) {
                    current.copy(entry = current.entry.copy(pinned = !item.pinned, pinnedAt = pinnedAt))
                } else {
                    current
                }
            }
            _uiState.value = _uiState.value.copy(items = sortItems(updated))
        }
    }

    fun moveUp(item: RecentReadingItem) {
        val items = _uiState.value.items.toMutableList()
        val index = items.indexOfFirst { it.bookId == item.bookId && it.serverId == item.serverId }
        if (index <= 0) return
        if (items[index - 1].pinned != item.pinned) return
        val current = items[index]
        items[index] = items[index - 1]
        items[index - 1] = current
        reorder(items)
    }

    fun moveDown(item: RecentReadingItem) {
        val items = _uiState.value.items.toMutableList()
        val index = items.indexOfFirst { it.bookId == item.bookId && it.serverId == item.serverId }
        if (index < 0 || index >= items.lastIndex) return
        if (items[index + 1].pinned != item.pinned) return
        val current = items[index]
        items[index] = items[index + 1]
        items[index + 1] = current
        reorder(items)
    }

    private fun sortItems(items: List<RecentReadingItem>): List<RecentReadingItem> = items.sortedWith(
        compareByDescending<RecentReadingItem> { it.entry.pinned }
            .thenByDescending { it.entry.pinnedAt }
            .thenByDescending { it.entry.sortIndex }
            .thenByDescending { it.entry.updatedAt }
    )
}
