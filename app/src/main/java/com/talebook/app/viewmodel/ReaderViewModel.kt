package com.talebook.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.talebook.app.data.api.RetrofitClient
import com.talebook.app.data.repository.BookRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReaderUiState(
    val isLoading: Boolean = false,
    val bookId: Int = 0,
    val bookTitle: String = "",
    val webReadUrl: String? = null,
    val error: String? = null
)

class ReaderViewModel : ViewModel() {
    private val repository = BookRepository()
    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    fun load(bookId: Int) {
        viewModelScope.launch {
            _uiState.value = ReaderUiState(isLoading = true, bookId = bookId)
            var title = ""
            repository.getBookDetail(bookId).onSuccess { book ->
                title = book.title
            }

            _uiState.value = ReaderUiState(
                webReadUrl = RetrofitClient.readUrl(bookId),
                bookId = bookId,
                bookTitle = title
            )
        }
    }
}
