package com.talebook.app.reader

import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import java.net.URLDecoder

object LocatorProgress {
    fun normalizeHref(href: String): String {
        var h = href.substringBefore('#').substringBefore('?')
        h = runCatching { URLDecoder.decode(h, "UTF-8") }.getOrDefault(h)
        h = h.replace('\\', '/')
        while (h.startsWith("./")) h = h.substring(2)
        return h.trimStart('/').trimEnd('/')
    }

    fun resourceStem(href: String): String {
        val file = normalizeHref(href).substringAfterLast('/')
        val base = file.substringBeforeLast('.', file)
        return base.replace(Regex("_split_\\d+$"), "")
    }

    fun hrefMatches(a: String, b: String): Boolean {
        val na = normalizeHref(a)
        val nb = normalizeHref(b)
        if (na.isEmpty() || nb.isEmpty()) return false
        if (na == nb) return true
        if (na.endsWith(nb) || nb.endsWith(na)) return true
        val sa = resourceStem(a)
        val sb = resourceStem(b)
        if (sa.isNotEmpty() && sa == sb) {
            val dirA = normalizeHref(a).substringBeforeLast('/', "")
            val dirB = normalizeHref(b).substringBeforeLast('/', "")
            if (dirA == dirB || dirA.endsWith(dirB) || dirB.endsWith(dirA)) return true
            if (dirA.isEmpty() || dirB.isEmpty()) return true
        }
        return false
    }

    fun estimateTotalProgression(publication: Publication, locator: Locator): Double? =
        estimateTotalProgression(publication.readingOrder, locator)

    fun estimateTotalProgression(readingOrder: List<Link>, locator: Locator): Double? {
        if (readingOrder.isEmpty()) return null
        val href = locator.href.toString()
        val index = readingOrder.indexOfFirst { hrefMatches(it.href.toString(), href) }
        if (index < 0) return null
        val within = locator.locations.progression ?: 0.0
        val estimate = (index + within.coerceIn(0.0, 1.0)) / readingOrder.size
        return estimate.coerceIn(0.0, 1.0)
    }

    fun estimateTotalForLink(readingOrder: List<Link>, link: Link): Double? {
        if (readingOrder.isEmpty()) return null
        val index = readingOrder.indexOfFirst { hrefMatches(it.href.toString(), link.href.toString()) }
        if (index < 0) return null
        return (index.toDouble() / readingOrder.size).coerceIn(0.0, 1.0)
    }

    fun isMissingTotal(locator: Locator): Boolean =
        locator.locations.totalProgression == null ||
            locator.locations.totalProgression?.let { it <= 0.0 } == true

    fun looksBareLocatorJson(json: String): Boolean {
        if (json.isBlank()) return true
        return !json.contains("totalProgression")
    }

    fun withTotalProgression(locator: Locator, total: Double): Locator =
        locator.copy(locations = locator.locations.copy(totalProgression = total.coerceIn(0.0, 1.0)))

    /**
     * 给 locator 补上「在这一章的第几页」，算法就是 Readium 兜底公式的反函数。
     *
     * 外排（reflowable）的 EPUB 里，Readium 上报的定位只有 `locations.cssSelector`，
     * 而它恢复位置时不看 cssSelector：要么用 URL 片段里的 id（`document.getElementById()`），
     * 要么退回 `R2EpubPageFragment` 的 `item = (progression * numPages).roundToInt()`。
     * 我们知道第几页，补上去以后那条兜底就会落在同一页，而不是章节第一页。
     */
    fun withResourceProgression(locator: Locator, pageIndex: Int, totalPages: Int): Locator {
        if (totalPages <= 1) return locator
        val progression = (pageIndex.toDouble() / totalPages.toDouble()).coerceIn(0.0, 1.0)
        return locator.copy(locations = locator.locations.copy(progression = progression))
    }

    /** 除了「在哪一章」以外还知不知道在这章的哪儿：段内进度，或者 URL 片段里的元素 id。 */
    fun hasPositionInResource(locator: Locator): Boolean {
        locator.locations.progression?.let { if (it > 0.0) return true }
        // Readium 恢复位置只认两条（见 `R2EpubPageFragment.loadLocator`）：
        // `locations.htmlId` → `document.getElementById()`，那条 id 是从 URL 片段（`#id`）里取的，
        // 跟 `cssSelector` 没关系；再就是按段内 `progression` 折算成第几页。
        if (locator.locations.fragments.any { it.startsWith("#") && it.length > 1 && !it.contains("=") }) return true
        return false
    }
}
