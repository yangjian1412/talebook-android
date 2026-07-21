package com.talebook.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import com.talebook.app.ui.components.BookCard
import com.talebook.app.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onBookClick: (Int) -> Unit,
    onNavigateSearch: () -> Unit,
    onNavigateLibrary: () -> Unit,
    onNavigateSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var localRecentBooks by remember { mutableStateOf(RecentBookStore.get(context)) }

    LaunchedEffect(Unit) {
        viewModel.load()
        localRecentBooks = RecentBookStore.get(context)
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
                title = { Text("Tale Book") },
                actions = {
                    IconButton(onClick = onNavigateSearch) {
                        Icon(Icons.Default.Search, contentDescription = "搜索")
                    }
                    IconButton(onClick = onNavigateSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                }
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        Text("加载失败", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(uiState.error ?: "", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.load() }) {
                            Text("重试")
                        }
                    }
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                ) {
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
