package com.talebook.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
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
import com.talebook.app.data.local.ReaderAnnotationEntity
import com.talebook.app.data.local.ReaderDatabase
import com.talebook.app.data.repository.ReaderBackupRepository
import com.talebook.app.data.repository.ReaderNoteBookSummary
import com.talebook.app.data.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesManagementScreen(
    onBack: () -> Unit,
    onOpenReaderAtNote: (Int, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { ReaderBackupRepository() }
    var summaries by remember { mutableStateOf<List<ReaderNoteBookSummary>>(emptyList()) }
    var selectedBook by remember { mutableStateOf<ReaderNoteBookSummary?>(null) }
    var annotations by remember { mutableStateOf<List<ReaderAnnotationEntity>>(emptyList()) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var isLoading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf("") }

    fun refreshSummaries() {
        scope.launch {
            isLoading = true
            runCatching { repository.noteBookSummaries(context.applicationContext) }
                .onSuccess {
                    summaries = it
                    message = ""
                }
                .onFailure {
                    summaries = emptyList()
                    message = it.message ?: "读取笔记失败"
                }
            isLoading = false
        }
    }

    fun refreshAnnotations(bookId: Int) {
        scope.launch {
            runCatching {
                val serverId = SettingsRepository(context.applicationContext).activeLibraryServerId.first()
                ReaderDatabase.get(context.applicationContext).readerDao().getAnnotations(serverId, bookId)
            }.onSuccess {
                annotations = it
                message = ""
            }.onFailure {
                annotations = emptyList()
                message = it.message ?: "读取本书笔记失败"
            }
            selectedIds = emptySet()
        }
    }

    LaunchedEffect(Unit) { refreshSummaries() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(selectedBook?.title ?: "笔记管理", maxLines = 1) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (selectedBook != null) {
                                selectedBook = null
                                selectedIds = emptySet()
                                refreshSummaries()
                            } else {
                                onBack()
                            }
                        }
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        if (selectedBook == null) {
            when {
                isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("正在读取笔记...")
                }
                summaries.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(message.ifBlank { "暂无笔记" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (message.isNotBlank()) {
                        item { Text(message, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
                    }
                    items(summaries, key = { it.bookId }) { item ->
                        NoteBookCard(
                            item = item,
                            onExport = {
                                scope.launch {
                                    repository.exportNotesMarkdownForBook(context.applicationContext, item.bookId).fold(
                                        onSuccess = { message = it },
                                        onFailure = { message = it.message ?: "导出失败" }
                                    )
                                }
                            },
                            onManage = {
                                selectedBook = item
                                refreshAnnotations(item.bookId)
                            }
                        )
                    }
                }
            }
        } else {
            val book = selectedBook!!
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                repository.exportNotesMarkdownForBook(context.applicationContext, book.bookId).fold(
                                    onSuccess = { message = it },
                                    onFailure = { message = it.message ?: "导出失败" }
                                )
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("导出本书") }
                    Button(
                        onClick = {
                            scope.launch {
                                repository.deleteAnnotations(context.applicationContext, selectedIds.toList())
                                message = "已删除 ${selectedIds.size} 条笔记"
                                refreshAnnotations(book.bookId)
                            }
                        },
                        enabled = selectedIds.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) { Text("删除选中") }
                }
                if (message.isNotBlank()) {
                    Text(message, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(annotations, key = { it.id }) { annotation ->
                        NoteManageCard(
                            annotation = annotation,
                            selected = annotation.id in selectedIds,
                            onSelectedChange = { checked ->
                                selectedIds = if (checked) selectedIds + annotation.id else selectedIds - annotation.id
                            },
                            onJump = { onOpenReaderAtNote(book.bookId, annotation.locatorJson) },
                            onDelete = {
                                scope.launch {
                                    repository.deleteAnnotations(context.applicationContext, listOf(annotation.id))
                                    refreshAnnotations(book.bookId)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteBookCard(
    item: ReaderNoteBookSummary,
    onExport: () -> Unit,
    onManage: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(item.title.ifBlank { "未命名图书" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("${item.noteCount} 条笔记 · 更新 ${item.updatedAt.dateText()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) { Text("导出") }
                Button(onClick = onManage, modifier = Modifier.weight(1f)) { Text("管理") }
            }
        }
    }
}

@Composable
private fun NoteManageCard(
    annotation: ReaderAnnotationEntity,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    onJump: () -> Unit,
    onDelete: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = selected, onCheckedChange = onSelectedChange)
            Column(modifier = Modifier.weight(1f)) {
                Text(annotation.selectedText.ifBlank { "当前位置" }, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
                if (annotation.note.isNotBlank()) {
                    Text(annotation.note, maxLines = 3, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(annotation.updatedAt.dateText(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onJump) { Text("跳转") }
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("删除")
                    }
                }
            }
        }
    }
}

private fun Long.dateText(): String = if (this <= 0L) {
    "未知"
} else {
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(this))
}
