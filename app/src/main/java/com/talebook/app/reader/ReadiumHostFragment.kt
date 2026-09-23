package com.talebook.app.reader

import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.commitNow
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.talebook.app.data.local.ReaderAnnotationEntity
import com.talebook.app.data.local.ReaderBookmarkEntity
import com.talebook.app.data.local.ReaderDatabase
import com.talebook.app.data.local.ReadingProgressEntity
import com.talebook.app.data.repository.SettingsRepository
import com.talebook.app.ui.theme.ThemePresets
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.readium.adapter.pdfium.navigator.PdfiumEngineProvider
import org.readium.r2.navigator.HyperlinkNavigator
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.Navigator
import org.readium.r2.navigator.SelectableNavigator
import org.readium.r2.navigator.VisualNavigator
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.input.DragEvent
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.KeyEvent
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.pdf.PdfNavigatorFragment
import org.readium.r2.navigator.preferences.Color as ReadiumColor
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.data.ReadError

@OptIn(ExperimentalReadiumApi::class)
class ReadiumHostFragment : Fragment(), EpubNavigatorFragment.Listener, EpubNavigatorFragment.PaginationListener, PdfNavigatorFragment.Listener {
    private val sessionId: Long by lazy { requireArguments().getLong(ARG_SESSION_ID) }
    private val containerId: Int by lazy { View.generateViewId() }
private var lastChapterName: String = ""
    private var lastTwoPageActive: Boolean? = null
    private var lastAvoidLargePublisherFonts: Boolean? = null
    private var lastFontFamily: ReaderFontFamily? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val session = ReadiumSessionStore.get(sessionId)
        if (session == null) {
            super.onCreate(savedInstanceState)
            requireActivity().onBackPressedDispatcher.onBackPressed()
            return
        }

        val jumpLocator = arguments?.getString(ARG_JUMP_TO)?.let { json ->
            runCatching { Locator.fromJSON(JSONObject(json)) }.getOrNull()
        }

        childFragmentManager.fragmentFactory = when (session) {
            is EpubReadiumSession -> session.navigatorFactory.createFragmentFactory(
                initialLocator = jumpLocator ?: session.initialLocator,
                listener = this,
                paginationListener = this,
                configuration = buildNaviConfiguration(session.displaySettings)
            )
            is PdfReadiumSession -> session.navigatorFactory.createFragmentFactory(
                initialLocator = jumpLocator ?: session.initialLocator,
                listener = this
            )
        }
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = FrameLayout(requireContext()).apply {
        id = containerId
        val session = ReadiumSessionStore.get(sessionId)
        val settings = session?.displaySettings
        val isImage = settings?.let { resolveActivePalette(it)?.imageResName?.isNotEmpty() } == true
        if (isImage) {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        } else {
            val dark = settings?.appDark == true
            val activeBg = resolveActiveBackground(settings?.readerBackgroundColor, dark)
            setBackgroundColor(rgbToColor(activeBg))
        }
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val session = ReadiumSessionStore.get(sessionId) ?: return
        if (savedInstanceState == null) {
            view.post {
                if (!isAdded || childFragmentManager.findFragmentByTag(NAVIGATOR_TAG) != null) return@post
                childFragmentManager.commitNow {
                    when (session) {
                        is EpubReadiumSession -> add(containerId, EpubNavigatorFragment::class.java, Bundle(), NAVIGATOR_TAG)
                        is PdfReadiumSession -> add(containerId, PdfNavigatorFragment::class.java, Bundle(), NAVIGATOR_TAG)
                    }
                }
                setupNavigator(view, session)
            }
        } else {
            setupNavigator(view, session)
        }
    }

    private fun buildNaviConfiguration(settings: ReaderDisplaySettings): org.readium.r2.navigator.epub.EpubNavigatorFragment.Configuration {
        return org.readium.r2.navigator.epub.EpubNavigatorFragment.Configuration()
    }

    private var transparencyWatchJob: kotlinx.coroutines.Job? = null

