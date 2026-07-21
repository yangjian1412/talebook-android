package com.talebook.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.talebook.app.data.model.Book
import com.talebook.app.data.repository.BookRepository
import com.talebook.app.data.repository.IndexData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = false,
    val readingBooks: List<Book> = emptyList(),
    val shelfBooks: List<Book> = emptyList(),
    val randomBooks: List<Book> = emptyList(),
    val newBooks: List<Book> = emptyList(),
    val error: String? = null
)

class HomeViewModel : ViewModel() {
    private val repository = BookRepository()
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            // 并行拉：首页 + 最近阅读 + 书架
            val indexResult = repository.getIndex()
            val readingResult = repository.getReading()
            val shelfResult = repository.getShelf()

            val reading = readingResult.getOrDefault(emptyList())
            val shelf = shelfResult.getOrDefault(emptyList())
            val (random, new) = indexResult.fold(
                onSuccess = { data -> data.randomBooks to data.newBooks },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(error = e.message ?: "加载失败")
                    emptyList<Book>() to emptyList()
                }
            )
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                readingBooks = reading,
                shelfBooks = shelf,
                randomBooks = random,
                newBooks = new
            )
        }
    }
}
