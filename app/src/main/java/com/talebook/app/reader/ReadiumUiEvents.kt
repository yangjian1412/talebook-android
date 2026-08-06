package com.talebook.app.reader

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object ReadiumUiEvents {
    private val _centerTaps = MutableSharedFlow<Pair<Long, String>>(extraBufferCapacity = 8)
    private val _addBookmarks = MutableSharedFlow<Long>(extraBufferCapacity = 8)
    private val _addNotes = MutableSharedFlow<Pair<Long, String>>(extraBufferCapacity = 8)
    private val _bookmarkAdded = MutableSharedFlow<Long>(extraBufferCapacity = 8)
    private val _annotationAdded = MutableSharedFlow<Long>(extraBufferCapacity = 8)
    private val _annotationChanged = MutableSharedFlow<Long>(extraBufferCapacity = 8)
    private val _ttsHighlights = MutableSharedFlow<Pair<Long, String>>(extraBufferCapacity = 8)
    private val _goToLocators = MutableSharedFlow<Pair<Long, String>>(extraBufferCapacity = 8)
    private val _goToLinks = MutableSharedFlow<Pair<Long, String>>(extraBufferCapacity = 8)
    private val _goToProgress = MutableSharedFlow<Pair<Long, Double>>(extraBufferCapacity = 8)
    private val _goToPage = MutableSharedFlow<Pair<Long, Int>>(extraBufferCapacity = 8)
    private val _progress = MutableSharedFlow<Pair<Long, Double>>(extraBufferCapacity = 16)
    private val _readerSettings = MutableSharedFlow<Pair<Long, ReaderDisplaySettings>>(extraBufferCapacity = 16)
    private val _readerThemeChanged = MutableSharedFlow<Pair<Long, ReaderDisplaySettings>>(extraBufferCapacity = 8)
    private val _readerInteractions = MutableSharedFlow<Long>(extraBufferCapacity = 16)
    private val _goForwardKey = MutableSharedFlow<Long>(extraBufferCapacity = 8)
    private val _goBackwardKey = MutableSharedFlow<Long>(extraBufferCapacity = 8)
    private val _currentChapterPath = MutableSharedFlow<Pair<Long, String>>(extraBufferCapacity = 8)
    val centerTaps = _centerTaps.asSharedFlow()
    val addBookmarks = _addBookmarks.asSharedFlow()
    val addNotes = _addNotes.asSharedFlow()
    val bookmarkAdded = _bookmarkAdded.asSharedFlow()
    val annotationAdded = _annotationAdded.asSharedFlow()
    val annotationChanged = _annotationChanged.asSharedFlow()
    val ttsHighlights = _ttsHighlights.asSharedFlow()
    val goToLocators = _goToLocators.asSharedFlow()
    val goToLinks = _goToLinks.asSharedFlow()
    val goToProgress = _goToProgress.asSharedFlow()
    val goToPage = _goToPage.asSharedFlow()
    val progress = _progress.asSharedFlow()
    val readerSettings = _readerSettings.asSharedFlow()
    val readerThemeChanged = _readerThemeChanged.asSharedFlow()
    val readerInteractions = _readerInteractions.asSharedFlow()
    val goForwardKey = _goForwardKey.asSharedFlow()
    val goBackwardKey = _goBackwardKey.asSharedFlow()
    val currentChapterPath = _currentChapterPath.asSharedFlow()

    fun emitCenterTap(sessionId: Long, locatorJson: String) {
        _centerTaps.tryEmit(sessionId to locatorJson)
    }

    fun emitAddBookmark(sessionId: Long) {
        _addBookmarks.tryEmit(sessionId)
    }

    fun emitAddNote(sessionId: Long, note: String) {
        _addNotes.tryEmit(sessionId to note)
    }

    fun emitBookmarkAdded(sessionId: Long) {
        _bookmarkAdded.tryEmit(sessionId)
    }

    fun emitAnnotationAdded(sessionId: Long) {
        _annotationAdded.tryEmit(sessionId)
    }

    fun emitAnnotationChanged(sessionId: Long) {
        _annotationChanged.tryEmit(sessionId)
    }

    fun emitGoToLocator(sessionId: Long, locatorJson: String) {
        _goToLocators.tryEmit(sessionId to locatorJson)
    }

    fun emitTtsHighlight(sessionId: Long, locatorJson: String) {
        _ttsHighlights.tryEmit(sessionId to locatorJson)
    }

    fun emitGoToLink(sessionId: Long, href: String) {
        _goToLinks.tryEmit(sessionId to href)
    }

    fun emitGoToProgress(sessionId: Long, progress: Double) {
        _goToProgress.tryEmit(sessionId to progress.coerceIn(0.0, 1.0))
    }

    fun emitGoToPage(sessionId: Long, page: Int) {
        _goToPage.tryEmit(sessionId to page.coerceAtLeast(1))
    }

    fun emitProgress(sessionId: Long, progression: Double) {
        _progress.tryEmit(sessionId to progression)
    }

    fun emitReaderSettings(sessionId: Long, settings: ReaderDisplaySettings) {
        _readerSettings.tryEmit(sessionId to settings)
    }

    fun emitReaderThemeChanged(sessionId: Long, settings: ReaderDisplaySettings) {
        _readerThemeChanged.tryEmit(sessionId to settings)
    }

    fun emitReaderInteraction(sessionId: Long) {
        _readerInteractions.tryEmit(sessionId)
    }

    fun emitGoForwardKey(sessionId: Long) {
        _goForwardKey.tryEmit(sessionId)
    }

    fun emitGoBackwardKey(sessionId: Long) {
        _goBackwardKey.tryEmit(sessionId)
    }

    fun emitCurrentChapterPath(sessionId: Long, path: String) {
        _currentChapterPath.tryEmit(sessionId to path)
    }
}

