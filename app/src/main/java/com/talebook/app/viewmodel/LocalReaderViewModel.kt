package com.talebook.app.viewmodel

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.talebook.app.data.local.ReaderAnnotationEntity
import com.talebook.app.data.local.ReaderBookmarkEntity
import com.talebook.app.data.local.ReaderDatabase
import com.talebook.app.data.local.ReadingProgressEntity
import com.talebook.app.data.local.RecentReadingEntity
import com.talebook.app.data.repository.BookRepository
import com.talebook.app.data.repository.LocalLibraryRepository
import com.talebook.app.data.repository.ReaderCacheRepository
import com.talebook.app.data.repository.SettingsRepository
import com.talebook.app.reader.EpubReadiumSession
import com.talebook.app.reader.LocatorProgress
import com.talebook.app.reader.PdfReadiumSession
import com.talebook.app.reader.ReaderDisplaySettings
import com.talebook.app.reader.ReaderFontFamily
import com.talebook.app.reader.ReaderPageAnimation
import com.talebook.app.reader.ReaderPageTurnMode
import com.talebook.app.reader.ReaderScrollTapSpeed
import com.talebook.app.reader.ReaderTheme
import com.talebook.app.reader.ReadiumEngine
import com.talebook.app.reader.ReadiumSessionStore
import com.talebook.app.reader.ReadiumUiEvents
import com.talebook.app.reader.TtsController
import com.talebook.app.reader.TtsPlaybackService
import com.talebook.app.ui.theme.ThemePresets
import com.talebook.app.util.TxtToEpubConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.readium.adapter.pdfium.navigator.PdfiumEngineProvider
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.pdf.PdfNavigatorFactory
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.allAreHtml
import org.readium.r2.shared.publication.services.content.Content
import org.readium.r2.shared.publication.services.content.content
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.archive.archive
import org.readium.r2.shared.util.http.HttpError
import java.io.File
import java.io.IOException
import java.net.URI

private const val LARGE_EMBEDDED_FONT_BYTES = 5L * 1024L * 1024L
private const val LOCAL_RECENT_SERVER_ID = "local"

data class LocalReaderUiState(
    val isLoading: Boolean = false,
    val title: String = "",
    val cachedPath: String = "",
    val sourceUri: String = "",
    val format: String = "",
    val sourceKind: String = RecentReadingEntity.SOURCE_KIND_LIBRARY,
    val isCached: Boolean = false,
    val isCaching: Boolean = false,
    val sessionId: Long? = null,
    val statusMessage: String = "",
    val error: String? = null,
    val tableOfContents: List<ReaderTocItem> = emptyList(),
    val currentChapterPath: String = "",
    val bookmarks: List<ReaderBookmarkEntity> = emptyList(),
    val annotations: List<ReaderAnnotationEntity> = emptyList(),
    val searchResults: List<ReaderSearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val isFullscreen: Boolean = false,
    val pageCount: Int = 0,
    val ttsState: ReaderTtsState = ReaderTtsState(),
    val readerSettings: ReaderDisplaySettings = ReaderDisplaySettings(
        fontFamily = ReaderFontFamily.DEFAULT,
        fontScale = 1.0f,
        lineHeight = 1.5f,
        brightness = 1.0f,
        scrollMode = false,
        useSystemBrightness = true,
        theme = ReaderTheme.SYSTEM,
        tapPageTurn = true,
        appDark = false,
        pageTurnMode = ReaderPageTurnMode.INVERTED_L,
        pageMargins = 1.0f,
        pageMarginVertical = 0f,
        paragraphSpacing = 1.0f,
        publisherStyles = true,
        forcePublisherFonts = false,
        keepScreenOn = false,
        pageAnimation = ReaderPageAnimation.SMOOTH,
        forceTapAnimation = true,
        scrollTapPageTurn = ReaderScrollTapSpeed.MEDIUM,
        scrollKeepLine = true,
        volumeKeyPageTurn = false,
        letterSpacing = 0f,
        readerBackgroundColor = 0x00000000L,
        readerTextColor = 0x00000000L,
        customThemeEnabled = false,
        twoPageMode = false,
        customFontPath = "",
        customFontName = ""
    )
)

data class ReaderTtsState(
    val isPanelVisible: Boolean = false,
    val isLoading: Boolean = false,
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val currentText: String = "",
    val status: String = "",
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val voiceName: String = "",
    val voices: List<String> = emptyList(),
    val sleepEnabled: Boolean = true,
    val sleepMinutes: Int = 30,
    val currentIndex: Int = 0,
    val total: Int = 0
)

private data class TtsUtterance(
    val id: String,
    val text: String,
    val locatorJson: String,
    val progression: Double
)

data class ReaderSearchResult(
    val locatorJson: String,
    val text: String,
    val progression: Double
)

data class ReaderTocItem(
    val title: String,
    val href: String,
    val level: Int
)

class LocalReaderViewModel : ViewModel() {
    private val bookRepository = BookRepository()
    private val cacheRepository = ReaderCacheRepository()
    private val _uiState = MutableStateFlow(LocalReaderUiState())
    val uiState: StateFlow<LocalReaderUiState> = _uiState.asStateFlow()

    init {
    }

    private var currentBookId: Int = 0
    private var currentLocalBookId: Long = 0L
    private var currentServerId: String = SettingsRepository.DEFAULT_SERVER_ID
    private var currentSessionServerId: String = SettingsRepository.DEFAULT_SERVER_ID
    private var currentSessionBookId: Int = 0
    private var currentSourceKind: String = RecentReadingEntity.SOURCE_KIND_LIBRARY
    private var currentSourceLabel: String = ""
    private var tempLocalFile: File? = null
    private var tempLocalEpubFile: File? = null
    private var ttsController: TtsController? = null
    private var ttsQueue: List<TtsUtterance> = emptyList()
    private var ttsIndex = 0
    private var ttsGeneration = 0
    private var sleepStopAtMillis = 0L

