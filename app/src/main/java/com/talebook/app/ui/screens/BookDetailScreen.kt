package com.talebook.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.talebook.app.data.api.RetrofitClient
import com.talebook.app.data.model.Book
import com.talebook.app.data.model.coverPath
import com.talebook.app.data.repository.RecentBookStore
import com.talebook.app.data.repository.SettingsRepository
import com.talebook.app.util.resolveUrl
import com.talebook.app.viewmodel.BookDetailViewModel
import com.talebook.app.viewmodel.DownloadState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    bookId: Int,
    onBack: () -> Unit,
    onRead: (Int) -> Unit,
    viewModel: BookDetailViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val storagePermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.download(context)
    }

    LaunchedEffect(bookId) {
        viewModel.loadBook(bookId)
    }

    LaunchedEffect(uiState.book?.id) {
        val loadedBookId = uiState.book?.id ?: return@LaunchedEffect
        viewModel.loadLocalReaderInfo(context.applicationContext, loadedBookId)
    }

    DisposableEffect(lifecycleOwner, uiState.book?.id) {
        val observer = LifecycleEventObserver { _, event ->
            val loadedBookId = uiState.book?.id ?: return@LifecycleEventObserver
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadLocalReaderInfo(context.applicationContext, loadedBookId)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(uiState.book?.id) {
        val b = uiState.book ?: return@LaunchedEffect
        RecentBookStore.add(
            context,
            Book(
                id = b.id,
                title = b.title,
                authorSort = b.authorSort,
                authors = b.authors,
                publisher = b.publisher,
                comments = b.comments,
                pubdate = b.pubdate,
                cover = b.cover,
                img = b.img,
                thumb = b.thumb,
                timestamp = b.timestamp
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.book?.title ?: "书籍详情", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(uiState.error ?: "加载失败")
                }
            }
            uiState.book != null -> {
                val book = uiState.book!!
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (book.coverPath().isNotBlank()) {
                            val cookie = RetrofitClient.cookieHeader()
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(resolveUrl(book.coverPath()))
                                    .apply { if (cookie.isNotBlank()) addHeader("Cookie", cookie) }
                                    .build(),
                                contentDescription = book.title,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Icon(
                                Icons.Default.Book,
                                contentDescription = null,
                                modifier = Modifier.size(96.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = book.title,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = book.authorSort.ifBlank { book.authors.joinToString(", ") },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (book.publisher.isNotBlank() || book.pubdate.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                if (book.publisher.isNotBlank()) {
                                    Text(
                                        text = book.publisher,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (book.pubdate.isNotBlank()) {
                                    Text(
                                        text = book.pubdate,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        if (book.tags.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                book.tags.forEach { tag ->
                                    SuggestionChip(
                                        onClick = {},
                                        label = { Text(tag, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                        }

                        if (book.comments.isNotBlank()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "简介",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = book.comments,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        LocalReaderInfoCard(
                            progression = uiState.localProgression,
                            isCached = uiState.cacheInfo.isCached,
                            cacheFormat = uiState.cacheInfo.format,
                            cacheSizeBytes = uiState.cacheInfo.sizeBytes,
                            cacheBusy = uiState.cacheBusy,
                            cacheProgressBytes = uiState.cacheProgressBytes,
                            cacheTotalBytes = uiState.cacheTotalBytes,
                            cacheAction = uiState.cacheCurrentAction,
                            cacheMessage = uiState.cacheMessage,
                            onDeleteCache = { viewModel.deleteLocalCache(context.applicationContext) },
                            onStartCache = { viewModel.startLocalCache(context.applicationContext) },
                            onCancelCache = { viewModel.cancelLocalCache() }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { onRead(book.id) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.MenuBook, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("阅读")
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = { viewModel.toggleShelf() },
                            enabled = !uiState.shelfBusy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (uiState.shelfBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("处理中...")
                            } else {
                                Icon(
                                    imageVector = if (uiState.inShelf) {
                                        Icons.Default.Bookmark
                                    } else {
                                        Icons.Default.BookmarkBorder
                                    },
                                    contentDescription = null
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (uiState.inShelf) "移出书架" else "加入书架")
                            }
                        }

                        if (uiState.shelfError != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = uiState.shelfError!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        DownloadButton(
                            state = uiState.downloadState,
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    viewModel.download(context)
                                } else {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) {
                                        viewModel.download(context)
                                    } else {
                                        storagePermLauncher.launch(
                                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                                        )
                                    }
                                }
                            }
                        )

                        if (uiState.downloadState is DownloadState.Failed) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = (uiState.downloadState as DownloadState.Failed).message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        if (uiState.downloadState is DownloadState.Done) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "已保存到 ${(uiState.downloadState as DownloadState.Done).relativePath}/",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (uiState.readState != null && uiState.readState!!.percentage > 0) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "已读 ${(uiState.readState!!.percentage * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            LinearProgressIndicator(
                                progress = { uiState.readState!!.percentage.toFloat() },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalReaderInfoCard(
    progression: Double,
    isCached: Boolean,
    cacheFormat: String,
    cacheSizeBytes: Long,
    cacheBusy: Boolean,
    cacheProgressBytes: Long,
    cacheTotalBytes: Long,
    cacheAction: String,
    cacheMessage: String,
    onDeleteCache: () -> Unit,
    onStartCache: () -> Unit,
    onCancelCache: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("本地阅读", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text("阅读进度 ${(progression * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(progress = { progression.toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            if (isCached) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "已缓存 ${cacheFormat.uppercase()} ${(cacheSizeBytes / 1024.0 / 1024.0).formatMb()} MB",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (cacheBusy) {
                        OutlinedButton(onClick = onCancelCache, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                            Text("取消", style = MaterialTheme.typography.labelSmall)
                        }
                    } else {
                        OutlinedButton(onClick = onDeleteCache, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("删除", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                if (cacheBusy) {
                    val progress = if (cacheTotalBytes > 0) (cacheProgressBytes.toFloat() / cacheTotalBytes.toFloat()).coerceIn(0f, 1f) else 0f
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Text("${cacheAction.ifBlank { "缓存中" }} ${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("未缓存", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (cacheBusy) {
                        OutlinedButton(onClick = onCancelCache, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                            Text("取消", style = MaterialTheme.typography.labelSmall)
                        }
                    } else {
                        Button(onClick = onStartCache, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("缓存", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                if (cacheBusy) {
                    val progress = if (cacheTotalBytes > 0) (cacheProgressBytes.toFloat() / cacheTotalBytes.toFloat()).coerceIn(0f, 1f) else 0f
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Text("${cacheAction.ifBlank { "缓存中" }} ${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (cacheMessage.isNotBlank()) {
                Text(cacheMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private fun Double.formatMb(): String = String.format(java.util.Locale.US, "%.1f", this)

@Composable
private fun DownloadButton(
    state: DownloadState,
    onClick: () -> Unit
) {
    when (state) {
        is DownloadState.Idle -> OutlinedButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Download, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("下载")
        }
        is DownloadState.Downloading -> OutlinedButton(
            onClick = {},
            enabled = false,
            modifier = Modifier.fillMaxWidth()
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
            val pct = if (state.totalBytes > 0) {
                (state.downloadedBytes * 100 / state.totalBytes).toInt()
            } else -1
            val downloadedMb = state.downloadedBytes / 1024.0 / 1024.0
            if (pct >= 0) {
                Text(
                    text = String.format("下载中 %d%% (%.1f MB)", pct, downloadedMb),
                    maxLines = 1
                )
            } else {
                Text(
                    text = String.format("下载中 %.1f MB", downloadedMb),
                    maxLines = 1
                )
            }
        }
        is DownloadState.Done -> OutlinedButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "已下载 (再次点击重下)",
                maxLines = 1
            )
        }
        is DownloadState.Failed -> OutlinedButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.width(8.dp))
            Text("重试下载")
        }
    }
}
