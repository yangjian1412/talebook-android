package com.talebook.app.ui.screens

import com.talebook.app.MainActivity

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.talebook.app.reader.ReaderBarsController
import com.talebook.app.reader.ReadiumHostFragment
import com.talebook.app.data.local.RecentReadingEntity
import com.talebook.app.reader.ReadiumUiEvents
import com.talebook.app.reader.ReaderFontFamily
import com.talebook.app.reader.ReaderTheme
import com.talebook.app.data.local.ReaderAnnotationEntity
import com.talebook.app.data.repository.SettingsRepository
import com.talebook.app.ui.theme.ReaderThemePalette
import com.talebook.app.ui.theme.ThemePresets
import com.talebook.app.ui.theme.toColor
import com.talebook.app.viewmodel.LocalReaderViewModel
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import android.view.View
import android.view.WindowInsets as AndroidWindowInsets
import android.widget.FrameLayout
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalReaderScreen(
    bookId: Int? = null,
    localBookId: Long? = null,
    initialLocatorJson: String? = null,
    onBack: () -> Unit,
    onOpenLogin: (Int) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    viewModel: LocalReaderViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val settingsRepository = remember(context.applicationContext) { SettingsRepository(context.applicationContext) }
    val skipAuth by settingsRepository.skipAuth.collectAsState(initial = false)
    val appThemeMode by settingsRepository.themeMode.collectAsState(initial = SettingsRepository.THEME_AUTO)
    val dayPreset by settingsRepository.dayThemePreset.collectAsState(initial = ThemePresets.DAY_SYSTEM)
    val nightPreset by settingsRepository.nightThemePreset.collectAsState(initial = ThemePresets.NIGHT_CHARCOAL)
    val customBackground by settingsRepository.dayCustomBackground.collectAsState(initial = ThemePresets.day.first { it.id == ThemePresets.DAY_CUSTOM }.background)
    val customText by settingsRepository.dayCustomText.collectAsState(initial = ThemePresets.day.first { it.id == ThemePresets.DAY_CUSTOM }.text)
    val hideStatusBarInReader by settingsRepository.readerHideStatusBarInReader.collectAsState(initial = false)
    val hideTimeInReader by settingsRepository.readerHideTimeInReader.collectAsState(initial = false)
    val hideChapterPathInReader by settingsRepository.readerHideChapterPathInReader.collectAsState(initial = false)
    val hideToolbarLabels by settingsRepository.readerHideToolbarLabels.collectAsState(initial = true)
    val pageMarginSeparateMode by settingsRepository.readerPageMarginSeparateMode.collectAsState(initial = false)
    val systemDark = isSystemInDarkTheme()
    val scope = rememberCoroutineScope()
    var barsVisible by ReaderBarsController.barsVisible
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
    var showCustomThemeDialog by remember { mutableStateOf(false) }
    var showPageMarginDialog by remember { mutableStateOf(false) }
    var previewBackground by remember(customBackground) { mutableStateOf(customBackground) }
    var previewText by remember(customText) { mutableStateOf(customText) }
    var showProgressJumpDialog by remember { mutableStateOf(false) }
    var progressJumpText by remember { mutableStateOf("") }
    var pageJumpText by remember { mutableStateOf("") }
    var progression by remember { mutableStateOf(0.0) }
    var initialLocatorConsumed by remember(bookId, initialLocatorJson) { mutableStateOf(false) }
    var readerSafeTopPadding by remember { mutableStateOf(0.dp) }
    val readerSettings = uiState.readerSettings
    val effectiveDark = ThemePresets.isDark(appThemeMode, systemDark)
    val selectedReaderPalette = if (effectiveDark) {
        ThemePresets.night.firstOrNull { it.id == nightPreset } ?: ThemePresets.night.first()
    } else {
        val base = ThemePresets.day.firstOrNull { it.id == dayPreset } ?: ThemePresets.day.first()
        if (base.id == ThemePresets.DAY_CUSTOM) base.copy(background = customBackground, text = customText) else base
    }
    fun readerPaletteForDark(dark: Boolean): ReaderThemePalette {
        return if (dark) {
            ThemePresets.night.firstOrNull { it.id == nightPreset } ?: ThemePresets.night.first()
        } else {
            val base = ThemePresets.day.firstOrNull { it.id == dayPreset } ?: ThemePresets.day.first()
            if (base.id == ThemePresets.DAY_CUSTOM) base.copy(background = customBackground, text = customText) else base
        }
    }
    fun applyReaderPalette(palette: ReaderThemePalette, dark: Boolean = effectiveDark) {
        val current = uiState.readerSettings
        viewModel.updateReaderSettings(
            fontScale = current.fontScale,
            lineHeight = current.lineHeight,
            brightness = current.brightness,
            scrollMode = current.scrollMode,
            useSystemBrightness = current.useSystemBrightness,
            theme = if (dark) ReaderTheme.DARK else ReaderTheme.CUSTOM,
            tapPageTurn = current.tapPageTurn,
            readerBackgroundColor = palette.background,
            readerTextColor = palette.text,
            customThemeEnabled = true,
            appDark = dark
        )
    }

    fun applyReaderTheme() {
        val current = uiState.readerSettings
        viewModel.updateReaderSettings(
            fontScale = current.fontScale,
            lineHeight = current.lineHeight,
            brightness = current.brightness,
            scrollMode = current.scrollMode,
            useSystemBrightness = current.useSystemBrightness,
            theme = current.theme,
            tapPageTurn = current.tapPageTurn,
            readerBackgroundColor = current.readerBackgroundColor,
            readerTextColor = current.readerTextColor,
            customThemeEnabled = current.customThemeEnabled,
            appDark = effectiveDark
        )
    }

    LaunchedEffect(uiState.sessionId) {
        if (uiState.sessionId != null) {
            applyReaderTheme()
        }
    }
    val activity = context.findFragmentActivity()
    val leaveReader = remember(activity, onBack) {
        {
            activity?.showSystemBarsAfterReader()
            onBack()
        }
    }

    DisposableEffect(activity, hideStatusBarInReader) {
        if (hideStatusBarInReader) {
            activity?.hideReaderStatusBar()
        } else {
            activity?.showSystemBarsAfterReader()
        }
        onDispose { activity?.showSystemBarsAfterReader() }
    }

    DisposableEffect(activity, readerSettings.volumeKeyPageTurn) {
        (activity as? MainActivity)?.volumeKeyPageTurnEnabled = readerSettings.volumeKeyPageTurn
        onDispose { (activity as? MainActivity)?.volumeKeyPageTurnEnabled = false }
    }

    LaunchedEffect(activity, uiState.sessionId, readerSettings.scrollMode) {
        val act = activity as? MainActivity ?: return@LaunchedEffect
        act.currentReaderSessionId = uiState.sessionId
        act.currentReaderScrollMode = readerSettings.scrollMode
    }

    LaunchedEffect(bookId, localBookId) {
        when {
            localBookId != null -> viewModel.loadLocalBook(context.applicationContext, localBookId)
            bookId != null -> viewModel.load(context.applicationContext, bookId)
        }
    }

    LaunchedEffect(uiState.sessionId, initialLocatorJson) {
        val sessionId = uiState.sessionId ?: return@LaunchedEffect
        val locator = initialLocatorJson ?: return@LaunchedEffect
        if (!initialLocatorConsumed && locator.isNotBlank()) {
            initialLocatorConsumed = true
            ReadiumUiEvents.emitGoToLocator(sessionId, locator)
        }
    }

    LaunchedEffect(uiState.sessionId, dayPreset, nightPreset, customBackground, customText) {
        // Reserved: palette is applied via dedicated flows; placeholder to avoid extra triggers when appThemeMode changes.
    }

    LaunchedEffect(uiState.sessionId) {
        val sessionId = uiState.sessionId ?: return@LaunchedEffect
        ReadiumUiEvents.progress.collect { (targetSessionId, _) ->
            if (targetSessionId == sessionId) {
                ReaderBarsController.hide()
            }
        }
    }

    LaunchedEffect(uiState.sessionId) {
        val sessionId = uiState.sessionId ?: return@LaunchedEffect
        ReadiumUiEvents.centerTaps.collect { (tappedSessionId, locatorJson) ->
            if (tappedSessionId == sessionId) {
                val wasVisible = barsVisible
                ReaderBarsController.toggle()
                if (wasVisible) {
                    ReadiumUiEvents.emitGoToLocator(sessionId, locatorJson)
                }
            }
        }
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

    LaunchedEffect(uiState.sessionId) {
        val sessionId = uiState.sessionId ?: return@LaunchedEffect
        ReadiumUiEvents.currentChapterPath.collect { (targetSessionId, path) ->
            if (targetSessionId == sessionId) viewModel.updateCurrentChapterPath(path)
        }
    }

    val currentReaderBackgroundLong = if (readerSettings.readerBackgroundColor != 0L) {
        readerSettings.readerBackgroundColor
    } else {
        if (effectiveDark) {
            ThemePresets.night.first { it.id == ThemePresets.NIGHT_SYSTEM }.background
        } else {
            ThemePresets.day.first { it.id == ThemePresets.DAY_SYSTEM }.background
        }
    }
    val currentReaderBackground = currentReaderBackgroundLong.toColor()
    val currentReaderTextLong = if (readerSettings.readerTextColor != 0L) {
        readerSettings.readerTextColor
    } else {
        if (effectiveDark) {
            ThemePresets.night.first { it.id == ThemePresets.NIGHT_SYSTEM }.text
        } else {
            ThemePresets.day.first { it.id == ThemePresets.DAY_SYSTEM }.text
        }
    }
    val currentReaderText = currentReaderTextLong.toColor()
    val topBarColor = if (barsVisible) MaterialTheme.colorScheme.surface else currentReaderBackground
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val cutoutTop = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
    val candidateSafeTop = if (statusBarTop > cutoutTop) statusBarTop else cutoutTop
    LaunchedEffect(candidateSafeTop) {
        if (candidateSafeTop > readerSafeTopPadding) {
            readerSafeTopPadding = candidateSafeTop
        }
    }
    SideEffect {
        activity?.window?.statusBarColor = topBarColor.toArgb()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(currentReaderBackground)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(currentReaderBackground)
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
                    val errMsg = uiState.error ?: "加载失败"
                    val authNeeded = errMsg.contains("登录") || errMsg.contains("访问受限")
                    val isRemote = !uiState.isCached && bookId != null && localBookId == null
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(errMsg, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(8.dp))
                        if (isRemote && (authNeeded || errMsg.contains("无法连接"))) {
                            Text(
                                text = "您可能需要配置服务器或登录后再次尝试。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = {
                                    if (skipAuth) {
                                        onOpenSettings()
                                    } else {
                                        onOpenLogin(bookId)
                                    }
                                }
                            ) {
                                Text(if (skipAuth) "设置书库与登录" else "配置服务器与登录")
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        Button(onClick = {
                            val id = bookId
                            val localId = localBookId
                            when {
                                localId != null -> viewModel.loadLocalBook(context.applicationContext, localId)
                                id != null -> viewModel.load(context.applicationContext, id)
                            }
                        }) {
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
                                        top = if (readerSettings.scrollMode) 32.dp else readerSafeTopPadding + 24.dp,
                                        bottom = 48.dp
                                    )
                                    .background(currentReaderBackground),
                                factory = { ctx ->
                                    FrameLayout(ctx).apply {
                                        id = containerId
                                        setBackgroundColor(android.graphics.Color.rgb(
                                            ((currentReaderBackgroundLong shr 16) and 0xFF).toInt(),
                                            ((currentReaderBackgroundLong shr 8) and 0xFF).toInt(),
                                            (currentReaderBackgroundLong and 0xFF).toInt()
                                        ))
                                    }
                                },
                                update = {
                                    it.setBackgroundColor(android.graphics.Color.rgb(
                                        ((currentReaderBackgroundLong shr 16) and 0xFF).toInt(),
                                        ((currentReaderBackgroundLong shr 8) and 0xFF).toInt(),
                                        (currentReaderBackgroundLong and 0xFF).toInt()
                                    ))
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
                    darkMode = effectiveDark,
                    showTime = !hideTimeInReader,
                    showLabels = !hideToolbarLabels,
                    contentColor = currentReaderText,
                    onToc = { showTocDialog = true },
                    onToggleTheme = {
                        val targetDark = !effectiveDark
                        applyReaderPalette(readerPaletteForDark(targetDark), targetDark)
                        scope.launch {
                            settingsRepository.saveThemeMode(
                                if (targetDark) SettingsRepository.THEME_DARK else SettingsRepository.THEME_LIGHT
                            )
                        }
                    },
                    onSettings = { showSettingsDialog = true },
                    onNote = { showNotesDialog = true },
                    onTts = { viewModel.startTts() },
                    onProgressClick = {
                        progressJumpText = ((progression * 100).toInt()).toString()
                        pageJumpText = ""
                        showProgressJumpDialog = true
                    },
                    chapterPath = if (hideChapterPathInReader) "" else uiState.currentChapterPath,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.displayCutout))
                )
            }
            if (!barsVisible && uiState.sessionId != null) {
                ReaderProgressOverlay(
                    progression = progression,
                    showTime = !hideTimeInReader,
                    contentColor = currentReaderText,
                    onClick = {
                        progressJumpText = ((progression * 100).toInt()).toString()
                        pageJumpText = ""
                        showProgressJumpDialog = true
                    },
                    chapterPath = if (hideChapterPathInReader) "" else uiState.currentChapterPath,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.displayCutout))
                )
            }
        }
        if (barsVisible) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(),
                color = topBarColor
            ) {
                Column {
                    Spacer(modifier = Modifier.height(statusBarTop))
                    TopAppBar(
                        windowInsets = WindowInsets(0.dp),
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
                            if (uiState.sourceKind != RecentReadingEntity.SOURCE_KIND_LOCAL) {
                                IconButton(
                                    onClick = { viewModel.cacheCurrentBook(context.applicationContext) },
                                    enabled = !uiState.isCached && !uiState.isCaching
                                ) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = "缓存本书")
                                }
                            }
                        }
                    )
                }
            }
}
            if (uiState.sessionId != null && !hideChapterPathInReader) {
                val bookTitle = uiState.title.ifBlank { "本地阅读器" }
                Text(
                    text = if (bookTitle.length > 20) bookTitle.take(20) + "..." else bookTitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = currentReaderText.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
                        .padding(top = 4.dp, start = 16.dp, end = 16.dp)
                )
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
                    .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
                    .padding(top = if (barsVisible) 72.dp else 12.dp, start = 12.dp, end = 12.dp)
            )
        }
        if (uiState.ttsState.isPanelVisible) {
            var voicesExpanded by remember { mutableStateOf(false) }
            Box(modifier = Modifier.align(Alignment.TopCenter)) {
                AnimatedVisibility(
                    visible = uiState.ttsState.isPanelVisible,
                    enter = slideInVertically(initialOffsetY = { -it }),
                    exit = slideOutVertically(targetOffsetY = { -it })
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        tonalElevation = 6.dp,
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "朗读",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                IconButton(
                                    onClick = { viewModel.hideTtsPanel() },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "收起",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                            Text(
                                text = uiState.ttsState.currentText.ifBlank { uiState.ttsState.status.ifBlank { "从当前位置开始朗读" } },
                                style = MaterialTheme.typography.bodyMedium,
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
                                    enabled = !uiState.ttsState.isLoading,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                ) { Text(if (uiState.ttsState.isPlaying) "暂停" else "开始") }
                                OutlinedButton(
                                    onClick = { viewModel.stopTts() },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                ) { Text("停止") }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { viewModel.previousTtsSentence() },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                ) { Text("上一句") }
                                OutlinedButton(
                                    onClick = { viewModel.nextTtsSentence() },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                ) { Text("下一句") }
                            }
                            Text(
                                text = "语速 ${String.format(java.util.Locale.US, "%.1f", uiState.ttsState.speechRate)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            CompactSlider(
                                value = uiState.ttsState.speechRate,
                                onValueChange = { viewModel.updateTtsRate(it) },
                                valueRange = 0.5f..2.0f
                            )
                            Text(
                                text = "音调 ${String.format(java.util.Locale.US, "%.1f", uiState.ttsState.pitch)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            CompactSlider(
                                value = uiState.ttsState.pitch,
                                onValueChange = { viewModel.updateTtsPitch(it) },
                                valueRange = 0.5f..2.0f
                            )
                            Box {
                                OutlinedButton(
                                    onClick = { voicesExpanded = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                ) {
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
                                Text(
                                    text = "睡眠模式",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                CompactSwitch(
                                    checked = uiState.ttsState.sleepEnabled,
                                    onCheckedChange = { viewModel.updateTtsSleep(it) }
                                )
                            }
                            if (uiState.ttsState.sleepEnabled) {
                                Text(
                                    text = "${uiState.ttsState.sleepMinutes} 分钟后自动停止",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                CompactSlider(
                                    value = uiState.ttsState.sleepMinutes.toFloat(),
                                    onValueChange = { viewModel.updateTtsSleep(true, it.toInt()) },
                                    valueRange = 5f..180f
                                )
                            }
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                            Text(
                                text = "无法后台播放时，请将本应用电池策略设为「不限制」",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val batteryContext = LocalContext.current
                            OutlinedButton(
                                onClick = {
                                    runCatching {
                                        batteryContext.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text("电池优化设置")
                            }
                        }
                    }
                }
            }
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
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
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
                        valueRange = 0.5f..3.0f
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
                        valueRange = 0.5f..3.0f
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("页边距")
                            if (pageMarginSeparateMode) {
                                Text(
                                    text = "左 ${String.format(java.util.Locale.US, "%.1f", readerSettings.pageMarginHorizontal)} · 右 ${String.format(java.util.Locale.US, "%.1f", readerSettings.pageMarginHorizontal)} · 上 ${String.format(java.util.Locale.US, "%.1f", readerSettings.pageMarginVertical)} · 下 ${String.format(java.util.Locale.US, "%.1f", readerSettings.pageMarginVertical)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Text(
                                    text = "${String.format(java.util.Locale.US, "%.1f", readerSettings.pageMargins)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        TextButton(onClick = { showPageMarginDialog = true }) { Text("分开设置") }
                        TextButton(
                            onClick = {
                                viewModel.updateReaderSettings(
                                    fontScale = readerSettings.fontScale,
                                    lineHeight = readerSettings.lineHeight,
                                    brightness = readerSettings.brightness,
                                    scrollMode = readerSettings.scrollMode,
                                    useSystemBrightness = readerSettings.useSystemBrightness,
                                    theme = readerSettings.theme,
                                    tapPageTurn = readerSettings.tapPageTurn,
                                    pageMargins = 1.0f,
                                    pageMarginHorizontal = 1.0f,
                                    pageMarginVertical = 1.0f
                                )
                            }
                        ) { Text("默认") }
                    }
                    if (!pageMarginSeparateMode) {
                        CompactSlider(
                            value = readerSettings.pageMargins,
                            onValueChange = {
                                viewModel.updateReaderSettings(readerSettings.fontScale, readerSettings.lineHeight, readerSettings.brightness, readerSettings.scrollMode, readerSettings.useSystemBrightness, readerSettings.theme, readerSettings.tapPageTurn, pageMargins = it, pageMarginHorizontal = it, pageMarginVertical = it)
                            },
                            valueRange = 0.5f..3.0f
                        )
                    }
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
                            valueRange = 0.0f..1.0f
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("明暗模式")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        ReaderOptionChip("跟随", appThemeMode == SettingsRepository.THEME_AUTO) {
                            val targetDark = ThemePresets.isDark(SettingsRepository.THEME_AUTO, systemDark)
                            applyReaderPalette(readerPaletteForDark(targetDark), targetDark)
                            scope.launch { settingsRepository.saveThemeMode(SettingsRepository.THEME_AUTO) }
                        }
                        ReaderOptionChip("白天", appThemeMode == SettingsRepository.THEME_LIGHT) {
                            applyReaderPalette(readerPaletteForDark(false), false)
                            scope.launch { settingsRepository.saveThemeMode(SettingsRepository.THEME_LIGHT) }
                        }
                        ReaderOptionChip("黑夜", appThemeMode == SettingsRepository.THEME_DARK) {
                            applyReaderPalette(readerPaletteForDark(true), true)
                            scope.launch { settingsRepository.saveThemeMode(SettingsRepository.THEME_DARK) }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(if (effectiveDark) "阅读背景（夜间）" else "阅读背景（白天）")
                    ReaderThemePresetPicker(
                        presets = if (effectiveDark) ThemePresets.night else ThemePresets.day,
                        selected = if (effectiveDark) nightPreset else dayPreset,
                        onSelect = { preset ->
                            val palette = if (effectiveDark) {
                                ThemePresets.night.first { it.id == preset }
                            } else {
                                ThemePresets.day.first { it.id == preset }.let { base ->
                                    if (base.id == ThemePresets.DAY_CUSTOM) {
                                        base.copy(background = customBackground, text = customText)
                                    } else {
                                        base
                                    }
                                }
                            }
                            applyReaderPalette(palette)
                            scope.launch {
                                if (effectiveDark) {
                                    settingsRepository.saveNightThemePreset(preset)
                                } else if (preset == ThemePresets.DAY_CUSTOM) {
                                    settingsRepository.saveDayCustomColors(customBackground, customText)
                                    showCustomThemeDialog = true
                                } else {
                                    settingsRepository.saveDayThemePreset(preset)
                                }
                            }
                        }
                    )
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
                    val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "横屏双页显示",
                            color = if (isLandscape) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        CompactSwitch(
                            checked = readerSettings.twoPageMode,
                            enabled = isLandscape,
                            onCheckedChange = {
                                viewModel.updateReaderSettings(fontScale = readerSettings.fontScale, lineHeight = readerSettings.lineHeight, brightness = readerSettings.brightness, scrollMode = false, useSystemBrightness = readerSettings.useSystemBrightness, theme = readerSettings.theme, tapPageTurn = readerSettings.tapPageTurn, twoPageMode = it)
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettingsDialog = false }) { Text("完成") }
            }
        )
    }

    if (showPageMarginDialog) {
        var localH by remember(readerSettings.pageMarginHorizontal) { mutableStateOf(readerSettings.pageMarginHorizontal) }
        var localV by remember(readerSettings.pageMarginVertical) { mutableStateOf(readerSettings.pageMarginVertical) }
        AlertDialog(
            onDismissRequest = { showPageMarginDialog = false },
            title = { Text("页边距分开设置") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("左右 ${String.format(java.util.Locale.US, "%.2f", localH)}")
                        TextButton(onClick = { localH = 1.0f }) { Text("默认") }
                    }
                    CompactSlider(value = localH, onValueChange = { localH = it }, valueRange = 0.5f..3.0f)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("上下 ${String.format(java.util.Locale.US, "%.2f", localV)}")
                        TextButton(onClick = { localV = 1.0f }) { Text("默认") }
                    }
                    CompactSlider(value = localV, onValueChange = { localV = it }, valueRange = 0.5f..3.0f)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateReaderSettings(
                        fontScale = readerSettings.fontScale,
                        lineHeight = readerSettings.lineHeight,
                        brightness = readerSettings.brightness,
                        scrollMode = readerSettings.scrollMode,
                        useSystemBrightness = readerSettings.useSystemBrightness,
                        theme = readerSettings.theme,
                        tapPageTurn = readerSettings.tapPageTurn,
                        pageMarginHorizontal = localH,
                        pageMarginVertical = localV
                    )
                    showPageMarginDialog = false
                }) { Text("完成") }
            },
            dismissButton = {
                TextButton(onClick = { showPageMarginDialog = false }) { Text("取消") }
            }
        )
    }

    if (showCustomThemeDialog) {
        AlertDialog(
            onDismissRequest = { showCustomThemeDialog = false },
            title = { Text("自定义阅读背景") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(previewBackground.toColor())
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text("样例正文 ABC", color = previewText.toColor(), style = MaterialTheme.typography.bodyLarge)
                    }
                    Text("背景颜色")
                    CompactSlider(
                        value = previewBackground.lightHueValue(),
                        onValueChange = { value ->
                            val bg = lightColorFromSlider(value)
                            previewBackground = bg
                            val palette = ThemePresets.day.first { it.id == ThemePresets.DAY_CUSTOM }.copy(background = bg, text = previewText)
                            applyReaderPalette(palette)
                        },
                        valueRange = 0f..1f
                    )
                    Text("文字颜色")
                    CompactSlider(
                        value = previewText.darkHueValue(),
                        onValueChange = { value ->
                            val fg = darkColorFromSlider(value)
                            previewText = fg
                            val palette = ThemePresets.day.first { it.id == ThemePresets.DAY_CUSTOM }.copy(background = previewBackground, text = fg)
                            applyReaderPalette(palette)
                        },
                        valueRange = 0f..1f
                    )
                    OutlinedButton(
                        onClick = {
                            previewText = 0xFF000000L
                            val palette = ThemePresets.day.first { it.id == ThemePresets.DAY_CUSTOM }.copy(background = previewBackground, text = 0xFF000000L)
                            applyReaderPalette(palette)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("使用纯黑色文字") }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { settingsRepository.saveDayCustomColors(previewBackground, previewText) }
                    showCustomThemeDialog = false
                }) { Text("完成") }
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
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
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
                        ReaderOptionChip("等宽", readerSettings.fontFamily == ReaderFontFamily.MONOSPACE) {
                            viewModel.updateReaderSettings(readerSettings.fontScale, readerSettings.lineHeight, readerSettings.brightness, readerSettings.scrollMode, readerSettings.useSystemBrightness, readerSettings.theme, readerSettings.tapPageTurn, fontFamily = ReaderFontFamily.MONOSPACE)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("字间距 ${String.format(java.util.Locale.US, "%.1f", readerSettings.letterSpacing)}")
                        TextButton(
                            onClick = {
                                viewModel.updateReaderSettings(readerSettings.fontScale, readerSettings.lineHeight, readerSettings.brightness, readerSettings.scrollMode, readerSettings.useSystemBrightness, readerSettings.theme, readerSettings.tapPageTurn, letterSpacing = 0f)
                            }
                        ) { Text("默认") }
                    }
                    CompactSlider(
                        value = readerSettings.letterSpacing,
                        onValueChange = {
                            viewModel.updateReaderSettings(readerSettings.fontScale, readerSettings.lineHeight, readerSettings.brightness, readerSettings.scrollMode, readerSettings.useSystemBrightness, readerSettings.theme, readerSettings.tapPageTurn, letterSpacing = it)
                        },
                        valueRange = 0f..10f
                    )
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
                        valueRange = 0.0f..4.0f
                    )
                    var showVolumeKeyInfo by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("音量键翻页")
                            Text(
                                text = "注意",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clickable { showVolumeKeyInfo = true }
                                    .padding(vertical = 2.dp)
                            )
                        }
                        CompactSwitch(
                            checked = readerSettings.volumeKeyPageTurn,
                            onCheckedChange = {
                                viewModel.updateReaderSettings(readerSettings.fontScale, readerSettings.lineHeight, readerSettings.brightness, readerSettings.scrollMode, readerSettings.useSystemBrightness, readerSettings.theme, readerSettings.tapPageTurn, volumeKeyPageTurn = it)
                            }
                        )
                    }
                    if (showVolumeKeyInfo) {
                        AlertDialog(
                            onDismissRequest = { showVolumeKeyInfo = false },
                            title = { Text("音量键翻页") },
                            text = { Text("开启后，音量上键 = 上一页，音量下键 = 下一页。仅在翻页模式生效，滚动模式按音量键会提示。仅在阅读器中屏蔽系统音量调节，离开阅读器恢复正常。") },
                            confirmButton = {
                                TextButton(onClick = { showVolumeKeyInfo = false }) { Text("知道了") }
                            }
                        )
                    }
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
                    Text("滚动模式点击翻页")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ReaderOptionChip("关", readerSettings.scrollTapPageTurn == com.talebook.app.reader.ReaderScrollTapSpeed.OFF) {
                            viewModel.updateReaderSettings(readerSettings.fontScale, readerSettings.lineHeight, readerSettings.brightness, readerSettings.scrollMode, readerSettings.useSystemBrightness, readerSettings.theme, readerSettings.tapPageTurn, scrollTapPageTurn = com.talebook.app.reader.ReaderScrollTapSpeed.OFF)
                        }
                        ReaderOptionChip("快", readerSettings.scrollTapPageTurn == com.talebook.app.reader.ReaderScrollTapSpeed.FAST) {
                            viewModel.updateReaderSettings(readerSettings.fontScale, readerSettings.lineHeight, readerSettings.brightness, readerSettings.scrollMode, readerSettings.useSystemBrightness, readerSettings.theme, readerSettings.tapPageTurn, scrollTapPageTurn = com.talebook.app.reader.ReaderScrollTapSpeed.FAST)
                        }
                        ReaderOptionChip("中", readerSettings.scrollTapPageTurn == com.talebook.app.reader.ReaderScrollTapSpeed.MEDIUM) {
                            viewModel.updateReaderSettings(readerSettings.fontScale, readerSettings.lineHeight, readerSettings.brightness, readerSettings.scrollMode, readerSettings.useSystemBrightness, readerSettings.theme, readerSettings.tapPageTurn, scrollTapPageTurn = com.talebook.app.reader.ReaderScrollTapSpeed.MEDIUM)
                        }
                        ReaderOptionChip("慢", readerSettings.scrollTapPageTurn == com.talebook.app.reader.ReaderScrollTapSpeed.SLOW) {
                            viewModel.updateReaderSettings(readerSettings.fontScale, readerSettings.lineHeight, readerSettings.brightness, readerSettings.scrollMode, readerSettings.useSystemBrightness, readerSettings.theme, readerSettings.tapPageTurn, scrollTapPageTurn = com.talebook.app.reader.ReaderScrollTapSpeed.SLOW)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("阅读时隐藏状态栏")
                        CompactSwitch(
                            checked = hideStatusBarInReader,
                            onCheckedChange = { scope.launch { settingsRepository.saveReaderHideStatusBarInReader(it) } }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("阅读时隐藏时间")
                        CompactSwitch(
                            checked = hideTimeInReader,
                            onCheckedChange = { scope.launch { settingsRepository.saveReaderHideTimeInReader(it) } }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("阅读时隐藏书名和章节")
                        CompactSwitch(
                            checked = hideChapterPathInReader,
                            onCheckedChange = { scope.launch { settingsRepository.saveReaderHideChapterPathInReader(it) } }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("底部工具栏隐藏中文标签")
                        CompactSwitch(
                            checked = hideToolbarLabels,
                            onCheckedChange = { scope.launch { settingsRepository.saveReaderHideToolbarLabels(it) } }
                        )
                    }
                    var showPublisherStylesInfo by remember { mutableStateOf(false) }
                    var showForcePublisherFontsInfo by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("使用出版社样式")
                            Text(
                                text = "注意",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clickable { showPublisherStylesInfo = true }
                                    .padding(vertical = 2.dp)
                            )
                        }
                        CompactSwitch(
                            checked = readerSettings.publisherStyles,
                            onCheckedChange = {
                                viewModel.updateReaderSettings(readerSettings.fontScale, readerSettings.lineHeight, readerSettings.brightness, readerSettings.scrollMode, readerSettings.useSystemBrightness, readerSettings.theme, readerSettings.tapPageTurn, publisherStyles = it)
                            }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("强制使用出版社字体")
                            Text(
                                text = "注意",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clickable { showForcePublisherFontsInfo = true }
                                    .padding(vertical = 2.dp)
                            )
                        }
                        CompactSwitch(
                            checked = readerSettings.forcePublisherFonts,
                            onCheckedChange = {
                                viewModel.updateReaderSettings(readerSettings.fontScale, readerSettings.lineHeight, readerSettings.brightness, readerSettings.scrollMode, readerSettings.useSystemBrightness, readerSettings.theme, readerSettings.tapPageTurn, forcePublisherFonts = it)
                            }
                        )
                    }
                    if (showPublisherStylesInfo) {
                        AlertDialog(
                            onDismissRequest = { showPublisherStylesInfo = false },
                            title = { Text("使用出版社样式") },
                            text = { Text("出版社样式指 EPUB 自带 CSS。关闭后 App 设置覆盖更强，行距、字体、字距等参数会强制生效。") },
                            confirmButton = {
                                TextButton(onClick = { showPublisherStylesInfo = false }) { Text("知道了") }
                            }
                        )
                    }
                    if (showForcePublisherFontsInfo) {
                        AlertDialog(
                            onDismissRequest = { showForcePublisherFontsInfo = false },
                            title = { Text("强制使用出版社字体") },
                            text = { Text("强制使用出版社字体可能造成部分图书无法在线阅读。开启此选项后，App 会忽略内嵌大字体，避免远程阅读时白屏，但会影响排版一致性。") },
                            confirmButton = {
                                TextButton(onClick = { showForcePublisherFontsInfo = false }) { Text("知道了") }
                            }
                        )
                    }
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
            IconButton(onClick = onPrevious) { Icon(Icons.Filled.SkipPrevious, contentDescription = "上一句") }
            IconButton(onClick = onToggle) { Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = if (isPlaying) "暂停" else "继续") }
            IconButton(onClick = onNext) { Icon(Icons.Filled.SkipNext, contentDescription = "下一句") }
            IconButton(onClick = onCloseTts) { Icon(Icons.Default.Close, contentDescription = "关闭朗读") }
        }
    }
}

