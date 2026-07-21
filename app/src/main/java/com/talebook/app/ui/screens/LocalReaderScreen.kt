package com.talebook.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.talebook.app.reader.ReadiumHostFragment
import com.talebook.app.reader.ReadiumUiEvents
import com.talebook.app.reader.ReaderFontFamily
import com.talebook.app.reader.ReaderPageAnimation
import com.talebook.app.reader.ReaderPageTurnMode
import com.talebook.app.reader.ReaderTheme
import com.talebook.app.data.local.ReaderAnnotationEntity
import com.talebook.app.data.repository.SettingsRepository
import com.talebook.app.viewmodel.LocalReaderViewModel
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalReaderScreen(
    bookId: Int,
    onBack: () -> Unit,
    viewModel: LocalReaderViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val settingsRepository = remember(context.applicationContext) { SettingsRepository(context.applicationContext) }
    val appThemeMode by settingsRepository.themeMode.collectAsState(initial = SettingsRepository.THEME_AUTO)
    val systemDark = isSystemInDarkTheme()
    var barsVisible by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var showBookmarksDialog by remember { mutableStateOf(false) }
    var showTocDialog by remember { mutableStateOf(false) }
    var showNotesDialog by remember { mutableStateOf(false) }
    var editingAnnotation by remember { mutableStateOf<ReaderAnnotationEntity?>(null) }
    var editNoteText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showAdvancedSettingsDialog by remember { mutableStateOf(false) }
    var showProgressJumpDialog by remember { mutableStateOf(false) }
    var progressJumpText by remember { mutableStateOf("") }
    var pageJumpText by remember { mutableStateOf("") }
    var progression by remember { mutableStateOf(0.0) }
    var locatorBeforeChrome by remember { mutableStateOf<String?>(null) }
    val readerSettings = uiState.readerSettings
    val activity = context.findFragmentActivity()
    val leaveReader = remember(activity, onBack) {
        {
            activity?.showSystemBarsAfterReader()
            onBack()
        }
    }

    DisposableEffect(activity) {
        onDispose { activity?.showSystemBarsAfterReader() }
    }

    LaunchedEffect(bookId) {
        viewModel.load(context.applicationContext, bookId)
    }

    LaunchedEffect(appThemeMode, systemDark, uiState.sessionId) {
        if (uiState.sessionId == null) return@LaunchedEffect
        val appDark = when (appThemeMode) {
            SettingsRepository.THEME_LIGHT -> false
            SettingsRepository.THEME_DARK -> true
            else -> systemDark
        }
        viewModel.updateAppDark(appDark)
    }

    LaunchedEffect(uiState.sessionId) {
        val sessionId = uiState.sessionId ?: return@LaunchedEffect
        ReadiumUiEvents.centerTaps.collect { (tappedSessionId, locatorJson) ->
            if (tappedSessionId == sessionId) {
                locatorBeforeChrome = locatorJson
                barsVisible = !barsVisible
            }
        }
    }

    LaunchedEffect(barsVisible, locatorBeforeChrome, uiState.sessionId) {
        val sessionId = uiState.sessionId ?: return@LaunchedEffect
        val locatorJson = locatorBeforeChrome ?: return@LaunchedEffect
        ReadiumUiEvents.emitGoToLocator(sessionId, locatorJson)
    }

    LaunchedEffect(uiState.sessionId) {
        val sessionId = uiState.sessionId ?: return@LaunchedEffect
        ReadiumUiEvents.bookmarkAdded.collect { targetSessionId ->
            if (targetSessionId == sessionId) viewModel.refreshBookmarks()
        }
    }

    LaunchedEffect(uiState.sessionId) {
        val sessionId = uiState.sessionId ?: return@LaunchedEffect
        ReadiumUiEvents.annotationAdded.collect { targetSessionId ->
            if (targetSessionId == sessionId) viewModel.refreshAnnotations()
        }
    }

    LaunchedEffect(uiState.sessionId) {
        val sessionId = uiState.sessionId ?: return@LaunchedEffect
        ReadiumUiEvents.progress.collect { (targetSessionId, value) ->
            if (targetSessionId == sessionId) progression = value
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                uiState.isLoading -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(uiState.statusMessage.ifBlank { "准备本地阅读器..." })
                    }
                }
                uiState.error != null -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(uiState.error ?: "加载失败", color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.load(context.applicationContext, bookId) }) {
                            Text("重试")
                        }
                    }
                }
                else -> {
                    if (uiState.sessionId != null) {
                        val containerId = remember(uiState.sessionId) { android.view.View.generateViewId() }
                        if (activity == null) {
                            Text("无法获取 FragmentActivity", color = MaterialTheme.colorScheme.error)
                        } else {
                            AndroidView(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(
                                        PaddingValues(
                                            top = if (barsVisible) 64.dp else 0.dp,
                                            bottom = if (barsVisible) 74.dp else 0.dp
                                        )
                                    ),
                                factory = { ctx ->
                                    FrameLayout(ctx).apply { id = containerId }
                                },
                                update = {
                                    val tag = ReadiumHostFragment.tag(uiState.sessionId!!)
                                    if (activity.supportFragmentManager.findFragmentByTag(tag) == null) {
                                        activity.supportFragmentManager.beginTransaction()
                                            .replace(containerId, ReadiumHostFragment.newInstance(uiState.sessionId!!), tag)
                                            .commitNowAllowingStateLoss()
                                    }
                                }
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.MenuBook,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Readium 本地阅读器无法打开内容",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = uiState.statusMessage,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = { viewModel.cacheCurrentBook(context.applicationContext) },
                                enabled = !uiState.isCached && !uiState.isCaching
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null)
                                Text(
                                    when {
                                        uiState.isCached -> "已缓存"
                                        uiState.isCaching -> "缓存中..."
                                        else -> "缓存本书"
                                    }
                                )
                            }
                        }
                    }
                }
            }
            if (barsVisible && uiState.sessionId != null) {
                ReaderBottomBar(
                    progression = progression,
                    theme = readerSettings.theme,
                    onToc = { showTocDialog = true },
                    onTheme = { viewModel.cycleReaderTheme() },
                    onSettings = { showSettingsDialog = true },
                    onNote = { showNotesDialog = true },
                    onTts = { viewModel.startTts() },
                    onProgressClick = {
                        progressJumpText = ((progression * 100).toInt()).toString()
                        pageJumpText = ""
                        showProgressJumpDialog = true
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                )
            }
            if (!barsVisible && uiState.sessionId != null) {
                ReaderProgressOverlay(
                    progression = progression,
                    onClick = {
                        progressJumpText = ((progression * 100).toInt()).toString()
                        pageJumpText = ""
                        showProgressJumpDialog = true
                    },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
        if (barsVisible) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
            ) {
                TopAppBar(
                    title = { Text(uiState.title.ifBlank { "本地阅读器" }, maxLines = 1) },
                    navigationIcon = {
                        IconButton(onClick = leaveReader) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showBookmarksDialog = true }) {
                            Icon(Icons.Default.BookmarkAdd, contentDescription = "书签")
                        }
                        IconButton(onClick = { showSearchDialog = true }) {
                            Icon(Icons.Default.Search, contentDescription = "搜索")
                        }
                        IconButton(
                            onClick = { viewModel.cacheCurrentBook(context.applicationContext) },
                            enabled = !uiState.isCached && !uiState.isCaching
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = "缓存本书")
                        }
                    }
                )
            }
        }
        if (uiState.ttsState.isPlaying || uiState.ttsState.isPaused) {
            FloatingTtsBar(
                text = uiState.ttsState.currentText,
                status = uiState.ttsState.status,
                isPlaying = uiState.ttsState.isPlaying,
                onToggle = { if (uiState.ttsState.isPlaying) viewModel.pauseTts() else viewModel.resumeTts() },
                onPrevious = { viewModel.previousTtsSentence() },
                onNext = { viewModel.nextTtsSentence() },
                onOpenPanel = { viewModel.startTts() },
                onCloseTts = { viewModel.exitTts() },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (barsVisible) 72.dp else 16.dp, start = 12.dp, end = 12.dp)
            )
        }
    }

    if (showSearchDialog) {
        AlertDialog(
            onDismissRequest = { showSearchDialog = false },
            title = { Text("搜索书内内容") },
            text = {
                Column {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("关键词") },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.searchInBook(searchQuery) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (uiState.isSearching) "搜索中..." else "搜索") }
                    Spacer(modifier = Modifier.height(8.dp))
                    when {
                        uiState.isSearching -> Text("正在搜索...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        uiState.searchResults.isEmpty() -> Text("暂无结果", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else -> LazyColumn(modifier = Modifier.height(320.dp)) {
                            items(uiState.searchResults) { result ->
                                TextButton(
                                    onClick = {
                                        viewModel.goToSearchResult(result)
                                        showSearchDialog = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text(result.text)
                                        Text(
                                            text = "${(result.progression * 100).toInt()}%",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSearchDialog = false }) { Text("确定") }
            }
        )
    }

    if (uiState.ttsState.isPanelVisible) {
        var voicesExpanded by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { viewModel.hideTtsPanel() },
            title = { Text("朗读") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = uiState.ttsState.currentText.ifBlank { uiState.ttsState.status.ifBlank { "从当前位置开始朗读" } },
                        maxLines = 4
                    )
                    Text(
                        text = when {
                            uiState.ttsState.isLoading -> "正在准备..."
                            uiState.ttsState.total > 0 -> "${uiState.ttsState.currentIndex}/${uiState.ttsState.total} · ${uiState.ttsState.status}"
                            else -> uiState.ttsState.status
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                if (uiState.ttsState.isPlaying) viewModel.pauseTts() else viewModel.resumeTts()
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !uiState.ttsState.isLoading
                        ) { Text(if (uiState.ttsState.isPlaying) "暂停" else "开始") }
                        OutlinedButton(onClick = { viewModel.stopTts() }, modifier = Modifier.weight(1f)) { Text("停止") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { viewModel.previousTtsSentence() }, modifier = Modifier.weight(1f)) { Text("上一句") }
                        OutlinedButton(onClick = { viewModel.nextTtsSentence() }, modifier = Modifier.weight(1f)) { Text("下一句") }
                    }
                    Text("语速 ${String.format(java.util.Locale.US, "%.1f", uiState.ttsState.speechRate)}")
                    CompactSlider(
                        value = uiState.ttsState.speechRate,
                        onValueChange = { viewModel.updateTtsRate(it) },
                        valueRange = 0.5f..2.0f
                    )
                    Text("音调 ${String.format(java.util.Locale.US, "%.1f", uiState.ttsState.pitch)}")
                    CompactSlider(
                        value = uiState.ttsState.pitch,
                        onValueChange = { viewModel.updateTtsPitch(it) },
                        valueRange = 0.5f..2.0f
                    )
                    Box {
                        OutlinedButton(onClick = { voicesExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(uiState.ttsState.voiceName.ifBlank { "系统默认语音" }, maxLines = 1)
                        }
                        DropdownMenu(expanded = voicesExpanded, onDismissRequest = { voicesExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("系统默认语音") },
                                onClick = {
                                    viewModel.updateTtsVoice("")
                                    voicesExpanded = false
                                }
                            )
                            uiState.ttsState.voices.forEach { voice ->
                                DropdownMenuItem(
                                    text = { Text(voice, maxLines = 1) },
                                    onClick = {
                                        viewModel.updateTtsVoice(voice)
                                        voicesExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("睡眠模式")
                        CompactSwitch(
                            checked = uiState.ttsState.sleepEnabled,
                            onCheckedChange = { viewModel.updateTtsSleep(it) }
                        )
                    }
                    if (uiState.ttsState.sleepEnabled) {
                        Text("${uiState.ttsState.sleepMinutes} 分钟后自动停止")
                        CompactSlider(
                            value = uiState.ttsState.sleepMinutes.toFloat(),
                            onValueChange = { viewModel.updateTtsSleep(true, it.toInt()) },
                            valueRange = 5f..180f
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.hideTtsPanel() }) { Text("收起") }
            }
        )
    }

    if (showProgressJumpDialog) {
        AlertDialog(
            onDismissRequest = { showProgressJumpDialog = false },
            title = { Text("跳转进度") },
            text = {
                Column {
                    OutlinedTextField(
                        value = progressJumpText,
                        onValueChange = { progressJumpText = it.filter { ch -> ch.isDigit() }.take(3) },
                        label = { Text("百分比 0-100") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pageJumpText,
                        onValueChange = { pageJumpText = it.filter { ch -> ch.isDigit() }.take(5) },
                        label = { Text(if (uiState.format.equals("pdf", ignoreCase = true)) "页码，共 ${uiState.pageCount.coerceAtLeast(1)} 页" else "阅读顺序编号，共 ${uiState.pageCount.coerceAtLeast(1)} 段") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("优先使用页码/编号；留空时按百分比跳转。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val page = pageJumpText.toIntOrNull()
                        if (page != null) {
                            viewModel.goToPage(page)
                        } else {
                            val percent = progressJumpText.toIntOrNull()?.coerceIn(0, 100) ?: 0
                            viewModel.goToProgress(percent / 100.0)
                        }
                        showProgressJumpDialog = false
                    }
                ) { Text("跳转") }
            },
            dismissButton = {
                TextButton(onClick = { showProgressJumpDialog = false }) { Text("取消") }
            }
        )
    }

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("基本设置")
                    TextButton(
                        onClick = {
                            showSettingsDialog = false
                            showAdvancedSettingsDialog = true
                        }
                    ) { Text("高级") }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("字号 ${(readerSettings.fontScale * 100).toInt()}%")
                        TextButton(
                            onClick = {
                                viewModel.updateReaderSettings(
                                    fontScale = 1.0f,
                                    lineHeight = readerSettings.lineHeight,
                                    brightness = readerSettings.brightness,
                                    scrollMode = readerSettings.scrollMode,
                                    useSystemBrightness = readerSettings.useSystemBrightness,
                                    theme = readerSettings.theme,
                                    tapPageTurn = readerSettings.tapPageTurn
                                )
                            }
                        ) { Text("默认") }
                    }
                    CompactSlider(
                        value = readerSettings.fontScale,
                        onValueChange = {
                            viewModel.updateReaderSettings(
                                fontScale = it,
                                lineHeight = readerSettings.lineHeight,
                                brightness = readerSettings.brightness,
                                scrollMode = readerSettings.scrollMode,
                                useSystemBrightness = readerSettings.useSystemBrightness,
                                theme = readerSettings.theme,
                                tapPageTurn = readerSettings.tapPageTurn
                            )
                        },
                        valueRange = 0.7f..1.8f
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("行距 ${String.format(java.util.Locale.US, "%.1f", readerSettings.lineHeight)}")
                        TextButton(
                            onClick = {
                                viewModel.updateReaderSettings(readerSettings.fontScale, 1.5f, readerSettings.brightness, readerSettings.scrollMode, readerSettings.useSystemBrightness, readerSettings.theme, readerSettings.tapPageTurn)
                            }
                        ) { Text("默认") }
                    }
                    CompactSlider(
                        value = readerSettings.lineHeight,
                        onValueChange = {
                            viewModel.updateReaderSettings(readerSettings.fontScale, it, readerSettings.brightness, readerSettings.scrollMode, readerSettings.useSystemBrightness, readerSettings.theme, readerSettings.tapPageTurn)
                        },
                        valueRange = 1.0f..2.4f
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("页边距 ${String.format(java.util.Locale.US, "%.1f", readerSettings.pageMargins)}")
                        TextButton(
                            onClick = {
                                viewModel.updateReaderSettings(readerSettings.fontScale, readerSettings.lineHeight, readerSettings.brightness, readerSettings.scrollMode, readerSettings.useSystemBrightness, readerSettings.theme, readerSettings.tapPageTurn, pageMargins = 1.0f)
                            }
                        ) { Text("默认") }
                    }
                    CompactSlider(
                        value = readerSettings.pageMargins,
                        onValueChange = {
                            viewModel.updateReaderSettings(readerSettings.fontScale, readerSettings.lineHeight, readerSettings.brightness, readerSettings.scrollMode, readerSettings.useSystemBrightness, readerSettings.theme, readerSettings.tapPageTurn, pageMargins = it)
                        },
                        valueRange = 0.5f..2.0f
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("亮度跟随系统")
                        CompactSwitch(
                            checked = readerSettings.useSystemBrightness,
                            onCheckedChange = {
                                viewModel.updateReaderSettings(fontScale = readerSettings.fontScale, lineHeight = readerSettings.lineHeight, brightness = readerSettings.brightness, scrollMode = readerSettings.scrollMode, useSystemBrightness = it, theme = readerSettings.theme, tapPageTurn = readerSettings.tapPageTurn)
                            }
                        )
                    }
                    if (!readerSettings.useSystemBrightness) {
                        Text("亮度 ${(readerSettings.brightness * 100).toInt()}%")
                        CompactSlider(
                            value = readerSettings.brightness,
                            onValueChange = {
                                viewModel.updateReaderSettings(fontScale = readerSettings.fontScale, lineHeight = readerSettings.lineHeight, brightness = it, scrollMode = readerSettings.scrollMode, useSystemBrightness = readerSettings.useSystemBrightness, theme = readerSettings.theme, tapPageTurn = readerSettings.tapPageTurn)
                            },
                            valueRange = 0.3f..1.0f
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("滚动模式")
                        CompactSwitch(
                            checked = readerSettings.scrollMode,
                            onCheckedChange = {
                                viewModel.updateReaderSettings(fontScale = readerSettings.fontScale, lineHeight = readerSettings.lineHeight, brightness = readerSettings.brightness, scrollMode = it, useSystemBrightness = readerSettings.useSystemBrightness, theme = readerSettings.theme, tapPageTurn = readerSettings.tapPageTurn)
                            }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("屏幕常亮")
                        CompactSwitch(
                            checked = readerSettings.keepScreenOn,
                            onCheckedChange = {
                                viewModel.updateReaderSettings(fontScale = readerSettings.fontScale, lineHeight = readerSettings.lineHeight, brightness = readerSettings.brightness, scrollMode = readerSettings.scrollMode, useSystemBrightness = readerSettings.useSystemBrightness, theme = readerSettings.theme, tapPageTurn = readerSettings.tapPageTurn, keepScreenOn = it)
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("主题")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        ReaderThemeChip("跟随", readerSettings.theme == ReaderTheme.SYSTEM) { viewModel.updateReaderTheme(ReaderTheme.SYSTEM) }
                        ReaderThemeChip("白色", readerSettings.theme == ReaderTheme.LIGHT) { viewModel.updateReaderTheme(ReaderTheme.LIGHT) }
                        ReaderThemeChip("护眼", readerSettings.theme == ReaderTheme.SEPIA) { viewModel.updateReaderTheme(ReaderTheme.SEPIA) }
                        ReaderThemeChip("夜间", readerSettings.theme == ReaderTheme.DARK) { viewModel.updateReaderTheme(ReaderTheme.DARK) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettingsDialog = false }) { Text("完成") }
            }
        )
    }

    if (showAdvancedSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showAdvancedSettingsDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("高级设置")
                    TextButton(
                        onClick = {
                            showAdvancedSettingsDialog = false
                            showSettingsDialog = true
                        }
                    ) { Text("基本") }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("字体")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        ReaderOptionChip("默认", readerSettings.fontFamily == ReaderFontFamily.DEFAULT) {
                            viewModel.updateReaderSettings(readerSettings.fontScale, readerSettings.lineHeight, readerSettings.brightness, readerSettings.scrollMode, readerSettings.useSystemBrightness, readerSettings.theme, readerSettings.tapPageTurn, fontFamily = ReaderFontFamily.DEFAULT)
                        }
                        ReaderOptionChip("衬线", readerSettings.fontFamily == ReaderFontFamily.SERIF) {
                            viewModel.updateReaderSettings(readerSettings.fontScale, readerSettings.lineHeight, readerSettings.brightness, readerSettings.scrollMode, readerSettings.useSystemBrightness, readerSettings.theme, readerSettings.tapPageTurn, fontFamily = ReaderFontFamily.SERIF)
                        }
                        ReaderOptionChip("无衬线", readerSettings.fontFamily == ReaderFontFamily.SANS_SERIF) {
                            viewModel.updateReaderSettings(readerSettings.fontScale, readerSettings.lineHeight, readerSettings.brightness, readerSettings.scrollMode, readerSettings.useSystemBrightness, readerSettings.theme, readerSettings.tapPageTurn, fontFamily = ReaderFontFamily.SANS_SERIF)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("音量键翻页")
                        CompactSwitch(
                            checked = readerSettings.volumeKeyPageTurn,
                            onCheckedChange = {
                                viewModel.updateReaderSettings(readerSettings.fontScale, readerSettings.lineHeight, readerSettings.brightness, readerSettings.scrollMode, readerSettings.useSystemBrightness, readerSettings.theme, readerSettings.tapPageTurn, volumeKeyPageTurn = it)
                            }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("段间距 ${String.format(java.util.Locale.US, "%.1f", readerSettings.paragraphSpacing)}")
                        TextButton(
                            onClick = {
                                viewModel.updateReaderSettings(readerSettings.fontScale, readerSettings.lineHeight, readerSettings.brightness, readerSettings.scrollMode, readerSettings.useSystemBrightness, readerSettings.theme, readerSettings.tapPageTurn, paragraphSpacing = 1.0f)
                            }
                        ) { Text("默认") }
                    }
                    CompactSlider(
                        value = readerSettings.paragraphSpacing,
                        onValueChange = {
                            viewModel.updateReaderSettings(readerSettings.fontScale, readerSettings.lineHeight, readerSettings.brightness, readerSettings.scrollMode, readerSettings.useSystemBrightness, readerSettings.theme, readerSettings.tapPageTurn, paragraphSpacing = it)
                        },
                        valueRange = 0.0f..2.0f
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("翻页模式点击翻页")
                        CompactSwitch(
                            checked = readerSettings.tapPageTurn,
                            onCheckedChange = {
                                viewModel.updateReaderSettings(readerSettings.fontScale, readerSettings.lineHeight, readerSettings.brightness, readerSettings.scrollMode, readerSettings.useSystemBrightness, readerSettings.theme, it)
                            }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("滚动模式点击翻页")
                        CompactSwitch(
                            checked = readerSettings.scrollTapPageTurn,
                            onCheckedChange = {
                                viewModel.updateReaderSettings(readerSettings.fontScale, readerSettings.lineHeight, readerSettings.brightness, readerSettings.scrollMode, readerSettings.useSystemBrightness, readerSettings.theme, readerSettings.tapPageTurn, scrollTapPageTurn = it)
                            }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("使用出版社样式")
                        CompactSwitch(
                            checked = readerSettings.publisherStyles,
                            onCheckedChange = {
                                viewModel.updateReaderSettings(readerSettings.fontScale, readerSettings.lineHeight, readerSettings.brightness, readerSettings.scrollMode, readerSettings.useSystemBrightness, readerSettings.theme, readerSettings.tapPageTurn, publisherStyles = it)
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("出版社样式指 EPUB 自带排版 CSS。关闭后 App 设置会更强地覆盖书籍原排版。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = { showAdvancedSettingsDialog = false }) { Text("完成") }
            }
        )
    }

    if (showTocDialog) {
        AlertDialog(
            onDismissRequest = { showTocDialog = false },
            title = { Text("目录") },
            text = {
                if (uiState.tableOfContents.isEmpty()) {
                    Text("暂无目录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(modifier = Modifier.height(420.dp)) {
                        items(uiState.tableOfContents) { item ->
                            TextButton(
                                onClick = {
                                    viewModel.goToTocItem(item)
                                    showTocDialog = false
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = (item.level * 16).dp)
                            ) {
                                Text(item.title, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTocDialog = false }) { Text("关闭") }
            }
        )
    }

    if (showBookmarksDialog) {
        AlertDialog(
            onDismissRequest = { showBookmarksDialog = false },
            title = { Text("书签") },
            text = {
                Column {
                    Button(
                        onClick = { viewModel.addBookmark() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.BookmarkAdd, contentDescription = null)
                        Text("添加当前位置")
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    if (uiState.bookmarks.isEmpty()) {
                        Text("暂无书签", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        LazyColumn(modifier = Modifier.height(320.dp)) {
                            items(uiState.bookmarks, key = { it.id }) { bookmark ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = {
                                            viewModel.goToBookmark(bookmark)
                                            showBookmarksDialog = false
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Text(bookmark.title.ifBlank { "书签" })
                                            Text(
                                                text = "${(bookmark.progression * 100).toInt()}%",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    IconButton(onClick = { viewModel.deleteBookmark(bookmark) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "删除书签")
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBookmarksDialog = false }) { Text("关闭") }
            }
        )
    }

    if (showNotesDialog) {
        AlertDialog(
            onDismissRequest = { showNotesDialog = false },
            title = { Text("笔记") },
            text = {
                Column {
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it.take(2000) },
                        label = { Text("新笔记") },
                        placeholder = { Text("先在正文中选中文字，可保存为选区笔记；未选中时保存当前位置笔记。") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            viewModel.addNote(noteText)
                            noteText = ""
                        },
                        enabled = noteText.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("保存当前笔记") }
                    Spacer(modifier = Modifier.height(12.dp))
                    if (uiState.annotations.isEmpty()) {
                        Text("暂无笔记", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        LazyColumn(modifier = Modifier.height(300.dp)) {
                            items(uiState.annotations, key = { it.id }) { annotation ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = {
                                            viewModel.goToAnnotation(annotation)
                                            showNotesDialog = false
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Text(annotation.selectedText.ifBlank { "当前位置" }, maxLines = 2)
                                            Text(annotation.note, maxLines = 2, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                    IconButton(onClick = {
                                        editingAnnotation = annotation
                                        editNoteText = annotation.note
                                    }) {
                                        Icon(Icons.Default.Edit, contentDescription = "编辑笔记")
                                    }
                                    IconButton(onClick = { viewModel.deleteAnnotation(annotation) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "删除笔记")
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showNotesDialog = false }) { Text("关闭") }
            }
        )
    }

    editingAnnotation?.let { annotation ->
        AlertDialog(
            onDismissRequest = { editingAnnotation = null },
            title = { Text("编辑笔记") },
            text = {
                OutlinedTextField(
                    value = editNoteText,
                    onValueChange = { editNoteText = it.take(2000) },
                    label = { Text("笔记内容") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateAnnotation(annotation, editNoteText)
                        editingAnnotation = null
                    }
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { editingAnnotation = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun FloatingTtsBar(
    text: String,
    status: String,
    isPlaying: Boolean,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenPanel: () -> Unit,
    onCloseTts: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Column(modifier = Modifier.weight(1f).clickable { onOpenPanel() }.sizeIn(minHeight = 36.dp)) {
                Text(text.ifBlank { status.ifBlank { "朗读中" } }, maxLines = 1, style = MaterialTheme.typography.bodySmall)
                Text(status, maxLines = 1, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onPrevious) { Text("上") }
            TextButton(onClick = onToggle) { Text(if (isPlaying) "暂停" else "继续") }
            TextButton(onClick = onNext) { Text("下") }
            TextButton(onClick = onCloseTts) { Text("关闭") }
        }
    }
}

@Composable
private fun ReaderBottomBar(
    progression: Double,
    theme: ReaderTheme,
    onToc: () -> Unit,
    onTheme: () -> Unit,
    onSettings: () -> Unit,
    onNote: () -> Unit,
    onTts: () -> Unit,
    onProgressClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        ReaderProgressOverlay(progression = progression, onClick = onProgressClick)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToc) { Icon(Icons.Default.MenuBook, contentDescription = "目录") }
            IconButton(onClick = onTheme) {
                Icon(if (theme == ReaderTheme.DARK) Icons.Default.LightMode else Icons.Default.DarkMode, contentDescription = "主题")
            }
            IconButton(onClick = onSettings) { Icon(Icons.Default.Tune, contentDescription = "阅读设置") }
            IconButton(onClick = onNote) { Icon(Icons.Default.Edit, contentDescription = "笔记") }
            IconButton(onClick = onTts) { Icon(Icons.Default.VolumeUp, contentDescription = "朗读") }
        }
    }
}

@Composable
private fun ReaderThemeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) }
    )
}

@Composable
private fun ReaderOptionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) }
    )
}

@Composable
private fun CompactSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>
) {
    Box(modifier = Modifier.height(28.dp), contentAlignment = Alignment.Center) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth().scale(scaleX = 1f, scaleY = 0.82f)
        )
    }
}

@Composable
private fun CompactSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = Modifier.height(32.dp).scale(0.82f)
    )
}

@Composable
private fun ReaderProgressOverlay(
    progression: Double,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val safeProgress = progression.toFloat().coerceIn(0f, 1f)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(safeProgress)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                text = "${(safeProgress * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}

private fun FragmentActivity.showSystemBarsAfterReader() {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
        window.insetsController?.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
    } else {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }
}
