package com.talebook.app.ui.screens

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.talebook.app.data.repository.LocalBook
import com.talebook.app.data.repository.LocalFolder
import com.talebook.app.data.repository.LocalLibraryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BrowseEntry(
    val displayName: String,
    val uri: Uri,
    val isDirectory: Boolean,
    val isBook: Boolean
)

data class LocalLibraryCache(
    val folders: List<LocalFolder> = emptyList(),
    val books: List<LocalBook> = emptyList()
)

class LocalLibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LocalLibraryRepository(application)
    private val gson = Gson()
    private val prefsName = "local_library_cache"

    private val _folders = MutableStateFlow<List<LocalFolder>>(emptyList())
    val folders: StateFlow<List<LocalFolder>> = _folders.asStateFlow()

    private val _books = MutableStateFlow<List<LocalBook>>(emptyList())
    val books: StateFlow<List<LocalBook>> = _books.asStateFlow()

    private val _browsePath = MutableStateFlow<Uri?>(null)
    val browsePath: StateFlow<Uri?> = _browsePath.asStateFlow()

    private val _browseEntries = MutableStateFlow<List<BrowseEntry>>(emptyList())
    val browseEntries: StateFlow<List<BrowseEntry>> = _browseEntries.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private var refreshJob: Job? = null

    fun searchBooks(query: String) {
        _searchQuery.value = query
    }

    fun clearSearch() {
        _searchQuery.value = ""
    }

    fun readCachedAsync() {
        viewModelScope.launch(Dispatchers.IO) {
            val prefs = getApplication<Application>().getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            val json = prefs.getString("cache", null) ?: return@launch
            val cached = runCatching { gson.fromJson(json, LocalLibraryCache::class.java) }.getOrNull()
                ?: return@launch
            _folders.value = cached.folders
            _books.value = cached.books
        }
    }

    private fun writeCache() {
        val cache = LocalLibraryCache(folders = _folders.value, books = _books.value)
        val prefs = getApplication<Application>().getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        prefs.edit().putString("cache", gson.toJson(cache)).apply()
    }

    fun refresh() {
        if (_refreshing.value) return
        refreshJob = viewModelScope.launch {
            _refreshing.value = true
            try {
                val (newFolders, newBooks) = withContext(Dispatchers.IO) {
                    repository.listFolders() to repository.listBooks()
                }
                _folders.value = newFolders
                _books.value = newBooks
                withContext(Dispatchers.IO) { writeCache() }
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun refreshAll() {
        if (_refreshing.value) return
        refreshJob = viewModelScope.launch {
            _refreshing.value = true
            try {
                withContext(Dispatchers.IO) {
                    _folders.value.forEach { folder -> repository.refreshFolder(folder.id) }
                }
                val (newFolders, newBooks) = withContext(Dispatchers.IO) {
                    repository.listFolders() to repository.listBooks()
                }
                _folders.value = newFolders
                _books.value = newBooks
                withContext(Dispatchers.IO) { writeCache() }
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun refreshFolder(folderId: Long) {
        if (_refreshing.value) return
        refreshJob = viewModelScope.launch {
            _refreshing.value = true
            try {
                withContext(Dispatchers.IO) { repository.refreshFolder(folderId) }
                val (newFolders, newBooks) = withContext(Dispatchers.IO) {
                    repository.listFolders() to repository.listBooks()
                }
                _folders.value = newFolders
                _books.value = newBooks
                withContext(Dispatchers.IO) { writeCache() }
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun removeFolder(folderId: Long) {
        if (_refreshing.value) return
        refreshJob = viewModelScope.launch {
            _refreshing.value = true
            try {
                withContext(Dispatchers.IO) { repository.removeFolder(folderId) }
                val (newFolders, newBooks) = withContext(Dispatchers.IO) {
                    repository.listFolders() to repository.listBooks()
                }
                _folders.value = newFolders
                _books.value = newBooks
                withContext(Dispatchers.IO) { writeCache() }
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun addFolder(treeUri: Uri, displayName: String) {
        if (_refreshing.value) return
        refreshJob = viewModelScope.launch {
            _refreshing.value = true
            try {
                withContext(Dispatchers.IO) { repository.addFolder(treeUri, displayName) }
                val (newFolders, newBooks) = withContext(Dispatchers.IO) {
                    repository.listFolders() to repository.listBooks()
                }
                _folders.value = newFolders
                _books.value = newBooks
                withContext(Dispatchers.IO) { writeCache() }
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun browse(uri: Uri) {
        viewModelScope.launch {
            _browsePath.value = uri
            val node = DocumentFile.fromTreeUri(getApplication(), uri)
            val list = node?.listFiles()?.map { doc ->
                BrowseEntry(
                    displayName = doc.name ?: "",
                    uri = doc.uri,
                    isDirectory = doc.isDirectory,
                    isBook = !doc.isDirectory && doc.name?.let { name ->
                        val ext = name.lowercase()
                        ext.endsWith(".epub") || ext.endsWith(".pdf") || ext.endsWith(".txt") || ext.endsWith(".mobi")
                    } == true
                )
            }.orEmpty().sortedWith(compareByDescending<BrowseEntry> { it.isDirectory }.thenBy { it.displayName.lowercase() })
            _browseEntries.value = list
        }
    }
}