    fun load(context: Context, bookId: Int) {
        appContext = context.applicationContext
        if (_uiState.value.isLoading && currentBookId == bookId) return
        currentBookId = bookId
        viewModelScope.launch {
            currentServerId = SettingsRepository(context).activeLibraryServerId.first()
            currentSessionServerId = currentServerId
            currentSessionBookId = bookId
            val server = SettingsRepository(context).activeLibraryServer.first()
            currentSourceKind = RecentReadingEntity.SOURCE_KIND_LIBRARY
            currentSourceLabel = server.name.ifBlank { server.baseUrl.removePrefix("https://").removePrefix("http://").substringBefore("/").substringBefore(":") }
            val dao = ReaderDatabase.get(context).readerDao()
            if (dao.getProgress(currentServerId, bookId) == null) {
                dao.saveProgress(
                    ReadingProgressEntity(
                        serverId = currentServerId,
                        bookId = bookId,
                        locatorJson = "",
                        progression = 0.0,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
            val detail = runCatching { bookRepository.getBookDetail(bookId).getOrNull() }.getOrNull()
            val now = System.currentTimeMillis()
            if (detail != null) {
                val existingRecent = dao.getRecentEntry(currentServerId, bookId)
                dao.upsertRecentEntry(
                    RecentReadingEntity(
                        serverId = currentServerId,
                        bookId = bookId,
                        title = detail.title,
                        author = detail.authorSort.ifBlank { detail.authors.joinToString(", ") },
                        cover = detail.cover,
                        img = detail.img,
                        thumb = detail.thumb,
                        progression = dao.getProgress(currentServerId, bookId)?.progression ?: 0.0,
                        updatedAt = now,
                        sortIndex = now,
                        pinned = existingRecent?.pinned ?: false,
                        pinnedAt = existingRecent?.pinnedAt ?: 0L,
                        sourceKind = existingRecent?.sourceKind ?: RecentReadingEntity.SOURCE_KIND_LIBRARY,
                        sourceLabel = existingRecent?.sourceLabel?.takeIf { it.isNotBlank() } ?: currentSourceLabel
                    )
                )
            }
            _uiState.value = LocalReaderUiState(
                isLoading = true,
                statusMessage = "正在准备阅读源..."
            )
            bookRepository.getBookDetail(bookId).fold(
                onSuccess = { book ->
                    _uiState.update {
                        it.copy(title = book.title, statusMessage = "正在打开 ${book.title}...")
                    }
                    cacheRepository.resolveReadableSource(context, book).fold(
                        onSuccess = { source ->
                            val settingsRepository = SettingsRepository(context)
                            val appTheme = settingsRepository.themeMode.first()
                            val systemDark = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
                            val appDark = ThemePresets.isDark(appTheme, systemDark)
                            val palette = ThemePresets.palette(
                                mode = appTheme,
                                systemDark = systemDark,
                                dayPreset = settingsRepository.dayThemePreset.first(),
                                nightPreset = settingsRepository.nightThemePreset.first(),
                                customBackground = settingsRepository.dayCustomBackground.first(),
                                customText = settingsRepository.dayCustomText.first()
                            )
                            val readerTheme = settingsRepository.readerTheme.first().toReaderTheme()
                            val readerSettings = ReaderDisplaySettings(
                                fontFamily = settingsRepository.readerFontFamily.first().toReaderFontFamily(),
                                fontScale = settingsRepository.readerFontScale.first(),
                                lineHeight = settingsRepository.readerLineHeight.first(),
                                brightness = settingsRepository.readerBrightness.first(),
                                scrollMode = settingsRepository.readerScrollMode.first(),
                                useSystemBrightness = settingsRepository.readerUseSystemBrightness.first(),
                                theme = readerTheme,
                                tapPageTurn = settingsRepository.readerTapPageTurn.first(),
                                appDark = appDark,
                                pageTurnMode = settingsRepository.readerPageTurnMode.first().toReaderPageTurnMode(),
                                pageMargins = settingsRepository.readerPageMargins.first(),
                                pageMarginVertical = settingsRepository.readerPageMarginVertical.first(),
                                paragraphSpacing = settingsRepository.readerParagraphSpacing.first(),
                                publisherStyles = settingsRepository.readerPublisherStyles.first(),
                                forcePublisherFonts = settingsRepository.readerForcePublisherFonts.first(),
                                keepScreenOn = settingsRepository.readerKeepScreenOn.first(),
                                pageAnimation = settingsRepository.readerPageAnimation.first().toReaderPageAnimation(),
                                forceTapAnimation = settingsRepository.readerForceTapAnimation.first(),
                                scrollTapPageTurn = settingsRepository.readerScrollTapPageTurn.first(),
                                scrollKeepLine = settingsRepository.readerScrollKeepLine.first(),
                                volumeKeyPageTurn = settingsRepository.readerVolumeKeyPageTurn.first(),
                                letterSpacing = settingsRepository.readerLetterSpacing.first(),
                                dayPresetId = settingsRepository.dayThemePreset.first(),
                                nightPresetId = settingsRepository.nightThemePreset.first(),
                                readerBackgroundColor = palette.background,
                                readerTextColor = palette.text,
                                customThemeEnabled = true,
                                twoPageMode = settingsRepository.readerTwoPageMode.first(),
                                customFontPath = settingsRepository.readerCustomFontPath.first(),
                                customFontName = settingsRepository.readerCustomFontName.first(),
                            )
                            val ttsSettings = ReaderTtsState(
                                speechRate = settingsRepository.ttsSpeechRate.first(),
                                pitch = settingsRepository.ttsPitch.first(),
                                voiceName = settingsRepository.ttsVoiceName.first(),
                                sleepEnabled = settingsRepository.ttsSleepEnabled.first(),
                                sleepMinutes = settingsRepository.ttsSleepMinutes.first().coerceIn(5, 180)
                            )
                            val bookmarks = dao.getBookmarks(currentServerId, bookId)
                            val annotations = dao.getAnnotations(currentServerId, bookId)
                            val session = openReadiumSession(context, bookId, currentServerId, source.uri, readerSettings)
                            val tableOfContents = session.getOrNull()
                                ?.let { ReadiumSessionStore.get(it)?.publication?.tableOfContents }
                                ?.flattenToc()
                                .orEmpty()
                            val pageCount = session.getOrNull()
                                ?.let { ReadiumSessionStore.get(it)?.publication?.readingOrder?.size }
                                ?: 0
                            _uiState.value = LocalReaderUiState(
                                isLoading = false,
                                title = book.title,
                                cachedPath = if (source.isCached) source.uri else "",
                                sourceUri = source.uri,
                                format = source.format,
                                sourceKind = RecentReadingEntity.SOURCE_KIND_LIBRARY,
                                isCached = source.isCached,
                                sessionId = session.getOrNull(),
                                statusMessage = if (session.isFailure) {
                                    session.exceptionOrNull()?.message ?: "Readium 无法打开内容"
                                } else if (source.isCached) {
                                    "使用本地缓存 ${source.format.uppercase()} ${(source.sizeBytes / 1024.0 / 1024.0).formatMb()} MB"
                                } else {
                                    "流式阅读 ${source.format.uppercase()}，未缓存"
                                },
                                tableOfContents = tableOfContents,
                                pageCount = pageCount,
                                bookmarks = bookmarks,
                                annotations = annotations,
                                ttsState = ttsSettings,
                                readerSettings = readerSettings
                            )
                            maybeAutoCache(context, bookId)
                        },
                        onFailure = { e ->
                            _uiState.value = LocalReaderUiState(
                                isLoading = false,
                                title = book.title,
                                error = friendlyOpenError(e)
                            )
                        }
                    )
                },
                onFailure = { e ->
                    _uiState.value = LocalReaderUiState(
                        isLoading = false,
                        error = friendlyOpenError(e)
                    )
                }
            )
        }
    }

    fun cacheCurrentBook(context: Context) {
        val bookId = currentBookId
        if (bookId <= 0 || _uiState.value.isCaching || _uiState.value.isCached) return
        viewModelScope.launch {
            _uiState.update { it.copy(isCaching = true, statusMessage = "正在缓存本书...") }
            bookRepository.getBookDetail(bookId).fold(
                onSuccess = { book ->
                    cacheRepository.cacheBook(context, book).fold(
                        onSuccess = { cache ->
                            val limitMb = SettingsRepository(context).readerCacheLimitMb.first()
                            cacheRepository.trimToSize(context, limitMb * 1024L * 1024L)
                            _uiState.update {
                                it.copy(
                                    isCaching = false,
                                    isCached = true,
                                    cachedPath = cache.file.toURI().toString(),
                                    sourceUri = cache.file.toURI().toString(),
                                    format = cache.format,
                                    statusMessage = "缓存完成 ${cache.format.uppercase()} ${(cache.sizeBytes / 1024.0 / 1024.0).formatMb()} MB"
                                )
                            }
                        },
                        onFailure = { e ->
                            _uiState.update {
                                it.copy(isCaching = false, statusMessage = e.message ?: "缓存失败")
                            }
                        }
                    )
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(isCaching = false, statusMessage = e.message ?: "加载书籍失败")
                    }
                }
            )
        }
    }

    private suspend fun maybeAutoCache(context: Context, bookId: Int) {
        if (_uiState.value.isCached) return
        val settings = SettingsRepository(context)
        if (!settings.readerAutoCacheOnWifi.first()) return
        if (!isWifiConnected(context)) return
        cacheCurrentBook(context)
    }

    fun loadLocalBook(context: Context, localBookId: Long) {
        appContext = context.applicationContext
        if (_uiState.value.isLoading && currentLocalBookId == localBookId) return
        currentBookId = localBookId.toInt().coerceAtLeast(0)
        currentLocalBookId = localBookId
        viewModelScope.launch(Dispatchers.IO) {
            cleanupOldLocalTempFiles(context)
        }
        viewModelScope.launch {
            currentSourceKind = RecentReadingEntity.SOURCE_KIND_LOCAL
            val repo = LocalLibraryRepository(context)
            val book = repo.getBook(localBookId) ?: run {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "本地书不存在或已移除"
                )
                return@launch
            }
            currentSourceLabel = repo.listFolders().firstOrNull { it.id == book.folderId }?.displayName.orEmpty()
            val dao = ReaderDatabase.get(context).readerDao()
            val serverId = LOCAL_RECENT_SERVER_ID
            val recentBookId = -localBookId.toInt()
            if (dao.getProgress(serverId, recentBookId) == null) {
                dao.saveProgress(
                    ReadingProgressEntity(
                        serverId = serverId,
                        bookId = recentBookId,
                        locatorJson = "",
                        progression = 0.0,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
            val existingRecent = dao.getRecentEntry(serverId, recentBookId)
            val now = System.currentTimeMillis()
            dao.upsertRecentEntry(
                RecentReadingEntity(
                    serverId = serverId,
                    bookId = recentBookId,
                    title = book.displayName,
                    author = "",
                    cover = "",
                    img = "",
                    thumb = "",
                    progression = dao.getProgress(serverId, recentBookId)?.progression ?: 0.0,
                    updatedAt = now,
                    sortIndex = now,
                    pinned = existingRecent?.pinned ?: false,
                    pinnedAt = existingRecent?.pinnedAt ?: 0L,
                    sourceKind = RecentReadingEntity.SOURCE_KIND_LOCAL,
                    sourceLabel = currentSourceLabel
                )
            )
            if (book.format != "epub" && book.format != "pdf" && book.format != "txt") {
                _uiState.value = LocalReaderUiState(
                    isLoading = false,
                    title = book.displayName,
                    error = "暂不支持本地打开 ${book.format.uppercase()} 文件，可使用支持此格式的阅读器。"
                )
                return@launch
            }
            _uiState.value = LocalReaderUiState(
                isLoading = true,
                title = book.displayName,
                statusMessage = "正在准备本地阅读源..."
            )
            runCatching {
                val tempDir = File(context.cacheDir, "local_books").apply { mkdirs() }
                val readUri = if (book.format == "txt") {
                    _uiState.value = _uiState.value.copy(statusMessage = "正在准备 TXT 阅读缓存...")
                    val epubFile = withContext(Dispatchers.IO) {
                        prepareTxtEpubCache(context, book, localBookId, tempDir)
                    }
                    tempLocalEpubFile = epubFile
                    epubFile.toURI().toString()
                } else {
                    val tempFile = File(tempDir, "local_${localBookId}.${book.format}")
                    context.contentResolver.openInputStream(book.documentUri).use { input ->
                        requireNotNull(input) { "无法读取本地文件" }
                        tempFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    tempLocalFile = tempFile
                    tempFile.toURI().toString()
                }
                repo.touchBook(localBookId)
                currentSessionServerId = LOCAL_RECENT_SERVER_ID
                currentSessionBookId = recentBookId
                openReadiumSession(
                    context = context,
                    bookId = recentBookId,
                    serverId = LOCAL_RECENT_SERVER_ID,
                    uri = readUri,
                    readerSettings = buildLocalReaderSettings(context)
                )
            }.fold(
                onSuccess = { sessionResult ->
                    val session = sessionResult.getOrNull()
                    val toc = session?.let { ReadiumSessionStore.get(it)?.publication?.tableOfContents }?.flattenToc().orEmpty()
                    val pages = session?.let { ReadiumSessionStore.get(it)?.publication?.readingOrder?.size } ?: 0
                    val bookmarks = dao.getBookmarks(LOCAL_RECENT_SERVER_ID, recentBookId)
                    val annotations = dao.getAnnotations(LOCAL_RECENT_SERVER_ID, recentBookId)
                    _uiState.value = LocalReaderUiState(
                        isLoading = false,
                        title = book.displayName,
                        cachedPath = (tempLocalEpubFile ?: tempLocalFile)?.toURI().toString().orEmpty(),
                        sourceUri = (tempLocalEpubFile ?: tempLocalFile)?.toURI().toString().orEmpty(),
                        format = book.format,
                        sourceKind = RecentReadingEntity.SOURCE_KIND_LOCAL,
                        isCached = false,
                        sessionId = session,
                        statusMessage = "本地阅读 ${book.format.uppercase()} ${(book.sizeBytes / 1024.0 / 1024.0).formatMb()} MB",
                        tableOfContents = toc,
                        pageCount = pages,
                        bookmarks = bookmarks,
                        annotations = annotations,
                        ttsState = ReaderTtsState(
                            speechRate = 1.0f,
                            pitch = 1.0f,
                            voiceName = "",
                            sleepEnabled = false,
                            sleepMinutes = 30
                        ),
                        readerSettings = buildLocalReaderSettings(context)
                    )
                },
                onFailure = { e ->
                    _uiState.value = LocalReaderUiState(
                        isLoading = false,
                        title = book.displayName,
                        error = e.message ?: "Readium 无法打开本地文件"
                    )
                }
            )
        }
    }

    private suspend fun buildLocalReaderSettings(context: Context): ReaderDisplaySettings {
        val settingsRepository = SettingsRepository(context)
        val appTheme = settingsRepository.themeMode.first()
        val systemDark = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val appDark = ThemePresets.isDark(appTheme, systemDark)
        val palette = ThemePresets.palette(
            mode = appTheme,
            systemDark = systemDark,
            dayPreset = settingsRepository.dayThemePreset.first(),
            nightPreset = settingsRepository.nightThemePreset.first(),
            customBackground = settingsRepository.dayCustomBackground.first(),
            customText = settingsRepository.dayCustomText.first()
        )
        val readerTheme = settingsRepository.readerTheme.first().toReaderTheme()
        return ReaderDisplaySettings(
            fontFamily = settingsRepository.readerFontFamily.first().toReaderFontFamily(),
            fontScale = settingsRepository.readerFontScale.first(),
            lineHeight = settingsRepository.readerLineHeight.first(),
            brightness = settingsRepository.readerBrightness.first(),
            scrollMode = settingsRepository.readerScrollMode.first(),
            useSystemBrightness = settingsRepository.readerUseSystemBrightness.first(),
            theme = readerTheme,
            tapPageTurn = settingsRepository.readerTapPageTurn.first(),
            appDark = appDark,
            pageTurnMode = settingsRepository.readerPageTurnMode.first().toReaderPageTurnMode(),
pageMargins = settingsRepository.readerPageMargins.first(),
            pageMarginVertical = settingsRepository.readerPageMarginVertical.first(),
            paragraphSpacing = settingsRepository.readerParagraphSpacing.first(),
            publisherStyles = settingsRepository.readerPublisherStyles.first(),
            forcePublisherFonts = settingsRepository.readerForcePublisherFonts.first(),
            keepScreenOn = settingsRepository.readerKeepScreenOn.first(),
            pageAnimation = settingsRepository.readerPageAnimation.first().toReaderPageAnimation(),
            forceTapAnimation = settingsRepository.readerForceTapAnimation.first(),
            scrollTapPageTurn = settingsRepository.readerScrollTapPageTurn.first(),
            scrollKeepLine = settingsRepository.readerScrollKeepLine.first(),
            volumeKeyPageTurn = settingsRepository.readerVolumeKeyPageTurn.first(),
            letterSpacing = settingsRepository.readerLetterSpacing.first(),
            dayPresetId = settingsRepository.dayThemePreset.first(),
            nightPresetId = settingsRepository.nightThemePreset.first(),
            readerBackgroundColor = palette.background,
            readerTextColor = palette.text,
            customThemeEnabled = true,
            twoPageMode = settingsRepository.readerTwoPageMode.first(),
            customFontPath = settingsRepository.readerCustomFontPath.first(),
            customFontName = settingsRepository.readerCustomFontName.first(),
        )
    }

    private var localTempCleanupDone = false

    private fun prepareTxtEpubCache(context: Context, book: com.talebook.app.data.repository.LocalBook, localBookId: Long, tempDir: File): File {
        val sourceSize = book.sizeBytes.takeIf { it > 0L } ?: querySourceSize(context, book.documentUri)
        val sourceModified = querySourceLastModified(context, book.documentUri)
        val fingerprint = "${sourceSize.coerceAtLeast(0L)}_${sourceModified.coerceAtLeast(0L)}"
        val epubFile = File(tempDir, "txt_${localBookId}_$fingerprint.epub")
        val metaFile = File(tempDir, "txt_${localBookId}.json")
        val cached = readTxtCacheMeta(metaFile)

        if (cached != null &&
            cached.optInt("converterVersion") == TxtToEpubConverter.CONVERTER_VERSION &&
            cached.optString("sourceUri") == book.documentUri.toString() &&
            cached.optLong("sourceSize") == sourceSize &&
            cached.optLong("sourceModified") == sourceModified
        ) {
            val cachedFile = File(tempDir, cached.optString("epubName"))
            if (cachedFile.exists() && cachedFile.length() > 0L) {
                cachedFile.setLastModified(System.currentTimeMillis())
                return cachedFile
            }
        }

        tempDir.listFiles { file -> file.name.startsWith("txt_${localBookId}_") && file.extension == "epub" }
            ?.forEach { runCatching { it.delete() } }

        context.contentResolver.openInputStream(book.documentUri).use { input ->
            requireNotNull(input) { "无法读取本地 TXT 文件" }
            val result = TxtToEpubConverter.convertFromStream(
                input = input,
                title = book.displayName,
                outputEpubFile = epubFile,
                sourceSizeBytes = sourceSize
            )
            writeTxtCacheMeta(
                metaFile,
                JSONObject()
                    .put("converterVersion", TxtToEpubConverter.CONVERTER_VERSION)
                    .put("sourceUri", book.documentUri.toString())
                    .put("sourceSize", sourceSize)
                    .put("sourceModified", sourceModified)
                    .put("epubName", epubFile.name)
                    .put("encoding", result.encoding)
                    .put("chapterCount", result.chapterCount)
                    .put("contentCount", result.contentCount)
                    .put("createdAt", System.currentTimeMillis())
            )
        }
        return epubFile
    }

    private fun readTxtCacheMeta(file: File): JSONObject? {
        if (!file.exists()) return null
        return runCatching { JSONObject(file.readText(Charsets.UTF_8)) }.getOrNull()
    }

    private fun writeTxtCacheMeta(file: File, json: JSONObject) {
        file.writeText(json.toString(), Charsets.UTF_8)
    }

    private fun querySourceSize(context: Context, uri: android.net.Uri): Long {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (idx >= 0 && !cursor.isNull(idx)) cursor.getLong(idx) else 0L
                } else 0L
            } ?: 0L
        }.getOrDefault(0L)
    }

    private fun querySourceLastModified(context: Context, uri: android.net.Uri): Long {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                    if (idx >= 0 && !cursor.isNull(idx)) cursor.getLong(idx) else 0L
                } else 0L
            } ?: 0L
        }.getOrDefault(0L)
    }

    private fun cleanupOldLocalTempFiles(context: Context) {
        if (localTempCleanupDone) return
        localTempCleanupDone = true
        val dir = File(context.cacheDir, "local_books")
        if (!dir.exists()) return
        val cutoff = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        dir.listFiles()?.forEach { file ->
            if (file.lastModified() < cutoff) {
                runCatching { file.delete() }
            }
        }
    }

    private fun friendlyOpenError(e: Throwable): String {
    val raw = e.message.orEmpty().trim()
    val isNetwork = e is HttpError.IO ||
        e is HttpError.Unreachable ||
        e is HttpError.Timeout ||
        e is HttpError.SslHandshake ||
        e is HttpError.Redirection ||
        e is IOException
    val isAuth = raw.contains("401") || raw.contains("403") || raw.contains("Unauthorized", ignoreCase = true)
    return when {
        isAuth && !isNetwork -> "需要登录当前书库或访问受限"
        isNetwork -> "无法连接服务器，请检查网络"
        raw.isBlank() -> "Readium 无法读取书籍资源"
        else -> raw
    }
}