@Composable
private fun ReaderBottomBar(
    progression: Double,
    darkMode: Boolean,
    showTime: Boolean,
    showLabels: Boolean,
    contentColor: Color,
    onToc: () -> Unit,
    onToggleTheme: () -> Unit,
    onSettings: () -> Unit,
    onNote: () -> Unit,
    onTts: () -> Unit,
    onProgressClick: () -> Unit,
    chapterPath: String = "",
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        ReaderProgressOverlay(
            progression = progression,
            showTime = showTime,
            contentColor = contentColor,
            onClick = onProgressClick,
            chapterPath = chapterPath
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolbarButton(icon = { Icon(Icons.Default.MenuBook, contentDescription = "目录") }, label = "目录", showLabel = showLabels, onClick = onToc)
            ToolbarButton(icon = { Icon(if (darkMode) Icons.Default.LightMode else Icons.Default.DarkMode, contentDescription = "切换明暗") }, label = if (darkMode) "白天" else "黑夜", showLabel = showLabels, onClick = onToggleTheme)
            ToolbarButton(icon = { Icon(Icons.Default.Tune, contentDescription = "阅读设置") }, label = "设置", showLabel = showLabels, onClick = onSettings)
            ToolbarButton(icon = { Icon(Icons.Default.Edit, contentDescription = "笔记") }, label = "笔记", showLabel = showLabels, onClick = onNote)
            ToolbarButton(icon = { Icon(Icons.Default.VolumeUp, contentDescription = "朗读") }, label = "朗读", showLabel = showLabels, onClick = onTts)
        }
    }
}

