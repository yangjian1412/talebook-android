package com.talebook.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.talebook.app.data.model.BookDetail
import com.talebook.app.data.model.ReadState
import com.talebook.app.data.repository.BookRepository
import com.talebook.app.data.repository.DownloadEvent
import com.talebook.app.data.repository.DownloadRepository
import com.talebook.app.data.repository.ReaderCacheInfo
import com.talebook.app.data.repository.ReaderCacheRepository
import com.talebook.app.data.local.ReaderDatabase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BookDetailUiState(
    val isLoading: Boolean = false,
    val book: BookDetail? = null,
    val readState: ReadState? = null,
    val error: String? = null,
    val downloadState: DownloadState = DownloadState.Idle,
    val inShelf: Boolean = false,
    val shelfBusy: Boolean = false,
    val shelfError: String? = null,
    val localProgression: Double = 0.0,
    val cacheInfo: ReaderCacheInfo = ReaderCacheInfo(isCached = false),
    val cacheBusy: Boolean = false,
    val cacheMessage: String = ""
)

sealed interface DownloadState {
    data object Idle : DownloadState
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : DownloadState
    data class Done(val displayName: String, val relativePath: String) : DownloadState
    data class Failed(val message: String) : DownloadState
}

class BookDetailViewModel : ViewModel() {
    private val repository = BookRepository()
    private val downloadRepository = DownloadRepository()
    private val readerCacheRepository = ReaderCacheRepository()
    private val _uiState = MutableStateFlow(BookDetailUiState())
    val uiState: StateFlow<BookDetailUiState> = _uiState.asStateFlow()

    private var downloadJob: Job? = null

    fun loadBook(bookId: Int) {
        viewModelScope.launch {
            _uiState.value = BookDetailUiState(isLoading = true)
            repository.getBookDetail(bookId).fold(
                onSuccess = { book ->
                    _uiState.value = BookDetailUiState(book = book)
                    loadReadState(bookId)
                    loadShelfStatus()
                },
                onFailure = { e ->
                    _uiState.value = BookDetailUiState(error = e.message ?: "加载失败")
                }
            )
        }
    }

    fun loadLocalReaderInfo(context: Context, bookId: Int) {
        viewModelScope.launch {
            val appCtx = context.applicationContext
            val dao = ReaderDatabase.get(appCtx).readerDao()
            val progress = dao.getProgress(bookId)?.progression ?: 0.0
            val cacheInfo = readerCacheRepository.cacheInfo(appCtx, bookId)
            _uiState.update {
                it.copy(localProgression = progress, cacheInfo = cacheInfo, cacheMessage = "")
            }
        }
    }

    fun deleteLocalCache(context: Context) {
        val bookId = _uiState.value.book?.id ?: return
        if (_uiState.value.cacheBusy) return
        viewModelScope.launch {
            val appCtx = context.applicationContext
            _uiState.update { it.copy(cacheBusy = true, cacheMessage = "正在删除缓存...") }
            val removed = readerCacheRepository.deleteBookCache(appCtx, bookId)
            val cacheInfo = readerCacheRepository.cacheInfo(appCtx, bookId)
            _uiState.update {
                it.copy(
                    cacheBusy = false,
                    cacheInfo = cacheInfo,
                    cacheMessage = if (removed > 0L) "已删除缓存 ${(removed / 1024.0 / 1024.0).formatMb()} MB" else "本书没有本地缓存"
                )
            }
        }
    }

    private suspend fun loadReadState(bookId: Int) {
        repository.getReadState(bookId).fold(
            onSuccess = { state ->
                _uiState.value = _uiState.value.copy(readState = state)
            },
            onFailure = { }
        )
    }

    private suspend fun loadShelfStatus() {
        val bookId = _uiState.value.book?.id ?: return
        repository.getShelf().onSuccess { shelf ->
            val inShelf = shelf.any { it.id == bookId }
            _uiState.update { it.copy(inShelf = inShelf) }
        }
        // 失败时静默：未登录或服务端异常时 inShelf 保持默认 false
    }

    fun toggleShelf() {
        val book = _uiState.value.book ?: return
        if (_uiState.value.shelfBusy) return
        val targetState = !_uiState.value.inShelf

        viewModelScope.launch {
            _uiState.update { it.copy(shelfBusy = true, shelfError = null) }
            repository.toggleShelf(book.id, targetState).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(inShelf = targetState, shelfBusy = false)
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            shelfBusy = false,
                            shelfError = e.message ?: "操作失败"
                        )
                    }
                }
            )
        }
    }

    fun clearShelfError() {
        _uiState.update { it.copy(shelfError = null) }
    }

    fun download(context: Context) {
        val book = _uiState.value.book ?: return
        if (_uiState.value.downloadState is DownloadState.Downloading) return

        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            val appCtx = context.applicationContext
            downloadRepository.downloadBookAsFlow(appCtx, book.id, book).collect { event ->
                when (event) {
                    is DownloadEvent.Started -> _uiState.update {
                        it.copy(downloadState = DownloadState.Downloading(0, event.totalBytes))
                    }
                    is DownloadEvent.Progress -> _uiState.update {
                        it.copy(downloadState = DownloadState.Downloading(event.downloadedBytes, event.totalBytes))
                    }
                    is DownloadEvent.Done -> _uiState.update {
                        it.copy(downloadState = DownloadState.Done(event.displayName, event.relativePath))
                    }
                    is DownloadEvent.Failed -> _uiState.update {
                        it.copy(downloadState = DownloadState.Failed(event.message))
                    }
                }
            }
        }
    }
}

private fun Double.formatMb(): String = String.format(java.util.Locale.US, "%.1f", this)