    private fun setupNavigator(view: View, session: ReadiumSession) {
        val navigator = childFragmentManager.findFragmentByTag(NAVIGATOR_TAG) as? Navigator ?: return
        applyReaderSettings(session, navigator, session.displaySettings)
        stabilizeInitialLayout(view)
        if (isImagePresetForSettings(session.displaySettings)) {
            setNavigatorWebViewTransparent(navigator)
        }
        startImageTransparencyWatch(navigator)
        view.post {
            if (!isAdded || view == null) return@post
            val nav = childFragmentManager.findFragmentByTag(NAVIGATOR_TAG) as? EpubNavigatorFragment ?: return@post
            injectLayoutCss(nav, session.displaySettings)
            persistEpubPrefsIfReady(nav)
        }
        (navigator as? VisualNavigator)?.addInputListener(object : InputListener {
            override fun onTap(event: TapEvent): Boolean {
                val viewWidth = view.width.takeIf { it > 0 } ?: return false
                val viewHeight = view.height.takeIf { it > 0 } ?: return false
                val x = event.point.x
                val y = event.point.y
                val centerX = x > viewWidth * 0.25f && x < viewWidth * 0.75f
                val centerY = y > viewHeight * 0.33f && y < viewHeight * 0.67f
                return if (centerX && centerY) {
                    ReadiumUiEvents.emitCenterTap(sessionId, navigator.currentLocator.value.toJSON().toString())
                    true
                } else if (session.displaySettings.tapPageTurn) {
                    ReaderBarsController.hide()
                    if (session.displaySettings.scrollMode) {
                        if (session.displaySettings.scrollTapPageTurn != com.talebook.app.reader.ReaderScrollTapSpeed.OFF) {
                            handleScrollTap(navigator, x, y, viewWidth.toFloat(), viewHeight.toFloat(), session.displaySettings)
                        }
                        true
                    } else {
                        handlePageTurnTap(navigator, x, y, viewWidth.toFloat(), viewHeight.toFloat(), session.displaySettings.pageTurnMode)
                    }
                } else {
                    ReaderBarsController.hide()
                    false
                }
            }

            override fun onDrag(event: DragEvent): Boolean = false

            override fun onKey(event: KeyEvent): Boolean {
                if (!session.displaySettings.volumeKeyPageTurn) return false
                val key = event.key.toString().lowercase()
                return when {
                    key.contains("volumedown") || key.contains("volume_down") -> {
                        ReaderBarsController.hide()
                        goForward(navigator)
                    }
                    key.contains("volumeup") || key.contains("volume_up") -> {
                        ReaderBarsController.hide()
                        goBackward(navigator)
                    }
                    else -> false
                }
            }
        })
        viewLifecycleOwner.lifecycleScope.launch { applyAnnotationDecorations(session.serverId, session.bookId, navigator) }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                navigator.currentLocator
                    .onEach { locator ->
                        saveProgress(session.bookId, locator)
                        emitChapterPath(session, locator)
                    }
                    .launchIn(this)
                ReadiumUiEvents.addBookmarks
                    .onEach { targetSessionId ->
                        if (targetSessionId == sessionId) addBookmark(session.bookId, navigator.currentLocator.value)
                    }
                    .launchIn(this)
                ReadiumUiEvents.addNotes
                    .onEach { (targetSessionId, note) ->
                        if (targetSessionId == sessionId) addAnnotation(session.bookId, navigator, note)
                    }
                    .launchIn(this)
                ReadiumUiEvents.goToLocators
                    .onEach { (targetSessionId, locatorJson) ->
                        if (targetSessionId == sessionId) {
                            runCatching { Locator.fromJSON(JSONObject(locatorJson)) }
                                .getOrNull()
                                ?.let { locator -> navigator.go(locator) }
                        }
                    }
                    .launchIn(this)
                ReadiumUiEvents.ttsHighlights
                    .onEach { (targetSessionId, locatorJson) ->
                        if (targetSessionId == sessionId) {
                            applyTtsHighlight(navigator, locatorJson)
                        }
                    }
                    .launchIn(this)
                ReadiumUiEvents.goToLinks
                    .onEach { (targetSessionId, href) ->
                        if (targetSessionId == sessionId) {
                            val link = findLink(session.publication.tableOfContents, href)
                                ?: findLink(session.publication.readingOrder, href)
                            if (link != null) {
                                restartWithLocator(buildLocatorJson(link, readingOrder = session.publication.readingOrder))
                            }
                        }
                    }
                    .launchIn(this)
                ReadiumUiEvents.goToProgress
                    .onEach { (targetSessionId, progress) ->
                        if (targetSessionId == sessionId) restartByProgress(session.publication.readingOrder, progress)
                    }
                    .launchIn(this)
                ReadiumUiEvents.goToPage
                    .onEach { (targetSessionId, page) ->
                        if (targetSessionId == sessionId) restartByPage(session.publication.readingOrder, page)
                    }
                    .launchIn(this)
                ReadiumUiEvents.readerJump
                    .onEach { (targetSessionId, locatorJson) ->
                        if (targetSessionId == sessionId) restartWithLocator(locatorJson)
                    }
                    .launchIn(this)
                ReadiumUiEvents.readerSettings
                    .onEach { (targetSessionId, settings) ->
                        if (targetSessionId == sessionId) {
                            session.displaySettings = settings
                            applyReaderSettings(session, navigator, settings)
                        }
                    }
                    .launchIn(this)
                ReadiumUiEvents.goForwardKey
                    .onEach { targetSessionId ->
                        if (targetSessionId == sessionId) {
                            ReaderBarsController.hide()
                            goForward(navigator)
                        }
                    }
                    .launchIn(this)
                ReadiumUiEvents.goBackwardKey
                    .onEach { targetSessionId ->
                        if (targetSessionId == sessionId) {
                            ReaderBarsController.hide()
                            goBackward(navigator)
                        }
                    }
                    .launchIn(this)
                ReadiumUiEvents.annotationChanged
                    .onEach { targetSessionId ->
                        if (targetSessionId == sessionId) applyAnnotationDecorations(session.serverId, session.bookId, navigator)
                    }
                    .launchIn(this)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (requireActivity().isFinishing) {
            ReadiumSessionStore.remove(sessionId)
        }
    }

    override fun onDestroyView() {
        showSystemBars()
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val currentView = view ?: return
        val session = ReadiumSessionStore.get(sessionId) ?: return
        currentView.post {
            if (!isAdded || view == null || activity == null) return@post
            try {
                val nav = childFragmentManager.findFragmentByTag(NAVIGATOR_TAG) as? Navigator ?: return@post
                applyReaderSettings(session, nav, session.displaySettings)
            } catch (_: Exception) {
            }
        }
    }

    override fun onResourceLoadFailed(url: Url, error: ReadError) {
        android.util.Log.w("TaleReadium", "Resource load failed: $url $error")
    }

    override fun onJumpToLocator(locator: Locator) {
        android.util.Log.d("TaleReadium", "Jump to locator: $locator")
    }

    override fun shouldFollowInternalLink(link: Link, context: HyperlinkNavigator.LinkContext?): Boolean = true

    override fun onExternalLinkActivated(url: AbsoluteUrl) {
        android.util.Log.d("TaleReadium", "External link: $url")
    }

    override fun onPageChanged(pageIndex: Int, totalPages: Int, locator: Locator) {
        val session = ReadiumSessionStore.get(sessionId) ?: return
        emitChapterPath(session, locator)
    }

    override fun onPageLoaded() {
        val session = ReadiumSessionStore.get(sessionId) ?: return
        val navigator = childFragmentManager.findFragmentByTag(NAVIGATOR_TAG) as? Navigator ?: return
        if (session is EpubReadiumSession && navigator is EpubNavigatorFragment) {
            val avoidLargePublisherFonts = session.isRemote && session.hasLargeEmbeddedFonts && !session.displaySettings.forcePublisherFonts
            val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val twoPageActive = session.displaySettings.twoPageMode && isLandscape
            injectBasicCss(navigator, session.displaySettings, avoidLargePublisherFonts, twoPageActive)
            injectCustomFontCss(navigator, session.displaySettings)
            injectBackgroundImageCss(navigator, session.displaySettings)
            if (isImagePresetForSettings(session.displaySettings)) {
                setNavigatorWebViewTransparent(navigator)
            }
        }
        if (!healedInitialProgress) {
            healedInitialProgress = true
            viewLifecycleOwner.lifecycleScope.launch {
                healBareProgressIfNeeded(session, navigator.currentLocator.value)
            }
        }
    }

