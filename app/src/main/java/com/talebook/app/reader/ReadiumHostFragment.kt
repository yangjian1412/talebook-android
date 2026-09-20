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
                paginationListener = this
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
        val dark = session?.displaySettings?.appDark == true
        val activeBg = resolveActiveBackground(session?.displaySettings?.readerBackgroundColor, dark)
        setBackgroundColor(rgbToColor(activeBg))
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

    private fun setupNavigator(view: View, session: ReadiumSession) {
        val navigator = childFragmentManager.findFragmentByTag(NAVIGATOR_TAG) as? Navigator ?: return
        applyReaderSettings(session, navigator, session.displaySettings)
        stabilizeInitialLayout(view)
        view.post {
            if (!isAdded || view == null) return@post
            val nav = childFragmentManager.findFragmentByTag(NAVIGATOR_TAG) as? EpubNavigatorFragment ?: return@post
            injectLayoutCss(nav, session.displaySettings)
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
                        val toc = session.publication.tableOfContents
                        val bookTitle = session.publication.metadata.title ?: ""
                        val chapter = buildChapterPath(locator.href.toString(), toc, bookTitle)
                        if (chapter.isNotBlank()) lastChapterName = chapter
                        ReadiumUiEvents.emitCurrentChapterPath(sessionId, chapter.ifBlank { lastChapterName })
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
                                restartWithLocator(buildLocatorJson(link))
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

    override fun onPageChanged(pageIndex: Int, totalPages: Int, locator: Locator) {}

    override fun onPageLoaded() {
        val session = ReadiumSessionStore.get(sessionId) ?: return
        val navigator = childFragmentManager.findFragmentByTag(NAVIGATOR_TAG) as? Navigator ?: return
        if (session is EpubReadiumSession && navigator is EpubNavigatorFragment) {
            val avoidLargePublisherFonts = session.isRemote && session.hasLargeEmbeddedFonts && !session.displaySettings.forcePublisherFonts
            val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val twoPageActive = session.displaySettings.twoPageMode && isLandscape
            injectBasicCss(navigator, session.displaySettings, avoidLargePublisherFonts, twoPageActive)
        }
    }

private suspend fun saveProgress(bookId: Int, locator: Locator) {
        val session = ReadiumSessionStore.get(sessionId) ?: return
        val serverId = session.serverId
        val progression = locator.locations.totalProgression ?: 0.0
        val now = System.currentTimeMillis()
        val dao = ReaderDatabase.get(requireContext()).readerDao()
        dao.saveProgress(
                ReadingProgressEntity(
                    serverId = serverId,
                    bookId = bookId,
                locatorJson = locator.toJSON().toString(),
                progression = progression,
                updatedAt = now
            )
        )
        val existing = dao.getRecentEntry(serverId, bookId)
        if (existing != null) {
            dao.upsertRecentEntry(existing.copy(progression = progression, updatedAt = now))
        }
        ReadiumUiEvents.emitProgress(sessionId, progression)
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
        restartWithLocator(buildLocatorJson(links[index], totalProgression = clampedProgress))
    }

    private fun restartByPage(readingOrder: List<Link>, page: Int) {
        val links = readingOrder.takeIf { it.isNotEmpty() } ?: return
        restartWithLocator(buildLocatorJson(links[(page - 1).coerceIn(0, links.lastIndex)]))
    }

    private fun buildLocatorJson(link: Link, totalProgression: Double? = null): String {
        val mediaType = link.mediaType?.toString() ?: "application/xhtml+xml"
        val hrefStr = link.href.toString().replace("\"", "\\\"")
        return if (totalProgression != null)
            """{"href":"$hrefStr","type":"$mediaType","locations":{"totalProgression":$totalProgression}}"""
        else
            """{"href":"$hrefStr","type":"$mediaType"}"""
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

    private fun buildChapterPath(href: String, toc: List<Link>, bookTitle: String): String {
        val cleanHref = href.substringBefore("#").trimEnd('/')
        val target = findTocItem(toc, cleanHref)
        val chapterTitle = target?.title?.takeIf { it.isNotBlank() } ?: ""
        return truncate(chapterTitle, 20)
    }

    private fun findTocItem(toc: List<Link>, href: String): Link? {
        for (link in toc) {
            val linkHref = link.href.toString().substringBefore("#").trimEnd('/')
            if (linkHref == href || linkHref.endsWith(href) || href.endsWith(linkHref)) return link
            val child = findTocItem(link.children, href)
            if (child != null) return child
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
        view?.setBackgroundColor(color)
        activity?.window?.decorView?.setBackgroundColor(color)
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
            }
            val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val twoPageActive = settings.twoPageMode && isLandscape
            navigator.submitPreferences(
                EpubPreferences(
                    backgroundColor = readiumColorOrNull(settings.readerBackgroundColor),
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
        }
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
        val css = buildString {
            append("html, body { margin: 0 !important; padding-top: 0 !important; padding-bottom: 0 !important; }")
            append("html, :root { height: 100% !important; max-height: 100% !important; }")
            if (avoidLargePublisherFonts) {
                append("html, body, body *, p, div, span, a, li, blockquote, h1, h2, h3, h4, h5, h6 { font-family: ${publisherFontFamilyCss(settings.fontFamily)} !important; }")
            }
            if (twoPageActive) {
                append("body { -webkit-column-count: 2 !important; column-count: 2 !important; column-width: auto !important; column-gap: 24px !important; column-fill: balance !important; max-width: none !important; width: auto !important; }")
            }
        }
        val isTwoPageActiveChanged = lastTwoPageActive != null && lastTwoPageActive != twoPageActive
        lastTwoPageActive = twoPageActive
        injectStyle(navigator, "talebook-reader-custom-css", css)
        if (isTwoPageActiveChanged) {
            val beforeLocator = navigator.currentLocator.value
            viewLifecycleOwner.lifecycleScope.launch {
                delay(150)
                if (isAdded) {
                    runCatching { navigator.go(beforeLocator) }
                }
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

    private fun publisherFontFamilyCss(fontFamily: ReaderFontFamily): String = when (fontFamily) {
        ReaderFontFamily.SERIF -> "serif"
        ReaderFontFamily.MONOSPACE -> "monospace"
        ReaderFontFamily.DEFAULT,
        ReaderFontFamily.SANS_SERIF -> "sans-serif"
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
