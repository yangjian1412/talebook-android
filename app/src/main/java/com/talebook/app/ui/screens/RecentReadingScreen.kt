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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.talebook.app.data.api.RetrofitClient
import com.talebook.app.util.resolveUrl
import com.talebook.app.viewmodel.RecentReadingItem
import com.talebook.app.viewmodel.RecentReadingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentReadingScreen(
    contentPadding: PaddingValues = PaddingValues(),
    refreshSignal: Int = 0,
    onBookClick: (Int) -> Unit,
    onReadBook: (Int) -> Unit,
    viewModel: RecentReadingViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var editMode by remember { mutableStateOf(false) }

    LaunchedEffect(refreshSignal) { viewModel.load() }

    Scaffold(
        modifier = Modifier.padding(contentPadding),
        topBar = {
            TopAppBar(
                title = { Text("最近阅读") },
                actions = {
                    if (uiState.items.isNotEmpty()) {
                        IconButton(onClick = {
                            editMode = !editMode
                            if (!editMode) viewModel.load()
                        }) {
                            Icon(
                                imageVector = if (editMode) Icons.Default.Check else Icons.Default.Edit,
                                contentDescription = if (editMode) "完成" else "编辑"
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            uiState.error != null -> Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                Text(uiState.error ?: "加载失败", color = MaterialTheme.colorScheme.error)
            }
            uiState.items.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                Text("还没有最近阅读，去书库找一本书开始读吧", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> {
                val items = uiState.items
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(items, key = { _, item -> item.bookId }) { index, item ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            RecentReadingBookRow(
                                item = item,
                                editMode = editMode,
                                canMoveUp = index > 0 && items[index - 1].pinned == item.pinned,
                                canMoveDown = index < items.lastIndex && items[index + 1].pinned == item.pinned,
                                onDetail = { onBookClick(item.bookId) },
                                onRead = { onReadBook(item.bookId) },
                                onTogglePinned = { viewModel.togglePinned(item) },
                                onMoveToTop = { viewModel.moveToTop(item) },
                                onMoveUp = { viewModel.moveUp(item) },
                                onMoveDown = { viewModel.moveDown(item) },
                                onDelete = { viewModel.remove(item) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentReadingBookRow(
    item: RecentReadingItem,
    editMode: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onDetail: () -> Unit,
    onRead: () -> Unit,
    onTogglePinned: () -> Unit,
    onMoveToTop: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val coverHeight = 108.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (editMode) 0.dp else 8.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (editMode) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TextButton(onClick = onMoveToTop, modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Text("移顶", style = MaterialTheme.typography.labelSmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "上移", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "下移", modifier = Modifier.size(18.dp))
                    }
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "删除", modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.width(6.dp))
        }
        val coverUrl = resolveUrl(item.cover)
        if (coverUrl.isNotBlank()) {
            val cookie = RetrofitClient.cookieHeader()
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(coverUrl)
                    .apply { if (cookie.isNotBlank()) addHeader("Cookie", cookie) }
                    .build(),
                contentDescription = item.title,
                modifier = Modifier
                    .size(width = 72.dp, height = coverHeight)
                    .clip(MaterialTheme.shapes.small),
                contentScale = ContentScale.FillBounds
            )
        } else {
            Box(Modifier.size(width = 72.dp, height = coverHeight), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Book, contentDescription = null)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onTogglePinned, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = if (item.pinned) "取消置顶" else "置顶",
                        modifier = Modifier.size(18.dp),
                        tint = if (item.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                    )
                }
            }
            Text(
                item.author,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onDetail,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f)
                ) { Text("详情", style = MaterialTheme.typography.labelSmall) }
                Button(
                    onClick = onRead,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "阅读 ${item.progression.toPercentText()}",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

private fun Double.toPercentText(): String = "${(this.coerceIn(0.0, 1.0) * 100).toInt()}%"