    private var healedInitialProgress: Boolean = false

    private suspend fun healBareProgressIfNeeded(session: ReadiumSession, locator: Locator) {
        val dao = ReaderDatabase.get(requireContext()).readerDao()
        val row = dao.getProgress(session.serverId, session.bookId) ?: return
        if (row.progression > 0.0 && !LocatorProgress.looksBareLocatorJson(row.locatorJson)) return
        val stored = runCatching { Locator.fromJSON(JSONObject(row.locatorJson)) }.getOrNull()
            ?: return
        val estimated = LocatorProgress.estimateTotalProgression(session.publication, stored)
            ?: LocatorProgress.estimateTotalProgression(session.publication, locator)
            ?: return
        if (estimated <= 0.0) return
        val healed = LocatorProgress.withTotalProgression(
            if (stored.href.toString().isBlank()) locator else stored,
            estimated
        )
        val now = System.currentTimeMillis()
        dao.saveProgress(row.copy(locatorJson = healed.toJSON().toString(), progression = estimated, updatedAt = now))
        dao.getRecentEntry(session.serverId, session.bookId)?.let { recent ->
            if (recent.progression <= 0.0) {
                dao.upsertRecentEntry(recent.copy(progression = estimated, updatedAt = now))
            }
        }
        ReadiumUiEvents.emitProgress(sessionId, estimated)
    }

private suspend fun saveProgress(bookId: Int, locator: Locator) {
        val session = ReadiumSessionStore.get(sessionId) ?: return
        val serverId = session.serverId
        val now = System.currentTimeMillis()
        val dao = ReaderDatabase.get(requireContext()).readerDao()
        val existing = dao.getProgress(serverId, bookId)
        val existingProgression = existing?.progression ?: 0.0

        var progression: Double?
        var locatorOut = locator
        val reported = locator.locations.totalProgression
        if (reported != null) {
            progression = reported
        } else {
            progression = LocatorProgress.estimateTotalProgression(session.publication, locator)
            if (progression == null && existingProgression > 0.0) {
                progression = existingProgression
            }
            if (progression != null) {
                locatorOut = LocatorProgress.withTotalProgression(locator, progression)
            }
        }
        if (progression == null) progression = 0.0
        if (reported == null && progression <= 0.0 && existingProgression > 0.0) {
            progression = existingProgression
            locatorOut = LocatorProgress.withTotalProgression(locator, progression)
        }
        if (reported == null && locatorOut.locations.totalProgression == null && progression > 0.0) {
            locatorOut = LocatorProgress.withTotalProgression(locator, progression)
        }

        dao.saveProgress(
                ReadingProgressEntity(
                    serverId = serverId,
                    bookId = bookId,
                locatorJson = locatorOut.toJSON().toString(),
                progression = progression,
                updatedAt = now
            )
        )
        val existingRecent = dao.getRecentEntry(serverId, bookId)
        if (existingRecent != null) {
            val recentProgression = if (reported == null && progression <= 0.0 && existingRecent.progression > 0.0) {
                existingRecent.progression
            } else {
                progression
            }
            if (kotlin.math.abs(existingRecent.progression - recentProgression) > 0.000001 || existingRecent.progression <= 0.0 && recentProgression > 0.0) {
                dao.upsertRecentEntry(existingRecent.copy(progression = recentProgression, updatedAt = now))
            } else {
                dao.upsertRecentEntry(existingRecent.copy(updatedAt = now))
            }
            progression = recentProgression
        }
        ReadiumUiEvents.emitProgress(sessionId, progression)
    }

    private fun emitChapterPath(session: ReadiumSession, locator: Locator) {
        val toc = session.publication.tableOfContents
        val bookTitle = session.publication.metadata.title ?: ""
        val chapter = buildChapterPath(locator.href.toString(), toc, bookTitle, session.publication.readingOrder)
            .ifBlank { locator.title?.takeIf { it.isNotBlank() } ?: "" }
        if (chapter.isNotBlank()) lastChapterName = chapter
        ReadiumUiEvents.emitCurrentChapterPath(sessionId, chapter.ifBlank { lastChapterName })
    }

    private suspend fun addBookmark(bookId: Int, locator: Locator) {
        val session = ReadiumSessionStore.get(sessionId) ?: return
        val serverId = session.serverId
        ReaderDatabase.get(requireContext()).readerDao().addBookmark(
                ReaderBookmarkEntity(
                    serverId = serverId,
                    bookId = bookId,
                title = bookmarkTitle(locator),
                locatorJson = locator.toJSON().toString(),
                progression = locator.locations.totalProgression ?: 0.0,
                createdAt = System.currentTimeMillis()
            )
        )
        ReadiumUiEvents.emitBookmarkAdded(sessionId)
        android.widget.Toast.makeText(requireContext(), "已添加书签", android.widget.Toast.LENGTH_SHORT).show()
    }

