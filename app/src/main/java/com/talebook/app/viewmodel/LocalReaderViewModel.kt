package com.talebook.app.viewmodel

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.talebook.app.data.local.ReaderAnnotationEntity
import com.talebook.app.data.local.ReaderBookmarkEntity
import com.talebook.app.data.local.ReaderDatabase
import com.talebook.app.data.repository.BookRepository
import com.talebook.app.data.repository.ReaderCacheRepository
import com.talebook.app.data.repository.SettingsRepository
import com.talebook.app.reader.EpubReadiumSession
import com.talebook.app.reader.PdfReadiumSession
import com.talebook.app.reader.ReaderDisplaySettings
import com.talebook.app.reader.ReaderFontFamily
import com.talebook.app.reader.ReaderPageAnimation
import com.talebook.app.reader.ReaderPageTurnMode
import com.talebook.app.reader.ReaderTheme
import com.talebook.app.reader.ReadiumEngine
import com.talebook.app.reader.ReadiumSessionStore
import com.talebook.app.reader.ReadiumUiEvents
import com.talebook.app.reader.TtsController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
import java.io.File
import java.net.URI

data class LocalReaderUiState(
    val isLoading: Boolean = false,
    val title: String = "",
    val cachedPath: String = "",
    val sourceUri: String = "",
    val format: String = "",
    val isCached: Boolean = false,
    val isCaching: Boolean = false,
    val sessionId: Long? = null,
    val statusMessage: String = "",
    val error: String? = null,
    val tableOfContents: List<ReaderTocItem> = emptyList(),
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
        paragraphSpacing = 1.0f,
        publisherStyles = true,
        keepScreenOn = false,
        pageAnimation = ReaderPageAnimation.SMOOTH,
        scrollTapPageTurn = true,
        scrollKeepLine = true,
        volumeKeyPageTurn = false
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

    private var currentBookId: Int = 0
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
                            val dao = ReaderDatabase.get(context).readerDao()
                            val settingsRepository = SettingsRepository(context)
                            val appTheme = settingsRepository.themeMode.first()
                            val readerTheme = when (appTheme) {
                                SettingsRepository.THEME_LIGHT -> ReaderTheme.LIGHT
                                SettingsRepository.THEME_DARK -> ReaderTheme.DARK
                                else -> settingsRepository.readerTheme.first().toReaderTheme()
                            }
                            val readerSettings = ReaderDisplaySettings(
                                fontFamily = settingsRepository.readerFontFamily.first().toReaderFontFamily(),
                                fontScale = settingsRepository.readerFontScale.first(),
                                lineHeight = settingsRepository.readerLineHeight.first(),
                                brightness = settingsRepository.readerBrightness.first(),
                                scrollMode = settingsRepository.readerScrollMode.first(),
                                useSystemBrightness = settingsRepository.readerUseSystemBrightness.first(),
                                theme = readerTheme,
                                tapPageTurn = settingsRepository.readerTapPageTurn.first(),
                                appDark = appTheme == SettingsRepository.THEME_DARK,
                                pageTurnMode = settingsRepository.readerPageTurnMode.first().toReaderPageTurnMode(),
                                pageMargins = settingsRepository.readerPageMargins.first(),
                                paragraphSpacing = settingsRepository.readerParagraphSpacing.first(),
                                publisherStyles = settingsRepository.readerPublisherStyles.first(),
                                keepScreenOn = settingsRepository.readerKeepScreenOn.first(),
                                pageAnimation = settingsRepository.readerPageAnimation.first().toReaderPageAnimation(),
                                scrollTapPageTurn = settingsRepository.readerScrollTapPageTurn.first(),
                                scrollKeepLine = settingsRepository.readerScrollKeepLine.first(),
                                volumeKeyPageTurn = settingsRepository.readerVolumeKeyPageTurn.first()
                            )
                            val ttsSettings = ReaderTtsState(
                                speechRate = settingsRepository.ttsSpeechRate.first(),
                                pitch = settingsRepository.ttsPitch.first(),
                                voiceName = settingsRepository.ttsVoiceName.first(),
                                sleepEnabled = settingsRepository.ttsSleepEnabled.first(),
                                sleepMinutes = settingsRepository.ttsSleepMinutes.first().coerceIn(5, 180)
                            )
                            val bookmarks = dao.getBookmarks(bookId)
                            val annotations = dao.getAnnotations(bookId)
                            val session = openReadiumSession(context, bookId, source.uri, readerSettings)
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
                                error = e.message ?: "缓存失败"
                            )
                        }
                    )
                },
                onFailure = { e ->
                    _uiState.value = LocalReaderUiState(
                        isLoading = false,
                        error = e.message ?: "加载书籍失败"
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

    private suspend fun openReadiumSession(
        context: Context,
        bookId: Int,
        uri: String,
        readerSettings: ReaderDisplaySettings
    ): Result<Long> {
        return runCatching {
            val engine = ReadiumEngine(context)
            val dao = ReaderDatabase.get(context).readerDao()
            val initialLocator = dao.getProgress(bookId)?.locatorJson?.let { json ->
                runCatching { Locator.fromJSON(JSONObject(json)) }.getOrNull()
            }
            val asset = if (uri.startsWith("file:")) {
                engine.assetRetriever.retrieve(File(URI(uri))).getOrNull()
            } else {
                val absoluteUrl = AbsoluteUrl(uri) ?: error("无效的阅读地址")
                engine.assetRetriever.retrieve(absoluteUrl).getOrNull()
            } ?: error("Readium 无法读取书籍资源")

            val publication = engine.publicationOpener.open(asset, allowUserInteraction = true).getOrNull()
                ?: error("Readium 无法解析书籍")
            val sessionId = ReadiumSessionStore.nextId()
            val session = when {
                publication.conformsTo(Publication.Profile.EPUB) || publication.readingOrder.allAreHtml -> {
                    EpubReadiumSession(
                        id = sessionId,
                        bookId = bookId,
                        publication = publication,
                        initialLocator = initialLocator,
                        navigatorFactory = EpubNavigatorFactory(publication),
                        displaySettings = readerSettings
                    )
                }
                publication.conformsTo(Publication.Profile.PDF) -> {
                    val pdfEngine = PdfiumEngineProvider()
                    PdfReadiumSession(
                        id = sessionId,
                        bookId = bookId,
                        publication = publication,
                        initialLocator = initialLocator,
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
            ReadiumUiEvents.emitGoToLocator(sessionId, bookmark.locatorJson)
        }
    }

    fun deleteBookmark(bookmark: ReaderBookmarkEntity) {
        val bookId = currentBookId
        viewModelScope.launch {
            val dao = ReaderDatabase.get(appContext ?: return@launch).readerDao()
            dao.deleteBookmark(bookmark)
            _uiState.update { it.copy(bookmarks = dao.getBookmarks(bookId)) }
        }
    }

    fun renameBookmark(bookmark: ReaderBookmarkEntity, title: String) {
        val context = appContext ?: return
        val bookId = currentBookId
        val normalized = title.trim().take(120).ifBlank { "书签" }
        viewModelScope.launch {
            val dao = ReaderDatabase.get(context).readerDao()
            dao.renameBookmark(bookmark.id, normalized)
            _uiState.update { it.copy(bookmarks = dao.getBookmarks(bookId)) }
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
            ReadiumUiEvents.emitGoToLocator(sessionId, annotation.locatorJson)
        }
    }

    fun deleteAnnotation(annotation: ReaderAnnotationEntity) {
        val context = appContext ?: return
        val bookId = currentBookId
        viewModelScope.launch {
            val dao = ReaderDatabase.get(context).readerDao()
            dao.deleteAnnotation(annotation)
            _uiState.update { it.copy(annotations = dao.getAnnotations(bookId)) }
            _uiState.value.sessionId?.let { ReadiumUiEvents.emitAnnotationChanged(it) }
        }
    }

    fun updateAnnotation(annotation: ReaderAnnotationEntity, note: String) {
        val context = appContext ?: return
        val bookId = currentBookId
        val normalized = note.trim().take(2000)
        if (normalized.isBlank()) return
        viewModelScope.launch {
            val dao = ReaderDatabase.get(context).readerDao()
            dao.saveAnnotation(annotation.copy(note = normalized, updatedAt = System.currentTimeMillis()))
            _uiState.update { it.copy(annotations = dao.getAnnotations(bookId)) }
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
        fontScale: Float,
        lineHeight: Float,
        brightness: Float,
        scrollMode: Boolean,
        useSystemBrightness: Boolean,
        theme: ReaderTheme,
        tapPageTurn: Boolean,
        fontFamily: ReaderFontFamily = _uiState.value.readerSettings.fontFamily,
        pageTurnMode: ReaderPageTurnMode = _uiState.value.readerSettings.pageTurnMode,
        pageMargins: Float = _uiState.value.readerSettings.pageMargins,
        paragraphSpacing: Float = _uiState.value.readerSettings.paragraphSpacing,
        publisherStyles: Boolean = _uiState.value.readerSettings.publisherStyles,
        keepScreenOn: Boolean = _uiState.value.readerSettings.keepScreenOn,
        pageAnimation: ReaderPageAnimation = _uiState.value.readerSettings.pageAnimation,
        scrollTapPageTurn: Boolean = _uiState.value.readerSettings.scrollTapPageTurn,
        scrollKeepLine: Boolean = _uiState.value.readerSettings.scrollKeepLine,
        volumeKeyPageTurn: Boolean = _uiState.value.readerSettings.volumeKeyPageTurn
    ) {
        val settings = ReaderDisplaySettings(
            fontFamily = fontFamily,
            fontScale = fontScale.coerceIn(0.7f, 1.8f),
            lineHeight = lineHeight.coerceIn(1.0f, 2.4f),
            brightness = brightness.coerceIn(0.3f, 1.0f),
            scrollMode = scrollMode,
            useSystemBrightness = useSystemBrightness,
            theme = theme,
            tapPageTurn = tapPageTurn,
            appDark = _uiState.value.readerSettings.appDark,
            pageTurnMode = pageTurnMode,
            pageMargins = pageMargins.coerceIn(0.5f, 2.0f),
            paragraphSpacing = paragraphSpacing.coerceIn(0.0f, 2.0f),
            publisherStyles = publisherStyles,
            keepScreenOn = keepScreenOn,
            pageAnimation = pageAnimation,
            scrollTapPageTurn = scrollTapPageTurn,
            scrollKeepLine = scrollKeepLine,
            volumeKeyPageTurn = volumeKeyPageTurn
        )
        _uiState.update { it.copy(readerSettings = settings) }
        _uiState.value.sessionId?.let { sessionId ->
            ReadiumUiEvents.emitReaderSettings(sessionId, settings)
        }
        viewModelScope.launch {
            SettingsRepository(appContext ?: return@launch).saveReaderDisplaySettings(
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
                paragraphSpacing = settings.paragraphSpacing,
                publisherStyles = settings.publisherStyles,
                keepScreenOn = settings.keepScreenOn,
                pageAnimation = settings.pageAnimation.toStorageValue(),
                scrollTapPageTurn = settings.scrollTapPageTurn,
                scrollKeepLine = settings.scrollKeepLine,
                volumeKeyPageTurn = settings.volumeKeyPageTurn
            )
        }
    }

    fun cycleReaderTheme() {
        val current = _uiState.value.readerSettings
        val nextTheme = when (current.theme) {
            ReaderTheme.SYSTEM -> ReaderTheme.LIGHT
            ReaderTheme.LIGHT -> ReaderTheme.SEPIA
            ReaderTheme.SEPIA -> ReaderTheme.DARK
            ReaderTheme.DARK -> ReaderTheme.SYSTEM
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
            }
            if (appTheme != null) SettingsRepository(context).saveThemeMode(appTheme)
        }
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
        startTtsPlayback()
    }

    fun hideTtsPanel() {
        _uiState.update { it.copy(ttsState = it.ttsState.copy(isPanelVisible = false)) }
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

    fun stopTts() {
        ttsController?.stop()
        sleepStopAtMillis = 0L
        _uiState.update { it.copy(ttsState = it.ttsState.copy(isPlaying = false, isPaused = false, status = "已停止")) }
    }

    fun exitTts() {
        ttsController?.shutdown()
        ttsController = null
        ttsQueue = emptyList()
        ttsIndex = 0
        ttsGeneration++
        sleepStopAtMillis = 0L
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
            val currentProgression = ReaderDatabase.get(context).readerDao().getProgress(currentBookId)?.progression ?: 0.0
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
                onError = { message -> onTtsError(message) }
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
        _uiState.value.sessionId?.let { ReadiumUiEvents.emitGoToLocator(it, utterance.locatorJson) }
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
        super.onCleared()
    }

    fun updateAppDark(appDark: Boolean) {
        val current = _uiState.value.readerSettings
        val settings = current.copy(appDark = appDark)
        _uiState.update { it.copy(readerSettings = settings) }
        _uiState.value.sessionId?.let { sessionId ->
            ReadiumUiEvents.emitReaderSettings(sessionId, settings)
        }
    }

    private var appContext: Context? = null

    fun refreshBookmarks() {
        val context = appContext ?: return
        val bookId = currentBookId
        viewModelScope.launch {
            val dao = ReaderDatabase.get(context).readerDao()
            _uiState.update { it.copy(bookmarks = dao.getBookmarks(bookId)) }
        }
    }

    fun refreshAnnotations() {
        val context = appContext ?: return
        val bookId = currentBookId
        viewModelScope.launch {
            val dao = ReaderDatabase.get(context).readerDao()
            _uiState.update { it.copy(annotations = dao.getAnnotations(bookId)) }
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
    else -> ReaderTheme.SYSTEM
}

private fun ReaderTheme.toStorageValue(): String = when (this) {
    ReaderTheme.SYSTEM -> "system"
    ReaderTheme.LIGHT -> "light"
    ReaderTheme.SEPIA -> "sepia"
    ReaderTheme.DARK -> "dark"
}

private fun String.toReaderFontFamily(): ReaderFontFamily = when (this) {
    "serif" -> ReaderFontFamily.SERIF
    "sans_serif" -> ReaderFontFamily.SANS_SERIF
    "monospace" -> ReaderFontFamily.MONOSPACE
    else -> ReaderFontFamily.DEFAULT
}

private fun ReaderFontFamily.toStorageValue(): String = when (this) {
    ReaderFontFamily.DEFAULT -> "default"
    ReaderFontFamily.SERIF -> "serif"
    ReaderFontFamily.SANS_SERIF -> "sans_serif"
    ReaderFontFamily.MONOSPACE -> "monospace"
}

private fun String.toReaderPageAnimation(): ReaderPageAnimation = when (this) {
    "slide" -> ReaderPageAnimation.SLIDE
    "cover" -> ReaderPageAnimation.COVER
    "none" -> ReaderPageAnimation.NONE
    else -> ReaderPageAnimation.SMOOTH
}

private fun ReaderPageAnimation.toStorageValue(): String = when (this) {
    ReaderPageAnimation.SMOOTH -> "smooth"
    ReaderPageAnimation.SLIDE -> "slide"
    ReaderPageAnimation.COVER -> "cover"
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
