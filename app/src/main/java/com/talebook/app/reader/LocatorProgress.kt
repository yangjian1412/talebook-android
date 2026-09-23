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
}
