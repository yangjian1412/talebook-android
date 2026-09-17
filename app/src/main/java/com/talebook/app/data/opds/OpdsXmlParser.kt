package com.talebook.app.data.opds

import com.talebook.app.data.model.Book

data class OpdsEntry(
    val id: String,
    val title: String,
    val author: String? = null,
    val summary: String? = null,
    val updated: String? = null,
    val coverUrl: String? = null,
    val downloadUrl: String? = null,
    val downloadMime: String? = null,
    val downloadSize: Long? = null,
    val categories: List<String> = emptyList(),
    val navLink: String? = null
)

data class OpdsFeed(
    val title: String,
    val entries: List<OpdsEntry>,
    val nextLink: String? = null
)

object OpdsXmlParser {

    fun parseFeed(xml: String): OpdsFeed {
        val root = XmlLite.parse(xml)
        val feedTitle = root.firstByPath("title")?.text.orEmpty()
        val nextLink = root.allByPath("link")
            .firstOrNull { it.attr("rel") == "next" }
            ?.attr("href")
        val entries = root.allByPath("entry").map { entry ->
            val id = entry.firstByPath("id")?.text.orEmpty()
            val title = entry.firstByPath("title")?.text.orEmpty()
            val author = entry.allByPath("author").joinToString(", ") {
                it.firstByPath("name")?.text.orEmpty()
            }.takeIf { it.isNotBlank() }
            val summary = entry.firstByPath("summary")?.text
            val updated = entry.firstByPath("updated")?.text
            val links = entry.allByPath("link")
            val acquisition = links.firstOrNull {
                val rel = it.attr("rel").orEmpty()
                rel == "http://opds-spec.org/acquisition" ||
                        rel == "http://opds-spec.org/acquisition/open-access" ||
                        rel.startsWith("http://opds-spec.org/acquisition/")
            }
            val downloadUrl = acquisition?.attr("href")
            val downloadMime = acquisition?.attr("type")
            val coverLink = links.firstOrNull {
                val rel = it.attr("rel").orEmpty()
                rel == "http://opds-spec.org/cover" || rel == "http://opds-spec.org/thumbnail"
            }
            val coverUrl = coverLink?.attr("href")
            val categories = entry.allByPath("category").mapNotNull { it.attr("label") }
            val navLink = links.firstOrNull {
                it.attr("rel") == "subsection" || it.attr("type")?.contains("navigation") == true
            }?.attr("href")
            OpdsEntry(
                id = id,
                title = title,
                author = author,
                summary = summary,
                updated = updated,
                coverUrl = coverUrl,
                downloadUrl = downloadUrl,
                downloadMime = downloadMime,
                categories = categories,
                navLink = navLink
            )
        }
        return OpdsFeed(title = feedTitle, entries = entries, nextLink = nextLink)
    }

    fun entriesToBooks(entries: List<OpdsEntry>, baseUrl: String): List<Book> {
        return entries.map { e ->
            val resolvedCover = e.coverUrl?.let { resolveUrl(baseUrl, it) }
            val resolvedDownload = e.downloadUrl?.let { resolveUrl(baseUrl, it) }
            Book(
                title = e.title,
                author = e.author ?: "",
                cover = resolvedCover ?: "",
                tags = e.categories,
                downloadUrl = resolvedDownload,
                downloadMime = e.downloadMime,
                source = "opds"
            )
        }
    }

    private fun resolveUrl(base: String, href: String): String {
        if (href.startsWith("http://") || href.startsWith("https://")) return href
        val trimmedBase = base.trimEnd('/')
        return if (href.startsWith("/")) "$trimmedBase$href" else "$trimmedBase/$href"
    }
}