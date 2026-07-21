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
    val bookId: Int get() = entry.bookId
    val title: String get() = entry.title.ifBlank { "未命名图书" }
    val author: String get() = entry.author.ifBlank { "未知作者" }
    val cover: String get() = entry.cover.ifBlank { entry.img.ifBlank { entry.thumb } }
    val progression: Double get() = entry.progression
    val pinned: Boolean get() = entry.pinned
}

data class RecentReadingUiState(
    val isLoading: Boolean = false,
    val items: List<RecentReadingItem> = emptyList(),
    val error: String? = null
)

class RecentReadingViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsRepository = SettingsRepository(application)
    private val readerDao = ReaderDatabase.get(application).readerDao()
    private val _uiState = MutableStateFlow(RecentReadingUiState())
    val uiState: StateFlow<RecentReadingUiState> = _uiState.asStateFlow()

    fun load(showLoading: Boolean = true) {
        viewModelScope.launch {
            if (showLoading || _uiState.value.items.isEmpty()) {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            } else {
                _uiState.value = _uiState.value.copy(error = null)
            }
            runCatching {
                val serverId = settingsRepository.activeLibraryServerId.first()
                readerDao.getRecentReading(serverId).map { entry ->
                    RecentReadingItem(
                        entry = entry,
                        progress = readerDao.getProgress(serverId, entry.bookId)
                    )
                }
            }.fold(
                onSuccess = { items ->
                    _uiState.value = RecentReadingUiState(items = items)
                },
                onFailure = { e ->
                    _uiState.value = RecentReadingUiState(error = e.message ?: "加载失败")
                }
            )
        }
    }

    fun remove(item: RecentReadingItem) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(items = _uiState.value.items.filterNot { it.bookId == item.bookId })
            val serverId = settingsRepository.activeLibraryServerId.first()
            readerDao.deleteRecentEntry(serverId, item.bookId)
        }
    }

    fun reorder(items: List<RecentReadingItem>) {
        viewModelScope.launch {
            val serverId = settingsRepository.activeLibraryServerId.first()
            val baseSort = System.currentTimeMillis()
            val updated = items.mapIndexed { index, item ->
                val newSort = baseSort + (items.size - index)
                readerDao.updateRecentSortIndex(serverId, item.bookId, newSort)
                item.copy(entry = item.entry.copy(sortIndex = newSort))
            }
            _uiState.value = _uiState.value.copy(items = sortItems(updated))
        }
    }

    fun moveToTop(item: RecentReadingItem) {
        val items = _uiState.value.items.toMutableList()
        val index = items.indexOfFirst { it.bookId == item.bookId }
        if (index <= 0) return
        val moved = items.removeAt(index)
        val firstNormalIndex = items.indexOfFirst { !it.pinned }.let { if (it >= 0) it else items.size }
        val targetIndex = if (moved.pinned) 0 else firstNormalIndex
        items.add(targetIndex, moved)
        reorder(items)
    }

    fun togglePinned(item: RecentReadingItem) {
        viewModelScope.launch {
            val serverId = settingsRepository.activeLibraryServerId.first()
            val pinnedAt = if (item.pinned) 0L else System.currentTimeMillis()
            readerDao.updateRecentPinned(serverId, item.bookId, !item.pinned, pinnedAt)
            val updated = _uiState.value.items.map { current ->
                if (current.bookId == item.bookId) {
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
        val index = items.indexOfFirst { it.bookId == item.bookId }
        if (index <= 0) return
        if (items[index - 1].pinned != item.pinned) return
        val current = items[index]
        items[index] = items[index - 1]
        items[index - 1] = current
        reorder(items)
    }

    fun moveDown(item: RecentReadingItem) {
        val items = _uiState.value.items.toMutableList()
        val index = items.indexOfFirst { it.bookId == item.bookId }
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