private suspend fun openReadiumSession(
        context: Context,
        bookId: Int,
        serverId: String,
        uri: String,
        readerSettings: ReaderDisplaySettings
    ): Result<Long> {
        return runCatching {
            val engine = ReadiumEngine(context)
            val dao = ReaderDatabase.get(context).readerDao()
            var initialLocator = dao.getProgress(serverId, bookId)?.locatorJson?.let { json ->
                if (json.isBlank()) null else runCatching { Locator.fromJSON(JSONObject(json)) }.getOrNull()
            }
            val isRemote = !uri.startsWith("file:")
            val asset = if (!isRemote) {
                engine.assetRetriever.retrieve(File(URI(uri))).getOrNull()
            } else {
                val absoluteUrl = AbsoluteUrl(uri) ?: error("无效的阅读地址")
                engine.assetRetriever.retrieve(absoluteUrl).getOrNull()
            } ?: error("Readium 无法读取书籍资源")

            val publication = engine.publicationOpener.open(asset, allowUserInteraction = true).getOrNull()
                ?: error("Readium 无法解析书籍")
            val progressRow = dao.getProgress(serverId, bookId)
            if (progressRow != null) {
                val needsHeal = initialLocator == null ||
                    LocatorProgress.looksBareLocatorJson(progressRow.locatorJson) ||
                    (progressRow.progression <= 0.0 && (initialLocator?.locations?.totalProgression ?: 0.0) <= 0.0)
                if (needsHeal && initialLocator != null) {
                    val estimated = LocatorProgress.estimateTotalProgression(publication, initialLocator)
                    val fallback = if (progressRow.progression > 0.0) progressRow.progression else estimated
                    if (fallback != null && fallback > 0.0) {
                        val healed = LocatorProgress.withTotalProgression(initialLocator, fallback)
                        initialLocator = healed
                        dao.saveProgress(
                            progressRow.copy(
                                locatorJson = healed.toJSON().toString(),
                                progression = fallback,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                        dao.getRecentEntry(serverId, bookId)?.let { recent ->
                            if (recent.progression <= 0.0) {
                                dao.upsertRecentEntry(
                                    recent.copy(
                                        progression = fallback,
                                        updatedAt = System.currentTimeMillis()
                                    )
                                )
                            }
                        }
                    }
                }
            }
            val hasLargeEmbeddedFonts = isRemote && hasLargeEmbeddedFonts(publication)
            val sessionId = ReadiumSessionStore.nextId()
            val session = when {
                publication.conformsTo(Publication.Profile.EPUB) || publication.readingOrder.allAreHtml -> {
                    EpubReadiumSession(
                        id = sessionId,
                        bookId = bookId,
                        serverId = serverId,
                        publication = publication,
                        initialLocator = initialLocator,
                        isRemote = isRemote,
                        hasLargeEmbeddedFonts = hasLargeEmbeddedFonts,
                        navigatorFactory = EpubNavigatorFactory(publication),
                        displaySettings = readerSettings
                    )
                }
                publication.conformsTo(Publication.Profile.PDF) -> {
                    val pdfEngine = PdfiumEngineProvider()
                    PdfReadiumSession(
                        id = sessionId,
                        bookId = bookId,
                        serverId = serverId,
                        publication = publication,
                        initialLocator = initialLocator,
                        isRemote = isRemote,
                        hasLargeEmbeddedFonts = false,
                        navigatorFactory = PdfNavigatorFactory(publication, pdfEngine),
                        pdfEngineProvider = pdfEngine,
                        displaySettings = readerSettings
                    )
                }
                else -> {
                    publication.close()
                    error("当前格式暂不支持本地阅读")
                }
            }
            ReadiumSessionStore.put(session)
        }
    }

    private suspend fun hasLargeEmbeddedFonts(publication: Publication): Boolean {
        return publication.resources.any { link ->
            val href = link.href.toString().lowercase()
            val mediaType = link.mediaType.toString().lowercase()
            val isFont = href.endsWith(".ttf") || href.endsWith(".otf") || href.endsWith(".woff") || href.endsWith(".woff2") ||
                mediaType.contains("font") || mediaType.contains("opentype") || mediaType.contains("truetype")
            if (!isFont) {
                false
            } else {
                publication.get(link)
                    ?.properties()
                    ?.getOrNull()
                    ?.archive
                    ?.entryLength
                    ?.let { it >= LARGE_EMBEDDED_FONT_BYTES }
                    ?: false
            }
        }
    }

    private fun isWifiConnected(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    fun toggleFullscreen() {
        _uiState.update { it.copy(isFullscreen = !it.isFullscreen) }
    }

    fun addBookmark() {
        _uiState.value.sessionId?.let { ReadiumUiEvents.emitAddBookmark(it) }
    }

    fun goToBookmark(bookmark: ReaderBookmarkEntity) {
        _uiState.value.sessionId?.let { sessionId ->
            ReadiumUiEvents.emitReaderJump(sessionId, bookmark.locatorJson)
        }
    }

    fun deleteBookmark(bookmark: ReaderBookmarkEntity) {
        val serverId = currentSessionServerId
        val bookId = currentSessionBookId
        viewModelScope.launch {
            val dao = ReaderDatabase.get(appContext ?: return@launch).readerDao()
            dao.deleteBookmark(bookmark)
            _uiState.update { it.copy(bookmarks = dao.getBookmarks(serverId, bookId)) }
        }
    }

    fun renameBookmark(bookmark: ReaderBookmarkEntity, title: String) {
        val context = appContext ?: return
        val serverId = currentSessionServerId
        val bookId = currentSessionBookId
        val normalized = title.trim().take(120).ifBlank { "书签" }
        viewModelScope.launch {
            val dao = ReaderDatabase.get(context).readerDao()
            dao.renameBookmark(bookmark.id, normalized)
            _uiState.update { it.copy(bookmarks = dao.getBookmarks(serverId, bookId)) }
        }
    }

    fun addNote(note: String) {
        val normalized = note.trim()
        if (normalized.isBlank()) return
        _uiState.value.sessionId?.let { sessionId ->
            ReadiumUiEvents.emitAddNote(sessionId, normalized)
        }
    }

    fun goToAnnotation(annotation: ReaderAnnotationEntity) {
        _uiState.value.sessionId?.let { sessionId ->
            ReadiumUiEvents.emitReaderJump(sessionId, annotation.locatorJson)
        }
    }

    fun deleteAnnotation(annotation: ReaderAnnotationEntity) {
        val context = appContext ?: return
        val serverId = currentSessionServerId
        val bookId = currentSessionBookId
        viewModelScope.launch {
            val dao = ReaderDatabase.get(context).readerDao()
            dao.deleteAnnotation(annotation)
            _uiState.update { it.copy(annotations = dao.getAnnotations(serverId, bookId)) }
            _uiState.value.sessionId?.let { ReadiumUiEvents.emitAnnotationChanged(it) }
        }
    }

    fun updateAnnotation(annotation: ReaderAnnotationEntity, note: String) {
        val context = appContext ?: return
        val serverId = currentSessionServerId
        val bookId = currentSessionBookId
        val normalized = note.trim().take(2000)
        if (normalized.isBlank()) return
        viewModelScope.launch {
            val dao = ReaderDatabase.get(context).readerDao()
            dao.saveAnnotation(annotation.copy(note = normalized, updatedAt = System.currentTimeMillis()))
            _uiState.update { it.copy(annotations = dao.getAnnotations(serverId, bookId)) }
            _uiState.value.sessionId?.let { ReadiumUiEvents.emitAnnotationChanged(it) }
        }
    }

    fun goToProgress(progress: Double) {
        _uiState.value.sessionId?.let { sessionId ->
            ReadiumUiEvents.emitGoToProgress(sessionId, progress)
        }
    }

    fun goToPage(page: Int) {
        _uiState.value.sessionId?.let { sessionId ->
            ReadiumUiEvents.emitGoToPage(sessionId, page)
        }
    }

    fun updateReaderSettings(
        fontScale: Float = _uiState.value.readerSettings.fontScale,
        lineHeight: Float = _uiState.value.readerSettings.lineHeight,
        brightness: Float = _uiState.value.readerSettings.brightness,
        scrollMode: Boolean = _uiState.value.readerSettings.scrollMode,
        useSystemBrightness: Boolean = _uiState.value.readerSettings.useSystemBrightness,
        theme: ReaderTheme = _uiState.value.readerSettings.theme,
        tapPageTurn: Boolean = _uiState.value.readerSettings.tapPageTurn,
        fontFamily: ReaderFontFamily = _uiState.value.readerSettings.fontFamily,
        pageTurnMode: ReaderPageTurnMode = _uiState.value.readerSettings.pageTurnMode,
        pageMargins: Float = _uiState.value.readerSettings.pageMargins,
        pageMarginVertical: Float = _uiState.value.readerSettings.pageMarginVertical,
        paragraphSpacing: Float = _uiState.value.readerSettings.paragraphSpacing,
        letterSpacing: Float = _uiState.value.readerSettings.letterSpacing,
        publisherStyles: Boolean = _uiState.value.readerSettings.publisherStyles,
        forcePublisherFonts: Boolean = _uiState.value.readerSettings.forcePublisherFonts,
        keepScreenOn: Boolean = _uiState.value.readerSettings.keepScreenOn,
        pageAnimation: ReaderPageAnimation = _uiState.value.readerSettings.pageAnimation,
        forceTapAnimation: Boolean = _uiState.value.readerSettings.forceTapAnimation,
        scrollTapPageTurn: ReaderScrollTapSpeed = _uiState.value.readerSettings.scrollTapPageTurn,
        scrollKeepLine: Boolean = _uiState.value.readerSettings.scrollKeepLine,
        volumeKeyPageTurn: Boolean = _uiState.value.readerSettings.volumeKeyPageTurn,
        readerBackgroundColor: Long = _uiState.value.readerSettings.readerBackgroundColor,
        readerTextColor: Long = _uiState.value.readerSettings.readerTextColor,
        customThemeEnabled: Boolean = _uiState.value.readerSettings.customThemeEnabled,
        twoPageMode: Boolean = _uiState.value.readerSettings.twoPageMode,
        appDark: Boolean = _uiState.value.readerSettings.appDark,
        customFontPath: String = _uiState.value.readerSettings.customFontPath,
        customFontName: String = _uiState.value.readerSettings.customFontName,
        dayPresetId: String = _uiState.value.readerSettings.dayPresetId,
        nightPresetId: String = _uiState.value.readerSettings.nightPresetId,
        persist: Boolean = true,
    ) {
        val settings = ReaderDisplaySettings(
            fontFamily = fontFamily,
            fontScale = fontScale.coerceIn(0.5f, 3.0f),
            lineHeight = lineHeight.coerceIn(0.5f, 3.0f),
            brightness = brightness.coerceIn(0.0f, 1.0f),
            scrollMode = scrollMode,
            useSystemBrightness = useSystemBrightness,
            theme = theme,
            tapPageTurn = tapPageTurn,
            appDark = appDark,
            pageTurnMode = pageTurnMode,
            pageMargins = pageMargins.coerceIn(0f, 5.0f),
            pageMarginVertical = pageMarginVertical.coerceIn(0f, 10.0f),
            paragraphSpacing = paragraphSpacing.coerceIn(0f, 2f),
            letterSpacing = letterSpacing.coerceIn(-0.2f, 1f),
            publisherStyles = publisherStyles,
            forcePublisherFonts = forcePublisherFonts,
            keepScreenOn = keepScreenOn,
            pageAnimation = pageAnimation,
            forceTapAnimation = forceTapAnimation,
            scrollTapPageTurn = scrollTapPageTurn,
            scrollKeepLine = scrollKeepLine,
            volumeKeyPageTurn = volumeKeyPageTurn,
            readerBackgroundColor = readerBackgroundColor and 0xFFFFFFFFL,
            readerTextColor = readerTextColor and 0xFFFFFFFFL,
            customThemeEnabled = customThemeEnabled,
            twoPageMode = twoPageMode,
            customFontPath = customFontPath,
            customFontName = customFontName,
            dayPresetId = dayPresetId,
            nightPresetId = nightPresetId,
        )
        val previous = _uiState.value.readerSettings
        _uiState.update { it.copy(readerSettings = settings) }
        _uiState.value.sessionId?.let { sessionId ->
            val colorChanged = previous.readerBackgroundColor != settings.readerBackgroundColor ||
                previous.readerTextColor != settings.readerTextColor ||
                previous.customThemeEnabled != settings.customThemeEnabled ||
                previous.theme != settings.theme
            ReadiumUiEvents.emitReaderSettings(sessionId, settings)
            if (colorChanged) {
                ReadiumUiEvents.emitReaderThemeChanged(sessionId, settings)
            }
        }
        if (!persist) return
        viewModelScope.launch {
            val repo = SettingsRepository(appContext ?: return@launch)
            repo.saveReaderDisplaySettings(
                fontScale = settings.fontScale,
                fontFamily = settings.fontFamily.toStorageValue(),
                lineHeight = settings.lineHeight,
                brightness = settings.brightness,
                scrollMode = settings.scrollMode,
                useSystemBrightness = settings.useSystemBrightness,
                theme = settings.theme.toStorageValue(),
                tapPageTurn = settings.tapPageTurn,
                pageTurnMode = settings.pageTurnMode.toStorageValue(),
                pageMargins = settings.pageMargins,
                pageMarginVertical = settings.pageMarginVertical,
                paragraphSpacing = settings.paragraphSpacing,
                letterSpacing = settings.letterSpacing,
                publisherStyles = settings.publisherStyles,
                forcePublisherFonts = settings.forcePublisherFonts,
                keepScreenOn = settings.keepScreenOn,
                pageAnimation = settings.pageAnimation.toStorageValue(),
                forceTapAnimation = settings.forceTapAnimation,
                scrollTapPageTurn = settings.scrollTapPageTurn,
                scrollKeepLine = settings.scrollKeepLine,
                volumeKeyPageTurn = settings.volumeKeyPageTurn,
                twoPageMode = settings.twoPageMode,
                customFontPath = settings.customFontPath,
                customFontName = settings.customFontName
            )
            repo.saveReaderCustomColors(settings.readerBackgroundColor, settings.readerTextColor, settings.customThemeEnabled)
        }
    }

    fun persistPageMargins() {
        val settings = _uiState.value.readerSettings
        viewModelScope.launch {
            val repo = SettingsRepository(appContext ?: return@launch)
            repo.savePageMargins(settings.pageMargins, settings.pageMarginVertical)
        }
    }

    fun cycleReaderTheme() {
        val current = _uiState.value.readerSettings
        val nextTheme = when (current.theme) {
            ReaderTheme.SYSTEM -> ReaderTheme.LIGHT
            ReaderTheme.LIGHT -> ReaderTheme.SEPIA
            ReaderTheme.SEPIA -> ReaderTheme.DARK
            ReaderTheme.DARK -> ReaderTheme.PINK
            ReaderTheme.PINK -> ReaderTheme.BLUE
            ReaderTheme.BLUE -> ReaderTheme.GREEN
            ReaderTheme.GREEN -> ReaderTheme.SYSTEM
            ReaderTheme.CUSTOM -> ReaderTheme.SYSTEM
        }
        updateReaderTheme(nextTheme)
    }

    fun updateReaderTheme(theme: ReaderTheme) {
        val current = _uiState.value.readerSettings
        updateReaderSettings(
            fontScale = current.fontScale,
            lineHeight = current.lineHeight,
            brightness = current.brightness,
            scrollMode = current.scrollMode,
            useSystemBrightness = current.useSystemBrightness,
            theme = theme,
            tapPageTurn = current.tapPageTurn,
            fontFamily = current.fontFamily
        )
        val context = appContext ?: return
        viewModelScope.launch {
            val appTheme = when (theme) {
                ReaderTheme.SYSTEM -> SettingsRepository.THEME_AUTO
                ReaderTheme.LIGHT -> SettingsRepository.THEME_LIGHT
                ReaderTheme.DARK -> SettingsRepository.THEME_DARK
                ReaderTheme.SEPIA -> null
                ReaderTheme.PINK -> null
                ReaderTheme.BLUE -> null
                ReaderTheme.GREEN -> null
                ReaderTheme.CUSTOM -> null
            }
            if (appTheme != null) SettingsRepository(context).saveThemeMode(appTheme)
        }
    }

    fun updateCustomThemeColors(backgroundColor: Long, textColor: Long) {
        val current = _uiState.value.readerSettings
        updateReaderSettings(
            fontScale = current.fontScale,
            lineHeight = current.lineHeight,
            brightness = current.brightness,
            scrollMode = current.scrollMode,
            useSystemBrightness = current.useSystemBrightness,
            theme = current.theme,
            tapPageTurn = current.tapPageTurn,
            readerBackgroundColor = backgroundColor,
            readerTextColor = textColor,
            customThemeEnabled = true,
            appDark = current.appDark
        )
    }

    fun openSearch() {
        // UI owns the dialog; searchInBook performs the actual query.
    }

    fun searchInBook(query: String) {
        val sessionId = _uiState.value.sessionId ?: return
        val normalized = query.trim()
        if (normalized.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList()) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, searchResults = emptyList()) }
            val session = ReadiumSessionStore.get(sessionId)
            val results = session?.publication?.content()?.elements()
                ?.filterIsInstance<Content.TextualElement>()
                ?.mapNotNull { element ->
                    val text = element.text?.trim()?.replace(Regex("\\s+"), " ").orEmpty()
                    if (!text.contains(normalized, ignoreCase = true)) return@mapNotNull null
                    ReaderSearchResult(
                        locatorJson = element.locator.toJSON().toString(),
                        text = text.take(120),
                        progression = element.locator.locations.totalProgression ?: 0.0
                    )
                }
                ?.take(50)
                .orEmpty()
            _uiState.update { it.copy(isSearching = false, searchResults = results) }
        }
    }

    fun goToSearchResult(result: ReaderSearchResult) {
        _uiState.value.sessionId?.let { sessionId ->
            ReadiumUiEvents.emitGoToLocator(sessionId, result.locatorJson)
        }
    }

    fun goToTocItem(item: ReaderTocItem) {
        _uiState.value.sessionId?.let { sessionId ->
            ReadiumUiEvents.emitGoToLink(sessionId, item.href)
        }
    }

    fun startTts() {
        _uiState.update { it.copy(ttsState = it.ttsState.copy(isPanelVisible = true)) }
        if (_uiState.value.ttsState.isPlaying) return
        appContext?.let { TtsPlaybackService.start(it) }
        startTtsPlayback()
    }

    fun hideTtsPanel() {
        _uiState.update { it.copy(ttsState = it.ttsState.copy(isPanelVisible = false)) }
    }

    fun updateCurrentChapterPath(path: String) {
        _uiState.update { it.copy(currentChapterPath = path) }
    }

    fun pauseTts() {
        ttsController?.stop()
        _uiState.update { it.copy(ttsState = it.ttsState.copy(isPlaying = false, isPaused = true, status = "已暂停")) }
    }

    fun resumeTts() {
        if (ttsQueue.isEmpty()) {
            startTtsPlayback()
        } else {
            speakCurrentTts()
        }
    }

    private fun focusLossPause() {
        if (_uiState.value.ttsState.isPlaying) {
            pauseTts()
        }
    }

    private fun focusGainResume() {
        if (_uiState.value.ttsState.isPaused && _uiState.value.ttsState.isPanelVisible) {
            resumeTts()
        }
    }

    fun stopTts() {
        ttsController?.stop()
        sleepStopAtMillis = 0L
        _uiState.value.sessionId?.let { ReadiumUiEvents.emitTtsHighlight(it, "") }
        _uiState.update { it.copy(ttsState = it.ttsState.copy(isPlaying = false, isPaused = false, status = "已停止")) }
    }

    fun exitTts() {
        appContext?.let { TtsPlaybackService.stop(it) }
        ttsController?.shutdown()
        ttsController = null
        ttsQueue = emptyList()
        ttsIndex = 0
        ttsGeneration++
        sleepStopAtMillis = 0L
        _uiState.value.sessionId?.let { ReadiumUiEvents.emitTtsHighlight(it, "") }
        _uiState.update { it.copy(ttsState = it.ttsState.copy(isPanelVisible = false, isPlaying = false, isPaused = false, currentText = "", status = "", currentIndex = 0, total = 0)) }
    }

    fun previousTtsSentence() {
        if (ttsQueue.isEmpty()) return
        ttsIndex = (ttsIndex - 1).coerceAtLeast(0)
        speakCurrentTts()
    }

    fun nextTtsSentence() {
        if (ttsQueue.isEmpty()) return
        ttsIndex = (ttsIndex + 1).coerceAtMost(ttsQueue.lastIndex)
        speakCurrentTts()
    }

    fun updateTtsRate(rate: Float) {
        _uiState.update { it.copy(ttsState = it.ttsState.copy(speechRate = rate.coerceIn(0.5f, 2.0f))) }
        saveTtsSettings()
    }

    fun updateTtsPitch(pitch: Float) {
        _uiState.update { it.copy(ttsState = it.ttsState.copy(pitch = pitch.coerceIn(0.5f, 2.0f))) }
        saveTtsSettings()
    }

    fun updateTtsVoice(voiceName: String) {
        _uiState.update { it.copy(ttsState = it.ttsState.copy(voiceName = voiceName)) }
        saveTtsSettings()
    }

    fun updateTtsSleep(enabled: Boolean, minutes: Int = _uiState.value.ttsState.sleepMinutes) {
        val safeMinutes = minutes.coerceIn(5, 180)
        _uiState.update { it.copy(ttsState = it.ttsState.copy(sleepEnabled = enabled, sleepMinutes = safeMinutes)) }
        saveTtsSettings()
        sleepStopAtMillis = if (enabled && _uiState.value.ttsState.isPlaying) {
            System.currentTimeMillis() + safeMinutes * 60_000L
        } else {
            0L
        }
    }

    private fun startTtsPlayback() {
        val context = appContext ?: return
        val sessionId = _uiState.value.sessionId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(ttsState = it.ttsState.copy(isLoading = true, status = "正在准备朗读...")) }
            val session = ReadiumSessionStore.get(sessionId)
            val currentProgression = ReaderDatabase.get(context).readerDao().getProgress(currentServerId, currentBookId)?.progression ?: 0.0
            val queue = session?.publication?.content()?.elements()
                ?.filterIsInstance<Content.TextualElement>()
                ?.flatMapIndexed { elementIndex, element ->
                    val text = element.text?.trim()?.replace(Regex("\\s+"), " ").orEmpty()
                    if (text.isBlank()) return@flatMapIndexed emptyList()
                    val locatorJson = element.locator.toJSON().toString()
                    val progression = element.locator.locations.totalProgression ?: 0.0
                    splitTtsText(text).mapIndexed { sentenceIndex, sentence ->
                        TtsUtterance(
                            id = "tts-$elementIndex-$sentenceIndex",
                            text = sentence,
                            locatorJson = locatorJson,
                            progression = progression
                        )
                    }
                }
                .orEmpty()
            if (queue.isEmpty()) {
                _uiState.update { it.copy(ttsState = it.ttsState.copy(isLoading = false, isPlaying = false, isPaused = false, status = "本书不支持朗读")) }
                return@launch
            }
            ttsQueue = queue
            ttsIndex = queue.indexOfFirst { it.progression >= currentProgression }.takeIf { it >= 0 } ?: 0
            ttsController?.shutdown()
            val generation = ++ttsGeneration
            val controller = TtsController(
                context = context,
                onReady = {
                    if (ttsGeneration != generation || ttsQueue.isEmpty()) return@TtsController
                    _uiState.update { state ->
                        state.copy(ttsState = state.ttsState.copy(
                            isLoading = false,
                            total = ttsQueue.size,
                            voices = ttsController?.voices()?.map { it.name }.orEmpty()
                        ))
                    }
                    speakCurrentTts()
                },
                onStart = { utteranceId -> onTtsStart(utteranceId) },
                onDone = { utteranceId -> onTtsDone(utteranceId) },
                onError = { message -> onTtsError(message) },
                onFocusLoss = { focusLossPause() },
                onFocusGain = { focusGainResume() }
            )
            ttsController = controller
            if (_uiState.value.ttsState.sleepEnabled) {
                sleepStopAtMillis = System.currentTimeMillis() + _uiState.value.ttsState.sleepMinutes * 60_000L
            }
        }
    }

    private fun speakCurrentTts() {
        val utterance = ttsQueue.getOrNull(ttsIndex) ?: run {
            stopTts()
            return
        }
        _uiState.value.sessionId?.let { sessionId ->
            ReadiumUiEvents.emitGoToLocator(sessionId, utterance.locatorJson)
            ReadiumUiEvents.emitTtsHighlight(sessionId, utterance.locatorJson)
        }
        val state = _uiState.value.ttsState
        val ok = ttsController?.speak(utterance.text, utterance.id, state.speechRate, state.pitch, state.voiceName) == true
        if (!ok) {
            _uiState.update { it.copy(ttsState = it.ttsState.copy(status = "系统 TTS 未准备好，请稍后重试")) }
        }
    }

    private fun onTtsStart(utteranceId: String) {
        val index = ttsQueue.indexOfFirst { it.id == utteranceId }.takeIf { it >= 0 } ?: ttsIndex
        ttsIndex = index
        val utterance = ttsQueue.getOrNull(index) ?: return
        _uiState.update {
            it.copy(ttsState = it.ttsState.copy(
                isPlaying = true,
                isPaused = false,
                currentText = utterance.text,
                currentIndex = index + 1,
                total = ttsQueue.size,
                status = "正在朗读"
            ))
        }
    }

    private fun onTtsDone(utteranceId: String) {
        val doneIndex = ttsQueue.indexOfFirst { it.id == utteranceId }.takeIf { it >= 0 } ?: ttsIndex
        if (sleepStopAtMillis > 0L && System.currentTimeMillis() >= sleepStopAtMillis) {
            stopTts()
            _uiState.update { it.copy(ttsState = it.ttsState.copy(status = "睡眠定时已停止")) }
            return
        }
        ttsIndex = doneIndex + 1
        if (ttsIndex > ttsQueue.lastIndex) {
            stopTts()
            _uiState.update { it.copy(ttsState = it.ttsState.copy(status = "朗读完成")) }
        } else {
            speakCurrentTts()
        }
    }

    private fun onTtsError(message: String) {
        _uiState.update { it.copy(ttsState = it.ttsState.copy(isPlaying = false, isPaused = false, isLoading = false, status = message)) }
    }

    private fun saveTtsSettings() {
        val context = appContext ?: return
        val state = _uiState.value.ttsState
        viewModelScope.launch {
            SettingsRepository(context).saveTtsSettings(
                speechRate = state.speechRate,
                pitch = state.pitch,
                voiceName = state.voiceName,
                sleepEnabled = state.sleepEnabled,
                sleepMinutes = state.sleepMinutes
            )
        }
    }

    override fun onCleared() {
        ttsController?.shutdown()
        tempLocalFile?.let { runCatching { it.delete() } }
        super.onCleared()
    }

    fun updateAppDark(appDark: Boolean) {
        val current = _uiState.value.readerSettings
        val settings = current.copy(appDark = appDark)
        _uiState.update { it.copy(readerSettings = settings) }
        _uiState.value.sessionId?.let { sessionId ->
            ReadiumUiEvents.emitReaderSettings(sessionId, settings)
            ReadiumUiEvents.emitReaderThemeChanged(sessionId, settings)
        }
    }

    private var appContext: Context? = null

    fun refreshBookmarks() {
        val context = appContext ?: return
        val serverId = currentSessionServerId
        val bookId = currentSessionBookId
        viewModelScope.launch {
            val dao = ReaderDatabase.get(context).readerDao()
            _uiState.update { it.copy(bookmarks = dao.getBookmarks(serverId, bookId)) }
        }
    }

    fun refreshAnnotations() {
        val context = appContext ?: return
        val serverId = currentSessionServerId
        val bookId = currentSessionBookId
        viewModelScope.launch {
            val dao = ReaderDatabase.get(context).readerDao()
            _uiState.update { it.copy(annotations = dao.getAnnotations(serverId, bookId)) }
        }
    }
}

