package com.talebook.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.talebook.app.data.repository.RecentBookStore
import com.talebook.app.data.repository.SettingsRepository
import com.talebook.app.ui.components.BookCard
import com.talebook.app.viewmodel.HomeViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    contentPadding: PaddingValues = PaddingValues(),
    onBookClick: (Int) -> Unit,
    onNavigateSearch: () -> Unit,
    onNavigateLibrary: () -> Unit,
    autoRefreshOnEnter: Boolean = false,
    libraryEnterSignal: Int = 0,
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val settingsRepository = remember { SettingsRepository(context.applicationContext) }
    val servers by settingsRepository.libraryServers.collectAsState(initial = emptyList())
    val activeServerId by settingsRepository.activeLibraryServerId.collectAsState(initial = SettingsRepository.DEFAULT_SERVER_ID)
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var serverMenuExpanded by remember { mutableStateOf(false) }
    var localRecentBooks by remember { mutableStateOf(RecentBookStore.get(context)) }
    val activeServerName = remember(servers, activeServerId) {
        val server = servers.firstOrNull { it.id == activeServerId }
        server?.name?.takeIf { it.isNotBlank() }
            ?: server?.baseUrl?.removePrefix("https://")?.removePrefix("http://")?.substringBefore("/")?.substringBefore(":")
            ?: ""
    }

    LaunchedEffect(Unit) {
        viewModel.readCachedHome(context)
        viewModel.bindContext(context)
        localRecentBooks = RecentBookStore.get(context)
        val s = viewModel.uiState.value
        if (!s.serverConfigured || (s.readingBooks.isEmpty() && s.randomBooks.isEmpty() && s.newBooks.isEmpty())) {
            viewModel.load()
        }
    }

    LaunchedEffect(libraryEnterSignal) {
        if (libraryEnterSignal > 0 && autoRefreshOnEnter) {
            viewModel.forceRefresh()
        }
    }

    LaunchedEffect(uiState.readingBooks, uiState.shelfBooks, uiState.randomBooks, uiState.newBooks) {
        if (uiState.serverConfigured && !uiState.isLoading && !uiState.isRefreshing) {
            viewModel.writeCachedHome(context)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                localRecentBooks = RecentBookStore.get(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { serverMenuExpanded = true },
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text(
                            servers.firstOrNull { it.id == activeServerId }?.name ?: "书库",
                            maxLines = 1
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "切换书库")
                        DropdownMenu(
                            expanded = serverMenuExpanded,
                            onDismissRequest = { serverMenuExpanded = false }
                        ) {
                            servers.forEach { server ->
                                DropdownMenuItem(
                                    text = { Text(server.name.ifBlank { server.baseUrl }) },
                                    onClick = {
                                        serverMenuExpanded = false
                                        scope.launch {
                                            settingsRepository.setActiveLibraryServer(server.id)
                                            viewModel.load()
                                        }
                                    }
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.forceRefresh() },
                        enabled = uiState.serverConfigured && !uiState.isRefreshing
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            )
        }
    ) { padding ->
        val combinedPadding = PaddingValues(
            start = padding.calculateStartPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
            top = padding.calculateTopPadding(),
            end = padding.calculateEndPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
            bottom = padding.calculateBottomPadding() + contentPadding.calculateBottomPadding()
        )
when {
            !uiState.serverConfigured -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(combinedPadding).padding(24.dp),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        Text("尚未配置服务器", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "可进入设置添加服务器，或浏览本地书架。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            uiState.isLoading && uiState.readingBooks.isEmpty() && uiState.shelfBooks.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(combinedPadding),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null && uiState.readingBooks.isEmpty() && uiState.shelfBooks.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(combinedPadding).padding(24.dp),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        Text("加载失败", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(uiState.error ?: "", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.forceRefresh() }) {
                            Text("重试")
                        }
                    }
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(combinedPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp).clickable { onNavigateSearch() }
                    ) {
                        OutlinedTextField(
                            value = "",
                            onValueChange = {},
                            placeholder = { Text("搜索书库...") },
                            enabled = false,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                Icon(Icons.Default.Search, contentDescription = "搜索")
                            }
                        )
                    }

                    if (localRecentBooks.isNotEmpty()) {
                        Text(
                            text = "最近浏览",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(localRecentBooks) { book ->
                                BookCard(
                                    book = book,
                                    onClick = { onBookClick(book.id) }
                                )
                            }
                        }
                    }

                    if (uiState.readingBooks.isNotEmpty()) {
                        Text(
                            text = "最近阅读",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                    items(uiState.readingBooks) { book ->
                                BookCard(
                                    book = book,
                                    onClick = { onBookClick(book.id) }
                                )
                            }
                        }
                    }

                    if (uiState.shelfBooks.isNotEmpty()) {
                        Text(
                            text = "我的书架",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(uiState.shelfBooks) { book ->
                                BookCard(
                                    book = book,
                                    onClick = { onBookClick(book.id) }
                                )
                            }
                        }
                    }

                    if (uiState.randomBooks.isNotEmpty()) {
                        Text(
                            text = "随机推荐",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(uiState.randomBooks) { book ->
                                BookCard(
                                    book = book,
                                    onClick = { onBookClick(book.id) }
                                )
                            }
                        }
                    }

                    if (uiState.newBooks.isNotEmpty()) {
                        Text(
                            text = "最新上架",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(uiState.newBooks) { book ->
                                BookCard(
                                    book = book,
                                    onClick = { onBookClick(book.id) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedButton(
                        onClick = onNavigateLibrary,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("浏览全部书库")
                    }
                }
            }
        }
    }
}
