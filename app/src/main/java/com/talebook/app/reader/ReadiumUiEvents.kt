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
    private val _goToLocators = MutableSharedFlow<Pair<Long, String>>(extraBufferCapacity = 8)
    private val _goToLinks = MutableSharedFlow<Pair<Long, String>>(extraBufferCapacity = 8)
    private val _goToProgress = MutableSharedFlow<Pair<Long, Double>>(extraBufferCapacity = 8)
    private val _goToPage = MutableSharedFlow<Pair<Long, Int>>(extraBufferCapacity = 8)
    private val _progress = MutableSharedFlow<Pair<Long, Double>>(extraBufferCapacity = 16)
    private val _readerSettings = MutableSharedFlow<Pair<Long, ReaderDisplaySettings>>(extraBufferCapacity = 16)
    val centerTaps = _centerTaps.asSharedFlow()
    val addBookmarks = _addBookmarks.asSharedFlow()
    val addNotes = _addNotes.asSharedFlow()
    val bookmarkAdded = _bookmarkAdded.asSharedFlow()
    val annotationAdded = _annotationAdded.asSharedFlow()
    val annotationChanged = _annotationChanged.asSharedFlow()
    val goToLocators = _goToLocators.asSharedFlow()
    val goToLinks = _goToLinks.asSharedFlow()
    val goToProgress = _goToProgress.asSharedFlow()
    val goToPage = _goToPage.asSharedFlow()
    val progress = _progress.asSharedFlow()
    val readerSettings = _readerSettings.asSharedFlow()

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
    val paragraphSpacing: Float = 1.0f,
    val publisherStyles: Boolean = true,
    val keepScreenOn: Boolean = false,
    val pageAnimation: ReaderPageAnimation = ReaderPageAnimation.SMOOTH,
    val scrollTapPageTurn: Boolean = true,
    val scrollKeepLine: Boolean = true,
    val volumeKeyPageTurn: Boolean = false
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
    DARK
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
    NONE
}
