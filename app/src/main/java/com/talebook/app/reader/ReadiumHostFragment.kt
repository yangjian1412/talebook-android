package com.talebook.app.reader

import android.os.Bundle
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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.data.ReadError

@OptIn(ExperimentalReadiumApi::class)
class ReadiumHostFragment : Fragment(), EpubNavigatorFragment.Listener, PdfNavigatorFragment.Listener {
    private val sessionId: Long by lazy { requireArguments().getLong(ARG_SESSION_ID) }
    private val containerId: Int by lazy { View.generateViewId() }

    override fun onCreate(savedInstanceState: Bundle?) {
        val session = ReadiumSessionStore.get(sessionId)
        if (session == null) {
            super.onCreate(savedInstanceState)
            requireActivity().onBackPressedDispatcher.onBackPressed()
            return
        }

        childFragmentManager.fragmentFactory = when (session) {
            is EpubReadiumSession -> session.navigatorFactory.createFragmentFactory(
                initialLocator = session.initialLocator,
                listener = this
            )
            is PdfReadiumSession -> session.navigatorFactory.createFragmentFactory(
                initialLocator = session.initialLocator,
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
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val session = ReadiumSessionStore.get(sessionId) ?: return
        hideSystemBars()
        if (savedInstanceState == null) {
            childFragmentManager.commitNow {
                when (session) {
                    is EpubReadiumSession -> add(containerId, EpubNavigatorFragment::class.java, Bundle(), NAVIGATOR_TAG)
                    is PdfReadiumSession -> add(containerId, PdfNavigatorFragment::class.java, Bundle(), NAVIGATOR_TAG)
                }
            }
        }
        val navigator = childFragmentManager.findFragmentByTag(NAVIGATOR_TAG) as? Navigator ?: return
        applyReaderSettings(session, navigator, session.displaySettings)
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
                    if (session.displaySettings.scrollMode) {
                        if (session.displaySettings.scrollTapPageTurn) {
                            handleScrollTap(navigator, x, y, viewWidth.toFloat(), viewHeight.toFloat(), session.displaySettings)
                        } else {
                            false
                        }
                    } else {
                        handlePageTurnTap(navigator, x, y, viewWidth.toFloat(), viewHeight.toFloat(), session.displaySettings.pageTurnMode)
                    }
                } else {
                    false
                }
            }

            override fun onDrag(event: DragEvent): Boolean = false

            override fun onKey(event: KeyEvent): Boolean {
                if (!session.displaySettings.volumeKeyPageTurn) return false
                val key = event.key.toString().lowercase()
                return when {
                    key.contains("volumedown") || key.contains("volume_down") -> goForward(navigator)
                    key.contains("volumeup") || key.contains("volume_up") -> goBackward(navigator)
                    else -> false
                }
            }
        })
        viewLifecycleOwner.lifecycleScope.launch { applyAnnotationDecorations(session.bookId, navigator) }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                navigator.currentLocator
                    .onEach { locator -> saveProgress(session.bookId, locator) }
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
                                ?.let { navigator.go(it) }
                        }
                    }
                    .launchIn(this)
                ReadiumUiEvents.goToLinks
                    .onEach { (targetSessionId, href) ->
                        if (targetSessionId == sessionId) {
                            val link = findLink(session.publication.tableOfContents, href)
                                ?: findLink(session.publication.readingOrder, href)
                            if (link != null) {
                                (navigator as? HyperlinkNavigator)?.go(link)
                            }
                        }
                    }
                    .launchIn(this)
                ReadiumUiEvents.goToProgress
                    .onEach { (targetSessionId, progress) ->
                        if (targetSessionId == sessionId) goToProgress(session.publication.readingOrder, navigator, progress)
                    }
                    .launchIn(this)
                ReadiumUiEvents.goToPage
                    .onEach { (targetSessionId, page) ->
                        if (targetSessionId == sessionId) goToPage(session.publication.readingOrder, navigator, page)
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
                ReadiumUiEvents.annotationChanged
                    .onEach { targetSessionId ->
                        if (targetSessionId == sessionId) applyAnnotationDecorations(session.bookId, navigator)
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
        hideSystemBars()
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

    private suspend fun saveProgress(bookId: Int, locator: Locator) {
        val progression = locator.locations.totalProgression ?: 0.0
        ReaderDatabase.get(requireContext()).readerDao().saveProgress(
            ReadingProgressEntity(
                bookId = bookId,
                locatorJson = locator.toJSON().toString(),
                progression = progression,
                updatedAt = System.currentTimeMillis()
            )
        )
        ReadiumUiEvents.emitProgress(sessionId, progression)
    }

    private suspend fun addBookmark(bookId: Int, locator: Locator) {
        ReaderDatabase.get(requireContext()).readerDao().addBookmark(
            ReaderBookmarkEntity(
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
        val selection = (navigator as? SelectableNavigator)?.currentSelection()
        val locator = selection?.locator ?: navigator.currentLocator.value
        val selectedText = locatorTitle(locator)
        ReaderDatabase.get(requireContext()).readerDao().saveAnnotation(
            ReaderAnnotationEntity(
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
        applyAnnotationDecorations(bookId, navigator)
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

    private fun goBackward(navigator: Navigator): Boolean = when (navigator) {
        is EpubNavigatorFragment -> navigator.goBackward(ReadiumSessionStore.get(sessionId)?.displaySettings?.pageAnimation != ReaderPageAnimation.NONE)
        is PdfNavigatorFragment<*, *> -> navigator.goBackward(ReadiumSessionStore.get(sessionId)?.displaySettings?.pageAnimation != ReaderPageAnimation.NONE)
        else -> false
    }

    private fun goForward(navigator: Navigator): Boolean = when (navigator) {
        is EpubNavigatorFragment -> navigator.goForward(ReadiumSessionStore.get(sessionId)?.displaySettings?.pageAnimation != ReaderPageAnimation.NONE)
        is PdfNavigatorFragment<*, *> -> navigator.goForward(ReadiumSessionStore.get(sessionId)?.displaySettings?.pageAnimation != ReaderPageAnimation.NONE)
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
        if (navigator is EpubNavigatorFragment) {
            val keepLine = if (settings.scrollKeepLine) "true" else "false"
            viewLifecycleOwner.lifecycleScope.launch {
                navigator.evaluateJavascript(
                    """
                    (function() {
                        const lineHeight = $keepLine ? parseFloat(getComputedStyle(document.body).lineHeight || '24') : 0;
                        const delta = Math.max(Math.floor(window.innerHeight * 0.84 - lineHeight), Math.floor(window.innerHeight * 0.55));
                        const start = window.scrollY || document.documentElement.scrollTop || 0;
                        const target = start + (${if (forward) "delta" else "-delta"});
                        const duration = 520;
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
        val linePx = if (settings.scrollKeepLine) (24 * resources.displayMetrics.density).toInt() else 0
        val delta = ((height * 0.84f).toInt() - linePx).coerceAtLeast((height * 0.55f).toInt())
        val visual = navigator as? VisualNavigator ?: return false
        visual.publicationView.scrollBy(0, if (forward) delta else -delta)
        return true
    }

    private fun goToProgress(readingOrder: List<Link>, navigator: Navigator, progress: Double) {
        val links = readingOrder.takeIf { it.isNotEmpty() } ?: return
        val index = (progress.coerceIn(0.0, 1.0) * (links.size - 1)).toInt().coerceIn(0, links.lastIndex)
        navigator.go(links[index])
    }

    private fun goToPage(readingOrder: List<Link>, navigator: Navigator, page: Int) {
        val links = readingOrder.takeIf { it.isNotEmpty() } ?: return
        navigator.go(links[(page - 1).coerceIn(0, links.lastIndex)])
    }

    private suspend fun applyAnnotationDecorations(bookId: Int, navigator: Navigator) {
        val decorable = navigator as? DecorableNavigator ?: return
        val annotations = ReaderDatabase.get(requireContext()).readerDao().getAnnotations(bookId)
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

    private fun findLink(links: List<Link>, href: String): Link? {
        for (link in links) {
            if (link.href.toString() == href) return link
            val child = findLink(link.children, href)
            if (child != null) return child
        }
        return null
    }

    private fun applyReaderSettings(session: ReadiumSession, navigator: Navigator, settings: ReaderDisplaySettings) {
        val window = activity?.window
        if (window != null) {
            window.attributes = window.attributes.apply {
                screenBrightness = if (settings.useSystemBrightness) -1f else settings.brightness.coerceIn(0.3f, 1.0f)
            }
            if (settings.keepScreenOn) {
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        if (session is EpubReadiumSession && navigator is EpubNavigatorFragment) {
            navigator.submitPreferences(
                EpubPreferences(
                    fontFamily = when (settings.fontFamily) {
                        ReaderFontFamily.DEFAULT -> null
                        ReaderFontFamily.SERIF -> org.readium.r2.navigator.preferences.FontFamily.SERIF
                        ReaderFontFamily.SANS_SERIF -> org.readium.r2.navigator.preferences.FontFamily.SANS_SERIF
                        ReaderFontFamily.MONOSPACE -> org.readium.r2.navigator.preferences.FontFamily.MONOSPACE
                    },
                    fontSize = settings.fontScale.toDouble(),
                    lineHeight = settings.lineHeight.toDouble(),
                    pageMargins = settings.pageMargins.toDouble(),
                    paragraphSpacing = settings.paragraphSpacing.toDouble(),
                    publisherStyles = settings.publisherStyles,
                    scroll = settings.scrollMode,
                    theme = when (settings.theme) {
                        ReaderTheme.SYSTEM -> if (settings.appDark) Theme.DARK else Theme.LIGHT
                        ReaderTheme.LIGHT -> Theme.LIGHT
                        ReaderTheme.SEPIA -> Theme.SEPIA
                        ReaderTheme.DARK -> Theme.DARK
                    }
                )
            )
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
        private const val NAVIGATOR_TAG = "readium_navigator"

        fun newInstance(sessionId: Long): ReadiumHostFragment = ReadiumHostFragment().apply {
            arguments = Bundle().apply { putLong(ARG_SESSION_ID, sessionId) }
        }

        fun tag(sessionId: Long): String = "readium_host_$sessionId"
    }
}
