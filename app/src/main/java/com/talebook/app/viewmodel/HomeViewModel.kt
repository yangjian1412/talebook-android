package com.talebook.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.talebook.app.data.model.Book
import com.talebook.app.data.repository.BookRepository
import com.talebook.app.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val readingBooks: List<Book> = emptyList(),
    val shelfBooks: List<Book> = emptyList(),
    val randomBooks: List<Book> = emptyList(),
    val newBooks: List<Book> = emptyList(),
    val serverConfigured: Boolean = false,
    val error: String? = null
)

class HomeViewModel : ViewModel() {
    private val repository = BookRepository()
    private val gson = Gson()
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun bindContext(context: Context) {
        val settings = SettingsRepository(context.applicationContext)
        viewModelScope.launch {
            settings.activeLibraryServer.collect { server ->
                val configured = server.baseUrl.isNotBlank()
                if (_uiState.value.serverConfigured != configured) {
                    _uiState.value = _uiState.value.copy(serverConfigured = configured)
                }
            }
        }
    }

    fun load() {
        loadInternal(initial = _uiState.value.readingBooks.isEmpty())
    }

    fun forceRefresh() {
        loadInternal(initial = false)
    }

    private fun loadInternal(initial: Boolean) {
        if (!_uiState.value.serverConfigured) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = initial,
                isRefreshing = !initial,
                error = null
            )
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
            val next = _uiState.value.copy(
                isLoading = false,
                isRefreshing = false,
                readingBooks = reading,
                shelfBooks = shelf,
                randomBooks = random,
                newBooks = new
            )
            _uiState.value = next
        }
    }

    fun readCachedHome(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_HOME_CACHE, null) ?: return
        runCatching { gson.fromJson(json, HomeUiState::class.java) }.getOrNull()?.let { cached ->
            if (cached.serverConfigured) {
                _uiState.value = cached.copy(isLoading = false, isRefreshing = false, error = null)
            }
        }
    }

    fun writeCachedHome(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val snapshot = _uiState.value.copy(isLoading = false, isRefreshing = false)
        prefs.edit().putString(KEY_HOME_CACHE, gson.toJson(snapshot)).apply()
    }

    companion object {
        private const val PREFS = "home_cache"
        private const val KEY_HOME_CACHE = "cached_state"

        fun clearCache(context: Context) {
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_HOME_CACHE)
                .apply()
        }
    }
}