    private suspend fun addAnnotation(bookId: Int, navigator: Navigator, note: String) {
        val session = ReadiumSessionStore.get(sessionId) ?: return
        val serverId = session.serverId
        val selection = (navigator as? SelectableNavigator)?.currentSelection()
        val locator = selection?.locator ?: navigator.currentLocator.value
        val selectedText = locatorTitle(locator)
        ReaderDatabase.get(requireContext()).readerDao().saveAnnotation(
                ReaderAnnotationEntity(
                    serverId = serverId,
                    bookId = bookId,
                locatorJson = locator.toJSON().toString(),
                selectedText = selectedText.ifBlank { "当前位置" },
                note = note.trim().take(2000),
                color = "yellow",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
            )
        )
        (navigator as? SelectableNavigator)?.clearSelection()
        applyAnnotationDecorations(serverId, bookId, navigator)
        ReadiumUiEvents.emitAnnotationAdded(sessionId)
        android.widget.Toast.makeText(requireContext(), "已保存笔记", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun bookmarkTitle(locator: Locator): String {
        return locatorTitle(locator).ifBlank { "书签" }
    }

    private fun locatorTitle(locator: Locator): String {
        val highlight = clean(locator.text.highlight)
        if (highlight.isNotBlank()) return highlight.take(48)

        val after = clean(locator.text.after)
        if (after.isNotBlank()) return after.take(48)

        val before = clean(locator.text.before)
        if (before.isNotBlank()) return before.takeLast(48)

        return locator.title?.ifBlank { null }.orEmpty()
    }

    private fun clean(text: String?): String = text
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        .orEmpty()

    private fun shouldAnimate(): Boolean {
        val settings = ReadiumSessionStore.get(sessionId)?.displaySettings ?: return true
        return when (settings.pageAnimation) {
            ReaderPageAnimation.NONE -> settings.forceTapAnimation
            ReaderPageAnimation.OVERRIDE -> true
            else -> true
        }
    }

    private fun scrollShouldAnimate(settings: ReaderDisplaySettings): Boolean {
        return when (settings.pageAnimation) {
            ReaderPageAnimation.NONE -> settings.forceTapAnimation
            else -> true
        }
    }

    private fun goBackward(navigator: Navigator): Boolean = when (navigator) {
        is EpubNavigatorFragment -> navigator.goBackward(shouldAnimate())
        is PdfNavigatorFragment<*, *> -> navigator.goBackward(shouldAnimate())
        else -> false
    }

    private fun goForward(navigator: Navigator): Boolean = when (navigator) {
        is EpubNavigatorFragment -> navigator.goForward(shouldAnimate())
        is PdfNavigatorFragment<*, *> -> navigator.goForward(shouldAnimate())
        else -> false
    }

    private fun handlePageTurnTap(
        navigator: Navigator,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        mode: ReaderPageTurnMode
    ): Boolean = when (mode) {
        ReaderPageTurnMode.DISABLED -> false
        ReaderPageTurnMode.LEFT_RIGHT -> when {
            x < width * 0.33f -> goBackward(navigator)
            x > width * 0.67f -> goForward(navigator)
            else -> false
        }
        ReaderPageTurnMode.RIGHT_ONLY -> goForward(navigator)
        ReaderPageTurnMode.INVERTED_L -> when {
            x < width * 0.28f -> goBackward(navigator)
            y < height * 0.33f -> goBackward(navigator)
            x > width * 0.72f -> goForward(navigator)
            y > height * 0.67f -> goForward(navigator)
            else -> false
        }
    }

    private fun handleScrollTap(
        navigator: Navigator,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        settings: ReaderDisplaySettings
    ): Boolean {
        val forward = when {
            x < width * 0.28f -> false
            y < height * 0.33f -> false
            x > width * 0.72f -> true
            y > height * 0.67f -> true
            else -> return false
        }
        val noAnim = !scrollShouldAnimate(settings)
        if (navigator is EpubNavigatorFragment) {
            if (noAnim) {
                val deltaPx = 0.84f * height
                viewLifecycleOwner.lifecycleScope.launch {
                    navigator.evaluateJavascript("window.scrollBy(0, ${if (forward) deltaPx else -deltaPx});")
                }
                return true
            }
            val keepLine = if (settings.scrollKeepLine) "true" else "false"
            val duration = settings.scrollTapPageTurn.durationMs
            viewLifecycleOwner.lifecycleScope.launch {
                navigator.evaluateJavascript(
                    """
                    (function() {
                        const lineHeight = $keepLine ? parseFloat(getComputedStyle(document.body).lineHeight || '24') : 0;
                        const delta = Math.max(Math.floor(window.innerHeight * 0.84 - lineHeight), Math.floor(window.innerHeight * 0.55));
                        const start = window.scrollY || document.documentElement.scrollTop || 0;
                        const target = start + (${if (forward) "delta" else "-delta"});
                        const duration = $duration;
                        const startTime = performance.now();
                        const ease = function(t) { return 1 - Math.pow(1 - t, 3); };
                        const step = function(now) {
                            const progress = Math.min(1, (now - startTime) / duration);
                            window.scrollTo(0, start + (target - start) * ease(progress));
                            if (progress < 1) requestAnimationFrame(step);
                        };
                        requestAnimationFrame(step);
                    })();
                    """.trimIndent()
                )
            }
            return true
        }
        if (noAnim) {
            val visual = navigator as? VisualNavigator ?: return false
            val snapDelta = (height * 0.84f).toInt()
            visual.publicationView.scrollBy(0, if (forward) snapDelta else -snapDelta)
            return true
        }
        val linePx = if (settings.scrollKeepLine) (24 * resources.displayMetrics.density).toInt() else 0
        val delta = ((height * 0.84f).toInt() - linePx).coerceAtLeast((height * 0.55f).toInt())
        val visual = navigator as? VisualNavigator ?: return false
        visual.publicationView.scrollBy(0, if (forward) delta else -delta)
        return true
    }

private fun goToProgress(readingOrder: List<Link>, navigator: Navigator, progress: Double) {
        val links = readingOrder.takeIf { it.isNotEmpty() } ?: return
        val clampedProgress = progress.coerceIn(0.0, 1.0)
        val index = (clampedProgress * links.size).toInt().coerceIn(0, links.lastIndex)
        val targetLink = links[index]
        val mediaType = targetLink.mediaType?.toString() ?: "application/xhtml+xml"
        val hrefStr = targetLink.href.toString().replace("\"", "\\\"")
        val locatorJson = """{"href":"$hrefStr","type":"$mediaType","locations":{"totalProgression":$clampedProgress}}""".trimIndent()
        runCatching { Locator.fromJSON(JSONObject(locatorJson)) }
            .getOrNull()
            ?.let { navigator.go(it) }
            ?: navigator.go(targetLink)
    }

    private fun restartByProgress(readingOrder: List<Link>, progress: Double) {
        val links = readingOrder.takeIf { it.isNotEmpty() } ?: return
        val clampedProgress = progress.coerceIn(0.0, 1.0)
        val index = (clampedProgress * links.size).toInt().coerceIn(0, links.lastIndex)
        restartWithLocator(buildLocatorJson(links[index], totalProgression = clampedProgress, readingOrder = links))
    }

    private fun restartByPage(readingOrder: List<Link>, page: Int) {
        val links = readingOrder.takeIf { it.isNotEmpty() } ?: return
        val index = (page - 1).coerceIn(0, links.lastIndex)
        val total = (index.toDouble() / links.size).coerceIn(0.0, 1.0)
        restartWithLocator(buildLocatorJson(links[index], totalProgression = total, readingOrder = links))
    }

    private fun buildLocatorJson(link: Link, totalProgression: Double? = null, readingOrder: List<Link> = emptyList()): String {
        val mediaType = link.mediaType?.toString() ?: "application/xhtml+xml"
        val hrefStr = link.href.toString().replace("\"", "\\\"")
        val total = totalProgression
            ?: LocatorProgress.estimateTotalForLink(readingOrder, link)
        return if (total != null)
            """{"href":"$hrefStr","type":"$mediaType","locations":{"totalProgression":${total.coerceIn(0.0, 1.0)}}}"""
        else
            """{"href":"$hrefStr","type":"$mediaType","locations":{}}"""
    }

    private fun restartWithLocator(locatorJson: String) {
        val v = view ?: return
        if (!isAdded) return
        val container = (v.parent as? ViewGroup)?.id ?: return
        if (container == View.NO_ID) return
        parentFragmentManager.beginTransaction()
            .replace(container, ReadiumHostFragment.newInstance(sessionId, locatorJson), tag(sessionId))
            .commitNowAllowingStateLoss()
    }

    private suspend fun applyAnnotationDecorations(serverId: String, bookId: Int, navigator: Navigator) {
        val decorable = navigator as? DecorableNavigator ?: return
        val annotations = ReaderDatabase.get(requireContext()).readerDao().getAnnotations(serverId, bookId)
        val decorations = annotations.mapNotNull { annotation ->
            runCatching { Locator.fromJSON(JSONObject(annotation.locatorJson)) }
                .getOrNull()
                ?.let { locator ->
                    Decoration(
                        id = "annotation-${annotation.id}",
                        locator = locator,
                        style = Decoration.Style.Highlight(0x66FFD54F, false)
                    )
                }
        }
        decorable.applyDecorations(decorations, "talebook-annotations")
    }

    private suspend fun applyTtsHighlight(navigator: Navigator, locatorJson: String) {
        val decorable = navigator as? DecorableNavigator ?: return
        if (locatorJson.isBlank()) {
            decorable.applyDecorations(emptyList(), "talebook-tts")
            return
        }
        val locator = runCatching { Locator.fromJSON(JSONObject(locatorJson)) }.getOrNull() ?: return
        decorable.applyDecorations(
            listOf(Decoration(id = "tts-current", locator = locator, style = Decoration.Style.Highlight(0x66BBDDFF, false))),
            "talebook-tts"
        )
    }

    private suspend fun activeServerId(): String = SettingsRepository(requireContext().applicationContext).activeLibraryServerId.first()

    private fun findLink(links: List<Link>, href: String): Link? {
        for (link in links) {
            if (link.href.toString() == href) return link
            val child = findLink(link.children, href)
            if (child != null) return child
        }
        return null
    }

    private fun buildChapterPath(href: String, toc: List<Link>, bookTitle: String, readingOrder: List<Link> = emptyList()): String {
        val target = findTocItem(toc, href, readingOrder)
        val chapterTitle = target?.title?.takeIf { it.isNotBlank() } ?: ""
        return truncate(chapterTitle, 20)
    }

    private fun findTocItem(toc: List<Link>, href: String, readingOrder: List<Link> = emptyList()): Link? {
        for (link in toc) {
            if (LocatorProgress.hrefMatches(link.href.toString(), href)) return link
            val child = findTocItem(link.children, href, emptyList())
            if (child != null) return child
        }
        if (readingOrder.isNotEmpty()) {
            val roHref = readingOrder.firstOrNull { LocatorProgress.hrefMatches(it.href.toString(), href) }
                ?.href?.toString()
            if (roHref != null && roHref != href) {
                for (link in toc) {
                    if (LocatorProgress.hrefMatches(link.href.toString(), roHref)) return link
                    val child = findTocItem(link.children, roHref, emptyList())
                    if (child != null) return child
                }
            }
        }
        return null
    }

    private fun truncate(text: String, max: Int): String {
        if (text.length <= max) return text
        return text.take(max) + "..."
    }

    private fun applyReaderSettings(session: ReadiumSession, navigator: Navigator, settings: ReaderDisplaySettings) {
        val activeBg = resolveActiveBackground(settings.readerBackgroundColor, settings.appDark)
        val color = rgbToColor(activeBg)
        applyBackground(settings)
        val window = activity?.window
        if (window != null) {
            window.attributes = window.attributes.apply {
                screenBrightness = if (settings.useSystemBrightness) -1f else settings.brightness.coerceIn(0.0f, 1.0f)
            }
            if (settings.keepScreenOn) {
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        if (session is EpubReadiumSession && navigator is EpubNavigatorFragment) {
            val avoidLargePublisherFonts = session.isRemote && session.hasLargeEmbeddedFonts && !settings.forcePublisherFonts
            val readiumFontFamily = when (settings.fontFamily) {
                ReaderFontFamily.DEFAULT -> if (avoidLargePublisherFonts) org.readium.r2.navigator.preferences.FontFamily.SANS_SERIF else null
                ReaderFontFamily.SERIF -> org.readium.r2.navigator.preferences.FontFamily.SERIF
                ReaderFontFamily.SANS_SERIF -> org.readium.r2.navigator.preferences.FontFamily.SANS_SERIF
                ReaderFontFamily.MONOSPACE -> org.readium.r2.navigator.preferences.FontFamily.MONOSPACE
                ReaderFontFamily.CUSTOM -> null
            }
            val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val twoPageActive = settings.twoPageMode && isLandscape
            val imagePreset = isImagePresetForSettings(settings)
            navigator.submitPreferences(
                EpubPreferences(
                    // Alpha 0: R2ViewPager.setBackgroundColor receives the raw ARGB int (transparent),
                    // while Color.toCss masks to #RRGGBB for HTML (handled by injectBackgroundImageCss).
                    backgroundColor = if (imagePreset) ReadiumColor(android.graphics.Color.TRANSPARENT) else readiumColorOrNull(settings.readerBackgroundColor),
                    fontFamily = readiumFontFamily,
                    fontSize = settings.fontScale.toDouble(),
                    letterSpacing = settings.letterSpacing.takeIf { it > 0f }?.toDouble(),
                    lineHeight = settings.lineHeight.toDouble(),
                    pageMargins = settings.pageMargins.toDouble(),
                    paragraphSpacing = settings.paragraphSpacing.toDouble(),
                    publisherStyles = settings.publisherStyles,
                    scroll = settings.scrollMode,
                    textColor = readiumColorOrNull(settings.readerTextColor),
                )
            )
            injectLayoutCss(navigator, settings)
            injectBasicCss(navigator, settings, avoidLargePublisherFonts, twoPageActive)
            injectBackgroundImageCss(navigator, settings)
            injectCustomFontCss(navigator, settings)
        }
    }

    private fun applyBackground(settings: ReaderDisplaySettings) {
        if (isImagePresetForSettings(settings)) {
            view?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            activity?.window?.decorView?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            return
        }
        val activeBg = resolveActiveBackground(settings.readerBackgroundColor, settings.appDark)
        val color = rgbToColor(activeBg)
        view?.setBackgroundColor(color)
        activity?.window?.decorView?.setBackgroundColor(color)
    }

    private fun injectLayoutCss(navigator: EpubNavigatorFragment, settings: ReaderDisplaySettings) {
        val css = buildString {
            if (settings.letterSpacing != 0f) {
                val ls = String.format(java.util.Locale.US, "%.2f", settings.letterSpacing)
                append("html, body, body *, p, div, span, h1, h2, h3, h4, h5, h6, li, td, th, blockquote, dd, dt, figcaption, caption, address { letter-spacing: ${ls}em !important; }\n")
            }
            if (settings.lineHeight != 1.5f) {
                val lh = String.format(java.util.Locale.US, "%.2f", settings.lineHeight)
                append("html, body { line-height: ${lh} !important; }\n")
            }
            if (settings.paragraphSpacing != 1.0f) {
                val ps = String.format(java.util.Locale.US, "%.2f", settings.paragraphSpacing)
                append("p { margin-top: ${ps}em !important; margin-bottom: ${ps}em !important; }\n")
            }
        }
        injectStyle(navigator, "talebook-reader-layout-css", css)
    }

    private fun injectBasicCss(navigator: EpubNavigatorFragment, settings: ReaderDisplaySettings, avoidLargePublisherFonts: Boolean, twoPageActive: Boolean) {
        val customCssChanged = lastTwoPageActive != twoPageActive ||
            lastAvoidLargePublisherFonts != avoidLargePublisherFonts ||
            lastFontFamily != settings.fontFamily
        val css = buildString {
            append("html, :root { height: 100% !important; max-height: 100% !important; }")
            if (avoidLargePublisherFonts || settings.fontFamily == ReaderFontFamily.CUSTOM) {
                append("html, body, body *, p, div, span, a, li, blockquote, h1, h2, h3, h4, h5, h6 { font-family: ${publisherFontFamilyCss(settings.fontFamily)} !important; }")
            }
            if (twoPageActive) {
                append("body { -webkit-column-count: 2 !important; column-count: 2 !important; column-width: auto !important; column-gap: 24px !important; column-fill: balance !important; max-width: none !important; width: auto !important; }")
            }
        }
        if (customCssChanged || settings.fontFamily == ReaderFontFamily.CUSTOM) {
            lastTwoPageActive = twoPageActive
            lastAvoidLargePublisherFonts = avoidLargePublisherFonts
            lastFontFamily = settings.fontFamily
            injectStyle(navigator, "talebook-reader-custom-css", css)
        }
        val vPad = String.format(java.util.Locale.US, "%.2f", settings.pageMarginVertical)
        injectPagePadding(navigator, "${vPad}rem")
        if (twoPageActive != (lastTwoPageActive ?: twoPageActive)) {
            val beforeLocator = navigator.currentLocator.value
            viewLifecycleOwner.lifecycleScope.launch {
                delay(150)
                if (isAdded) {
                    runCatching { navigator.go(beforeLocator) }
                }
            }
        }
    }

    private fun injectPagePadding(navigator: EpubNavigatorFragment, padding: String) {
        val js = """
            (function(){
                var PAD = '$padding';
                function apply() {
                    var html = document.documentElement;
                    var body = document.body;
                    if (!html || !body) return false;
                    html.style.setProperty('padding-top', PAD, 'important');
                    html.style.setProperty('padding-bottom', PAD, 'important');
                    body.style.setProperty('padding-top', PAD, 'important');
                    body.style.setProperty('padding-bottom', PAD, 'important');
                    return true;
                }
                if (!apply()) {
                    document.addEventListener('DOMContentLoaded', function once() {
                        apply();
                        document.removeEventListener('DOMContentLoaded', once);
                    });
                }
            })();
        """.trimIndent()
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                navigator.evaluateJavascript(js)
            }.onFailure { error ->
                Log.w("TaleReadium", "Page padding injection failed: " + error.message)
            }
        }
    }

    private fun injectStyle(navigator: EpubNavigatorFragment, id: String, css: String) {
        val escaped = css.replace("'", "\\'").replace("\n", " ")
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                navigator.evaluateJavascript(
                    "(function() { var old = document.getElementById('$id'); if (old) old.remove(); var s = document.createElement('style'); s.id = '$id'; s.type = 'text/css'; s.innerHTML = '$escaped'; (document.head || document.documentElement).appendChild(s); })();"
                )
            }.onFailure { error ->
                Log.w("TaleReadium", "Custom CSS injection skipped: " + error.message)
            }
        }
    }

