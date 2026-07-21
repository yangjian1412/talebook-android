package com.talebook.app.reader

import org.readium.adapter.pdfium.navigator.PdfiumEngineProvider
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.pdf.PdfNavigatorFactory
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

sealed interface ReadiumSession {
    val id: Long
    val bookId: Int
    val publication: Publication
    val initialLocator: Locator?
    val isRemote: Boolean
    val hasLargeEmbeddedFonts: Boolean
    var displaySettings: ReaderDisplaySettings
}

data class EpubReadiumSession(
    override val id: Long,
    override val bookId: Int,
    override val publication: Publication,
    override val initialLocator: Locator?,
    override val isRemote: Boolean,
    override val hasLargeEmbeddedFonts: Boolean,
    val navigatorFactory: EpubNavigatorFactory,
    override var displaySettings: ReaderDisplaySettings = ReaderDisplaySettings()
) : ReadiumSession

data class PdfReadiumSession(
    override val id: Long,
    override val bookId: Int,
    override val publication: Publication,
    override val initialLocator: Locator?,
    override val isRemote: Boolean,
    override val hasLargeEmbeddedFonts: Boolean,
    val navigatorFactory: PdfNavigatorFactory<*, *, *>,
    val pdfEngineProvider: PdfiumEngineProvider,
    override var displaySettings: ReaderDisplaySettings = ReaderDisplaySettings()
) : ReadiumSession

object ReadiumSessionStore {
    private val ids = AtomicLong(1)
    private val sessions = ConcurrentHashMap<Long, ReadiumSession>()

    fun put(session: ReadiumSession): Long {
        sessions[session.id] = session
        return session.id
    }

    fun nextId(): Long = ids.getAndIncrement()

    fun get(id: Long): ReadiumSession? = sessions[id]

    fun remove(id: Long) {
        sessions.remove(id)?.publication?.close()
    }
}