private fun Double.formatMb(): String = String.format(java.util.Locale.US, "%.1f", this)

private fun splitTtsText(text: String): List<String> {
    val normalized = text.replace(Regex("\\s+"), " ").trim()
    if (normalized.isBlank()) return emptyList()
    val result = mutableListOf<String>()
    val builder = StringBuilder()
    normalized.forEach { char ->
        builder.append(char)
        if (char in setOf('。', '！', '？', '；', '.', '!', '?', ';') || builder.length >= 120) {
            val sentence = builder.toString().trim()
            if (sentence.isNotBlank()) result += sentence
            builder.clear()
        }
    }
    val tail = builder.toString().trim()
    if (tail.isNotBlank()) result += tail
    return result
}

private fun String.toReaderTheme(): ReaderTheme = when (this) {
    "light" -> ReaderTheme.LIGHT
    "sepia" -> ReaderTheme.SEPIA
    "dark" -> ReaderTheme.DARK
    "pink" -> ReaderTheme.PINK
    "blue" -> ReaderTheme.BLUE
    "green" -> ReaderTheme.GREEN
    "custom" -> ReaderTheme.CUSTOM
    else -> ReaderTheme.SYSTEM
}

private fun ReaderTheme.toStorageValue(): String = when (this) {
    ReaderTheme.SYSTEM -> "system"
    ReaderTheme.LIGHT -> "light"
    ReaderTheme.SEPIA -> "sepia"
    ReaderTheme.DARK -> "dark"
    ReaderTheme.PINK -> "pink"
    ReaderTheme.BLUE -> "blue"
    ReaderTheme.GREEN -> "green"
    ReaderTheme.CUSTOM -> "custom"
}