    private fun injectBackgroundImageCss(navigator: EpubNavigatorFragment, settings: ReaderDisplaySettings) {
        val palette = resolveActivePalette(settings)
        val resName = palette?.imageResName
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                if (resName.isNullOrEmpty()) {
                    navigator.evaluateJavascript(
                        "var old=document.getElementById('talebook-reader-bgimage-css');if(old)old.remove();'ok'"
                    )
                } else {
                    // Beat ReadiumCSS-before.css :root{background-color:var(--RS__backgroundColor)!important}
                    // and after.css :root[style*="--USER__backgroundColor"] (0,2,0).
                    // Style tag is appended after Readium's links, so equal-specificity !important wins.
                    // Inline --USER__backgroundColor from html[style] loses to stylesheet !important.
                    val css = buildString {
                        append(":root,:root[style],html{background-color:transparent !important;background-image:none !important;")
                        append("--RS__backgroundColor:transparent !important;--USER__backgroundColor:transparent !important;}")
                        append(":root[style*=\"--USER__backgroundColor\"],:root[style*=\"--USER__backgroundColor\"] *{background-color:transparent !important;background-image:none !important;}")
                        append(":root[style*=\"readium-sepia-on\"],:root[style*=\"readium-night-on\"]{--RS__backgroundColor:transparent !important;}")
                        append("html body,body,html body *,body *{background-color:transparent !important;background-image:none !important;}")
                    }
                    val escaped = css.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ")
                    navigator.evaluateJavascript(
                        "(function(){var s=document.getElementById('talebook-reader-bgimage-css');" +
                            "if(!s){s=document.createElement('style');s.id='talebook-reader-bgimage-css';" +
                            "(document.head||document.documentElement).appendChild(s);}" +
                            "s.textContent='$escaped';return 'ok';})()"
                    )
                }
            }
        }
    }

    private fun resolveActivePalette(settings: ReaderDisplaySettings): com.talebook.app.ui.theme.ReaderThemePalette? {
        val dayPreset = settings.dayPresetId
        val nightPreset = settings.nightPresetId
        return if (settings.appDark) {
            com.talebook.app.ui.theme.ThemePresets.night.firstOrNull { it.id == nightPreset }
                ?: com.talebook.app.ui.theme.ThemePresets.night.first()
        } else {
            com.talebook.app.ui.theme.ThemePresets.day.firstOrNull { it.id == dayPreset }
                ?: com.talebook.app.ui.theme.ThemePresets.day.first()
        }
    }

    private fun isImagePresetForSettings(settings: ReaderDisplaySettings): Boolean {
        return resolveActivePalette(settings)?.imageResName?.isNotEmpty() == true
    }

    private fun setNavigatorWebViewTransparent(navigator: Navigator) {
        val root = (navigator as? VisualNavigator)?.publicationView ?: return
        root.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        makeAllChildrenTransparent(root)
    }

    private fun startImageTransparencyWatch(navigator: Navigator) {
        val epub = navigator as? EpubNavigatorFragment ?: return
        transparencyWatchJob?.cancel()
        transparencyWatchJob = viewLifecycleOwner.lifecycleScope.launch {
            epub.settings.collect {
                val settings = ReadiumSessionStore.get(sessionId)?.displaySettings ?: return@collect
                if (isImagePresetForSettings(settings)) {
                    // Readium re-applies R2ViewPager background on settings change; re-force transparent after it.
                    setNavigatorWebViewTransparent(epub)
                    view?.post { setNavigatorWebViewTransparent(epub) }
                }
            }
        }
    }

    private fun makeAllChildrenTransparent(view: View) {
        view.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        if (view is android.webkit.WebView) {
            view.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                makeAllChildrenTransparent(view.getChildAt(i))
            }
        }
    }

    private var fontStreamSeq = 0

    private fun injectCustomFontCss(navigator: EpubNavigatorFragment, settings: ReaderDisplaySettings) {
        val path = settings.customFontPath
        if (settings.fontFamily != ReaderFontFamily.CUSTOM || path.isEmpty()) {
            fontStreamSeq++
            injectStyle(navigator, "talebook-reader-fontface-css", "")
            viewLifecycleOwner.lifecycleScope.launch {
                runCatching { navigator.evaluateJavascript("window.__tbFontKey=null;window.__tbFont=null;'ok'") }
            }
            return
        }
        val file = java.io.File(path)
        if (!file.exists() || !file.isFile) {
            fontStreamSeq++
            injectStyle(navigator, "talebook-reader-fontface-css", "")
            viewLifecycleOwner.lifecycleScope.launch {
                runCatching { navigator.evaluateJavascript("window.__tbFontKey=null;window.__tbFont=null;'ok'") }
            }
            return
        }
        val key = "$path|${file.lastModified()}|${file.length()}"
        val ext = path.substringAfterLast('.').lowercase()
        val fmt = when (ext) {
            "otf" -> "opentype"
            "woff" -> "woff"
            "woff2" -> "woff2"
            else -> "truetype"
        }
        val mime = when (ext) {
            "otf" -> "font/otf"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            else -> "font/ttf"
        }
        val seq = ++fontStreamSeq
        viewLifecycleOwner.lifecycleScope.launch {
            val already = runCatching {
                navigator.evaluateJavascript(
                    "!!(window.__tbFontKey === ${jsStr(key)} && !!document.getElementById('talebook-reader-fontface-css'))"
                )
            }.getOrNull()
            if (seq != fontStreamSeq) return@launch
            if (already == "true") return@launch
            runCatching {
                navigator.evaluateJavascript(
                    "window.__tbFontKey=null;window.__tbFont={parts:[],key:${jsStr(key)},fmt:${jsStr(fmt)},mime:${jsStr(mime)},done:false};'ok'"
                )
                file.inputStream().use { input ->
                    val buf = ByteArray(300 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        if (seq != fontStreamSeq) return@use
                        val b64 = if (n == buf.size) {
                            android.util.Base64.encodeToString(buf, android.util.Base64.NO_WRAP)
                        } else {
                            android.util.Base64.encodeToString(buf, 0, n, android.util.Base64.NO_WRAP)
                        }
                        navigator.evaluateJavascript(
                            "(function(){var s=${jsStr(b64)};var bin=atob(s);var a=new Uint8Array(bin.length);for(var i=0;i<bin.length;i++)a[i]=bin.charCodeAt(i);window.__tbFont.parts.push(a);})()"
                        )
                    }
                }
                if (seq != fontStreamSeq) return@launch
                navigator.evaluateJavascript(
                    """
                    (function(){
                        var f=window.__tbFont;
                        if(!f||f.key!==${jsStr(key)})return 'stale';
                        var blob=new Blob(f.parts,{type:f.mime});
                        f.parts=[];
                        var url=URL.createObjectURL(blob);
                        var css="@font-face{font-family:'talebook-custom';src:url("+url+") format('"+f.fmt+"');font-display:swap;}";
                        var old=document.getElementById('talebook-reader-fontface-css');
                        if(old)old.remove();
                        var st=document.createElement('style');
                        st.id='talebook-reader-fontface-css';
                        st.textContent=css;
                        (document.head||document.documentElement).appendChild(st);
                        window.__tbFontKey=f.key;
                        window.__tbFont=null;
                        return 'done';
                    })();
                    """.trimIndent()
                )
            }.onFailure { e ->
                Log.w("TaleReadium", "Font stream failed: ${e.message}")
            }
        }
    }

    private fun jsStr(s: String): String =
        "'" + s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ") + "'"

    private fun publisherFontFamilyCss(fontFamily: ReaderFontFamily): String = when (fontFamily) {
        ReaderFontFamily.SERIF -> "serif"
        ReaderFontFamily.MONOSPACE -> "monospace"
        ReaderFontFamily.DEFAULT,
        ReaderFontFamily.SANS_SERIF -> "sans-serif"
        ReaderFontFamily.CUSTOM -> "'talebook-custom'"
    }

    private fun readiumColorOrNull(rgb: Long): ReadiumColor? {
        if (rgb == 0L) return null
        return ReadiumColor(rgbToColor(rgb))
    }

    private fun resolveActiveBackground(rgb: Long?, dark: Boolean): Long {
        if (rgb == null || rgb == 0L) {
            return if (dark) {
                ThemePresets.night.first { it.id == ThemePresets.NIGHT_SYSTEM }.background
            } else {
                ThemePresets.day.first { it.id == ThemePresets.DAY_SYSTEM }.background
            }
        }
        return rgb
    }

    private fun rgbToColor(rgb: Long): Int = android.graphics.Color.rgb(
        ((rgb shr 16) and 0xFF).toInt(),
        ((rgb shr 8) and 0xFF).toInt(),
        (rgb and 0xFF).toInt()
    )

    private fun persistEpubPrefsIfReady(navigator: EpubNavigatorFragment) {
        runCatching {
            val session = ReadiumSessionStore.get(sessionId) ?: return@runCatching
            val settings = session.displaySettings
            val repo = SettingsRepository(requireContext().applicationContext)
            viewLifecycleOwner.lifecycleScope.launch {
                repo.saveReaderDisplaySettings(
                    fontScale = settings.fontScale,
                    fontFamily = when (settings.fontFamily) {
                        ReaderFontFamily.DEFAULT -> "default"
                        ReaderFontFamily.SERIF -> "serif"
                        ReaderFontFamily.SANS_SERIF -> "sans_serif"
                        ReaderFontFamily.MONOSPACE -> "monospace"
                        ReaderFontFamily.CUSTOM -> "custom"
                    },
                    lineHeight = settings.lineHeight,
                    brightness = settings.brightness,
                    scrollMode = settings.scrollMode,
                    useSystemBrightness = settings.useSystemBrightness,
                    theme = when (settings.theme) {
                        ReaderTheme.SYSTEM -> "system"
                        ReaderTheme.LIGHT -> "light"
                        ReaderTheme.SEPIA -> "sepia"
                        ReaderTheme.DARK -> "dark"
                        ReaderTheme.PINK -> "pink"
                        ReaderTheme.BLUE -> "blue"
                        ReaderTheme.GREEN -> "green"
                        ReaderTheme.CUSTOM -> "custom"
                    },
                    tapPageTurn = settings.tapPageTurn,
                    pageTurnMode = when (settings.pageTurnMode) {
                        ReaderPageTurnMode.INVERTED_L -> "inverted_l"
                        ReaderPageTurnMode.LEFT_RIGHT -> "left_right"
                        ReaderPageTurnMode.RIGHT_ONLY -> "right_only"
                        ReaderPageTurnMode.DISABLED -> "disabled"
                    },
                    pageMargins = settings.pageMargins,
                    pageMarginVertical = settings.pageMarginVertical,
                    paragraphSpacing = settings.paragraphSpacing,
                    letterSpacing = settings.letterSpacing,
                    publisherStyles = settings.publisherStyles,
                    forcePublisherFonts = settings.forcePublisherFonts,
                    keepScreenOn = settings.keepScreenOn,
                    pageAnimation = when (settings.pageAnimation) {
                        ReaderPageAnimation.SMOOTH -> "smooth"
                        ReaderPageAnimation.SLIDE -> "slide"
                        ReaderPageAnimation.COVER -> "cover"
                        ReaderPageAnimation.OVERRIDE -> "override"
                        ReaderPageAnimation.NONE -> "none"
                    },
                    forceTapAnimation = settings.forceTapAnimation,
                    scrollTapPageTurn = settings.scrollTapPageTurn,
                    scrollKeepLine = settings.scrollKeepLine,
                    volumeKeyPageTurn = settings.volumeKeyPageTurn,
                    twoPageMode = settings.twoPageMode,
                    customFontPath = settings.customFontPath,
                    customFontName = settings.customFontName,
                )
            }
        }.onFailure { e ->
            Log.w("TaleReadium", "persistEpubPrefs failed: ${e.message}")
        }
    }

    private fun stabilizeInitialLayout(view: View) {
        view.post {
            view.requestApplyInsets()
            view.requestLayout()
        }
        viewLifecycleOwner.lifecycleScope.launch {
            delay(180)
            view.requestApplyInsets()
            view.requestLayout()
        }
    }

    private fun hideSystemBars() {
        val window = activity?.window ?: return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }

    private fun showSystemBars() {
        val window = activity?.window ?: return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.insetsController?.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    companion object {
        private const val ARG_SESSION_ID = "session_id"
        private const val ARG_JUMP_TO = "jump_to"
        private const val NAVIGATOR_TAG = "readium_navigator"

        fun newInstance(sessionId: Long, jumpTo: String? = null): ReadiumHostFragment = ReadiumHostFragment().apply {
            arguments = Bundle().apply {
                putLong(ARG_SESSION_ID, sessionId)
                if (jumpTo != null) putString(ARG_JUMP_TO, jumpTo)
            }
        }

        fun tag(sessionId: Long): String = "readium_host_$sessionId"
    }
}
