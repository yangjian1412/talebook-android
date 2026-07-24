package com.talebook.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.talebook.app.data.repository.LocalBook
import com.talebook.app.data.repository.SettingsRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalLibraryScreen(
    contentPadding: PaddingValues = PaddingValues(),
    onOpenLocalLibrary: () -> Unit = {},
    onBookClick: (Int) -> Unit = {},
    onReadBook: (Int) -> Unit = {},
    onReadLocalBook: (Long) -> Unit = {},
    onBack: (() -> Unit)? = null,
    viewModel: LocalLibraryViewModel = viewModel()
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val settingsRepository = remember { SettingsRepository(context.applicationContext) }
    val skipAuth by settingsRepository.skipAuth.collectAsState(initial = false)
    val folders by viewModel.folders.collectAsState()
    val books by viewModel.books.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val refreshing by viewModel.refreshing.collectAsState()
    val browsePath by viewModel.browsePath.collectAsState()
    val browseEntries by viewModel.browseEntries.collectAsState()
    var selectedFolderIndex by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        viewModel.readCachedAsync()
        viewModel.refresh()
    }

    LaunchedEffect(folders) {
        if (selectedFolderIndex >= folders.size) {
            selectedFolderIndex = (folders.size - 1).coerceAtLeast(0)
        }
    }

    val pickFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        if (treeUri != null) {
            val displayName = treeUri.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/')
                ?: treeUri.toString().substringAfterLast('/')
            viewModel.addFolder(treeUri, displayName)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("本地书架") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshAll() }, enabled = !refreshing) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新全部")
                    }
                    IconButton(onClick = { pickFolderLauncher.launch(null) }, enabled = !refreshing) {
                        Icon(Icons.Default.Folder, contentDescription = "添加文件夹")
                    }
                }
            )
        }
    ) { padding ->
        val combinedPadding = PaddingValues(
            start = padding.calculateStartPadding(LayoutDirection.Ltr),
            top = padding.calculateTopPadding(),
            end = padding.calculateEndPadding(LayoutDirection.Ltr),
            bottom = padding.calculateBottomPadding() + contentPadding.calculateBottomPadding()
        )
        Column(modifier = Modifier.fillMaxSize().padding(combinedPadding)) {
            if (folders.isEmpty()) {
                EmptyLocalState(
                    onAdd = { pickFolderLauncher.launch(null) },
                    modifier = Modifier.weight(1f).padding(16.dp)
                )
                return@Column
            }
            if (folders.size > 1) {
                ScrollableTabRow(
                    selectedTabIndex = selectedFolderIndex,
                    edgePadding = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    folders.forEachIndexed { index, folder ->
                        Tab(
                            selected = selectedFolderIndex == index,
                            onClick = {
                                selectedFolderIndex = index
                                viewModel.clearSearch()
                                focusManager.clearFocus()
                            },
                            text = { Text(folder.displayName, maxLines = 1) }
                        )
                    }
                }
            }
            val currentFolder = folders.getOrNull(selectedFolderIndex)
            if (currentFolder != null) {
                val folderBooks = remember(currentFolder.id, books) {
                    books.filter { it.folderId == currentFolder.id }
                }
                val displayBooks = remember(folderBooks, searchQuery) {
                    if (searchQuery.isBlank()) folderBooks
                    else folderBooks.filter { it.displayName.contains(searchQuery, ignoreCase = true) }
                }
                LaunchedEffect(searchQuery) {
                    if (searchQuery.isNotEmpty() && displayBooks.isNotEmpty()) {
                        listState.scrollToItem(0)
                    }
                }
                Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            if (folders.size <= 1) {
                                Text(currentFolder.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(2.dp))
                            }
                            Text(
                                text = if (searchQuery.isBlank()) "${folderBooks.count { it.available }} 本可用"
                                else "${displayBooks.size} / ${folderBooks.count { it.available }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { viewModel.refreshFolder(currentFolder.id) }, enabled = !refreshing) {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新")
                        }
                        OutlinedButton(onClick = { viewModel.removeFolder(currentFolder.id) }, enabled = !refreshing) {
                            Text("移除")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.searchBooks(it) },
                        placeholder = { Text("搜索当前文件夹...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.clearSearch() }) {
                                    Icon(Icons.Default.Clear, contentDescription = "清除")
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    if (displayBooks.isEmpty()) {
                        Box(
                            Modifier.fillMaxSize().clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { focusManager.clearFocus() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (searchQuery.isBlank()) "暂无电子书，可点击刷新扫描。"
                                else "未找到匹配的电子书",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            items(displayBooks, key = { it.id }) { book ->
                                BookRow(book = book, onRead = { onReadLocalBook(book.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLocalState(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text("还没有本地文件夹", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "添加 SAF 文件夹后可浏览其中的电子书；不复制源文件。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = onAdd) { Text("添加文件夹") }
    }
}

@Composable
private fun BookRow(book: LocalBook, onRead: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.MenuBook, contentDescription = null)
        Spacer(Modifier.size(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(book.displayName, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Text(
                text = "${book.format.uppercase()} · ${(book.sizeBytes / 1024.0 / 1024.0).formatMb()} MB",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (book.format == "epub" || book.format == "pdf" || book.format == "txt") {
            OutlinedButton(onClick = onRead, modifier = Modifier.height(32.dp)) {
                Text("打开", style = MaterialTheme.typography.bodySmall)
            }
        } else {
            Text(
                "暂不支持",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun Double.formatMb(): String = if (this < 0.1) "<0.1" else String.format(java.util.Locale.US, "%.1f", this)