private fun String.toReaderFontFamily(): ReaderFontFamily = when (this) {
    "serif" -> ReaderFontFamily.SERIF
    "sans_serif" -> ReaderFontFamily.SANS_SERIF
    "monospace" -> ReaderFontFamily.MONOSPACE
    "custom" -> ReaderFontFamily.CUSTOM
    else -> ReaderFontFamily.DEFAULT
}

private fun ReaderFontFamily.toStorageValue(): String = when (this) {
    ReaderFontFamily.DEFAULT -> "default"
    ReaderFontFamily.SERIF -> "serif"
    ReaderFontFamily.SANS_SERIF -> "sans_serif"
    ReaderFontFamily.MONOSPACE -> "monospace"
    ReaderFontFamily.CUSTOM -> "custom"
}

private fun String.toReaderPageAnimation(): ReaderPageAnimation = when (this) {
    "slide" -> ReaderPageAnimation.SLIDE
    "cover" -> ReaderPageAnimation.COVER
    "override" -> ReaderPageAnimation.OVERRIDE
    "none" -> ReaderPageAnimation.NONE
    else -> ReaderPageAnimation.SMOOTH
}

private fun ReaderPageAnimation.toStorageValue(): String = when (this) {
    ReaderPageAnimation.SMOOTH -> "smooth"
    ReaderPageAnimation.SLIDE -> "slide"
    ReaderPageAnimation.COVER -> "cover"
    ReaderPageAnimation.OVERRIDE -> "override"
    ReaderPageAnimation.NONE -> "none"
}

private fun String.toReaderPageTurnMode(): ReaderPageTurnMode = when (this) {
    "left_right" -> ReaderPageTurnMode.LEFT_RIGHT
    "right_only" -> ReaderPageTurnMode.RIGHT_ONLY
    "disabled" -> ReaderPageTurnMode.DISABLED
    else -> ReaderPageTurnMode.INVERTED_L
}

private fun ReaderPageTurnMode.toStorageValue(): String = when (this) {
    ReaderPageTurnMode.INVERTED_L -> "inverted_l"
    ReaderPageTurnMode.LEFT_RIGHT -> "left_right"
    ReaderPageTurnMode.RIGHT_ONLY -> "right_only"
    ReaderPageTurnMode.DISABLED -> "disabled"
}

private fun List<Link>.flattenToc(level: Int = 0): List<ReaderTocItem> = flatMap { link ->
    val title = link.title?.trim().orEmpty()
    val current = if (title.isNotBlank()) {
        listOf(ReaderTocItem(title = title, href = link.href.toString(), level = level))
    } else {
        emptyList()
    }
    current + link.children.flattenToc(level + 1)
}