@Composable
private fun ToolbarButton(icon: @Composable () -> Unit, label: String, showLabel: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            icon()
            if (showLabel) {
                Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ReaderOptionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                softWrap = false
            )
        }
    )
}

@Composable
private fun ReaderThemePresetPicker(
    presets: List<ReaderThemePalette>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        presets.forEach { preset ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(if (preset.id == selected) 40.dp else 34.dp)
                        .clip(CircleShape)
                        .background(preset.background.toColor())
                        .clickable { onSelect(preset.id) }
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(preset.text.toColor())
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = preset.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (preset.id == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CompactSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>
) {
    Box(modifier = Modifier.height(24.dp), contentAlignment = Alignment.Center) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth().scale(scaleX = 1f, scaleY = 0.82f)
        )
    }
}

@Composable
internal fun CompactSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = Modifier.height(32.dp).scale(0.82f)
    )
}

@Composable
private fun ReaderProgressOverlay(
    progression: Double,
    showTime: Boolean,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    chapterPath: String = ""
) {
    val safeProgress = progression.toFloat().coerceIn(0f, 1f)
    var nowText by remember { mutableStateOf(formatNow()) }
    LaunchedEffect(showTime) {
        if (showTime) {
            while (true) {
                nowText = formatNow()
                kotlinx.coroutines.delay(30_000L)
            }
        }
    }
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
                .background(contentColor.copy(alpha = 0.22f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(safeProgress)
                    .background(contentColor)
            )
        }
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = chapterPath,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 56.dp)
            )
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (showTime) {
                    Text(
                        text = nowText,
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.7f)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${(safeProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.7f)
                )
            }
        }
    }
}

