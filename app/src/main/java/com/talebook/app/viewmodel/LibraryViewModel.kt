package com.talebook.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.talebook.app.data.model.Book
import com.talebook.app.data.repository.BookRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LibraryUiState(
    val isLoading: Boolean = false,
    val books: List<Book> = emptyList(),
    val error: String? = null
)

class LibraryViewModel : ViewModel() {
    private val repository = BookRepository()
    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    fun loadLibrary() {
        viewModelScope.launch {
            _uiState.value = LibraryUiState(isLoading = true)
            repository.getLibrary().fold(
                onSuccess = { books ->
                    _uiState.value = LibraryUiState(books = books)
                },
                onFailure = { e ->
                    _uiState.value = LibraryUiState(error = e.message ?: "加载失败")
                }
            )
        }
    }

    fun search(query: String) {
        viewModelScope.launch {
            _uiState.value = LibraryUiState(isLoading = true)
            repository.search(query).fold(
                onSuccess = { books ->
                    _uiState.value = LibraryUiState(books = books)
                },
                onFailure = { e ->
                    _uiState.value = LibraryUiState(error = e.message ?: "搜索失败")
                }
            )
        }
    }

    fun loadCategoryBooks(meta: String, name: String) {
        viewModelScope.launch {
            _uiState.value = LibraryUiState(isLoading = true)
            repository.getCategoryBooks(meta, name).fold(
                onSuccess = { books ->
                    _uiState.value = LibraryUiState(books = books)
                },
                onFailure = { e ->
                    _uiState.value = LibraryUiState(error = e.message ?: "加载失败")
                }
            )
        }
    }
}
