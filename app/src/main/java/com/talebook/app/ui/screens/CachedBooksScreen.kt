package com.talebook.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.talebook.app.data.repository.CacheJobTracker
import com.talebook.app.data.repository.ReaderCachedBook
import com.talebook.app.data.repository.ReaderCacheRepository
import kotlinx.coroutines.launch

@Composable
private fun ActiveCacheCard(
    job: CacheJobTracker.CacheJobInfo,
    onCancel: () -> Unit,
    onDetail: () -> Unit
) {
    val progress = if (job.totalBytes > 0) (job.downloadedBytes.toFloat() / job.totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
    val progressText = if (job.totalBytes > 0) "${(progress * 100).toInt()}%" else "?"
    val downloadedMb = job.downloadedBytes / 1024.0f / 1024.0f
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(job.title.ifBlank { "未命名图书" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (job.isRunning) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(6.dp))
            }
            Text(
                text = if (job.isRunning) {
                    "缓存中 $progressText ${String.format(java.util.Locale.US, "(%.1f MB)", downloadedMb)}"
                } else {
                    "等待中，最多同时缓存 2 本书"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                    Text("取消", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(onClick = onDetail, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                    Text("详情", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CachedBooksScreen(
    onBack: () -> Unit,
    onOpenBookDetail: (Int) -> Unit,
    onReadLocalBook: (Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cacheRepository = remember { ReaderCacheRepository() }
    var cachedBooks by remember { mutableStateOf<List<ReaderCachedBook>>(emptyList()) }
    var message by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    val activeJobs by CacheJobTracker.jobs.collectAsState()

    fun refresh() {
        scope.launch {
            isLoading = true
            cachedBooks = cacheRepository.cachedBooks(context.applicationContext)
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("已缓存图书") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        val hasActive = activeJobs.isNotEmpty()
        val hasFinished = cachedBooks.isNotEmpty()
        when {
            isLoading && !hasActive -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { Text("正在读取缓存...") }

            !hasActive && !hasFinished -> Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("暂无已缓存图书", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (message.isNotBlank()) {
                    item {
                        Text(message, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (hasActive) {
                    item {
                        Text("缓存中", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    }
                    activeJobs.values.forEach { job ->
                        item(key = "active_${job.bookId}") {
                            ActiveCacheCard(
                                job = job,
                                onCancel = {
                                    CacheJobTracker.cancel(job.bookId)
                                },
                                onDetail = { onOpenBookDetail(job.bookId) }
                            )
                        }
                    }
                    if (hasFinished) {
                        item { Spacer(modifier = Modifier.height(4.dp)) }
                    }
                }
                if (hasFinished) {
                    if (hasActive) {
                        item {
                            Text("已完成", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    items(cachedBooks, key = { it.bookId }) { item ->
                        CachedBookCard(
                            item = item,
                            onDelete = {
                                scope.launch {
                                    val removed = cacheRepository.deleteBookCache(context.applicationContext, item.bookId)
                                    message = "已删除 ${item.title.ifBlank { "本书" }} 缓存 ${(removed / 1024.0 / 1024.0).cacheMbText()} MB"
                                    cachedBooks = cacheRepository.cachedBooks(context.applicationContext)
                                }
                            },
                            onDetail = { onOpenBookDetail(item.bookId) },
                            onRead = { onReadLocalBook(item.bookId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CachedBookCard(
    item: ReaderCachedBook,
    onDelete: () -> Unit,
    onDetail: () -> Unit,
    onRead: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(item.title.ifBlank { "未命名图书" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = "${item.format.uppercase()} ${(item.sizeBytes / 1024.0 / 1024.0).cacheMbText()} MB",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("删除")
                }
                OutlinedButton(onClick = onDetail, modifier = Modifier.weight(1f)) {
                    Text("详情")
                }
                Button(onClick = onRead, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.MenuBook, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("阅读")
                }
            }
        }
    }
}

private fun Double.cacheMbText(): String = String.format(java.util.Locale.US, "%.1f", this)