private fun formatNow(): String {
    val now = java.time.LocalTime.now()
    return String.format(java.util.Locale.US, "%02d:%02d", now.hour, now.minute)
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}

private fun FragmentActivity.showSystemBarsAfterReader() {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
        window.insetsController?.show(AndroidWindowInsets.Type.statusBars())
    } else {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }
}

private fun FragmentActivity.hideReaderStatusBar() {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
        window.insetsController?.hide(AndroidWindowInsets.Type.statusBars())
    }
}

private fun lightColorFromSlider(value: Float): Long = hsvToRgb(value.coerceIn(0f, 1f) * 360f, 0.16f, 0.98f)

private fun darkColorFromSlider(value: Float): Long = hsvToRgb(value.coerceIn(0f, 1f) * 360f, 0.45f, 0.42f)

private fun Long.lightHueValue(): Float = hueValue(default = 42f)

private fun Long.darkHueValue(): Float = hueValue(default = 220f)

private fun Long.hueValue(default: Float): Float {
    if (this == 0L) return default / 360f
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV((0xFF000000 or (this and 0xFFFFFF)).toInt(), hsv)
    return (hsv[0] / 360f).coerceIn(0f, 1f)
}

private fun hsvToRgb(hue: Float, saturation: Float, value: Float): Long {
    val color = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value))
    return (color and 0xFFFFFF).toLong()
}