data class ReaderDisplaySettings(
    val fontFamily: ReaderFontFamily = ReaderFontFamily.DEFAULT,
    val fontScale: Float = 1.0f,
    val lineHeight: Float = 1.5f,
    val brightness: Float = 1.0f,
    val scrollMode: Boolean = false,
    val useSystemBrightness: Boolean = true,
    val theme: ReaderTheme = ReaderTheme.SYSTEM,
    val tapPageTurn: Boolean = true,
    val appDark: Boolean = false,
    val pageTurnMode: ReaderPageTurnMode = ReaderPageTurnMode.INVERTED_L,
    val pageMargins: Float = 1.0f,
    val pageMarginHorizontal: Float = 1.0f,
    val pageMarginVertical: Float = 1.0f,
    val pageMarginSeparateMode: Boolean = false,
    val paragraphSpacing: Float = 1.0f,
    val publisherStyles: Boolean = true,
    val forcePublisherFonts: Boolean = false,
    val keepScreenOn: Boolean = false,
    val pageAnimation: ReaderPageAnimation = ReaderPageAnimation.SMOOTH,
    val forceTapAnimation: Boolean = true,
    val scrollTapPageTurn: ReaderScrollTapSpeed = ReaderScrollTapSpeed.MEDIUM,
    val scrollKeepLine: Boolean = true,
    val volumeKeyPageTurn: Boolean = false,
    val letterSpacing: Float = 0f,
    val readerBackgroundColor: Long = 0x00000000L,
    val readerTextColor: Long = 0x00000000L,
    val customThemeEnabled: Boolean = false,
)

enum class ReaderFontFamily {
    DEFAULT,
    SERIF,
    SANS_SERIF,
    MONOSPACE
}

enum class ReaderTheme {
    SYSTEM,
    LIGHT,
    SEPIA,
    DARK,
    PINK,
    BLUE,
    GREEN,
    CUSTOM
}

enum class ReaderPageTurnMode {
    INVERTED_L,
    LEFT_RIGHT,
    RIGHT_ONLY,
    DISABLED
}

enum class ReaderPageAnimation {
    SMOOTH,
    SLIDE,
    COVER,
    OVERRIDE,
    NONE
}

enum class ReaderScrollTapSpeed {
    OFF,
    FAST,
    MEDIUM,
    SLOW;

    val durationMs: Int
        get() = when (this) {
            OFF -> 0
            FAST -> 300
            MEDIUM -> 600
            SLOW -> 900
        }
}
