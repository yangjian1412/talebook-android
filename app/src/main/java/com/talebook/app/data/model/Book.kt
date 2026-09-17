package com.talebook.app.data.model

import com.google.gson.annotations.SerializedName

data class Book(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("title") val title: String = "",
    @SerializedName("author_sort") val authorSort: String = "",
    @SerializedName("authors") val authors: List<String> = emptyList(),
    @SerializedName("publisher") val publisher: String = "",
    @SerializedName("isbn") val isbn: String? = "",
    @SerializedName("tags") val tags: List<String> = emptyList(),
    @SerializedName("rating") val rating: Double = 0.0,
    @SerializedName("series") val series: String? = "",
    @SerializedName("series_index") val seriesIndex: String? = "",
    @SerializedName("comments") val comments: String = "",
    @SerializedName("pubdate") val pubdate: String = "",
    @SerializedName("cover") val cover: String = "",
    @SerializedName("img") val img: String = "",
    @SerializedName("thumb") val thumb: String = "",
    @SerializedName("fmt_epub") val fmtEpub: String = "",
    @SerializedName("fmt_pdf") val fmtPdf: String = "",
    @SerializedName("fmt_azw3") val fmtAzw3: String = "",
    @SerializedName("fmt_mobi") val fmtMobi: String = "",
    @SerializedName("available_formats") val availableFormats: String = "",
    @SerializedName("count_visit") val countVisit: Int = 0,
    @SerializedName("count_download") val countDownload: Int = 0,
    @SerializedName("scope") val scope: String = "public",
    @SerializedName("timestamp") val timestamp: String = "",
    @SerializedName("author") val author: String = "",
    @SerializedName("download_url") val downloadUrl: String? = null,
    @SerializedName("download_mime") val downloadMime: String? = null,
    @SerializedName("source") val source: String = ""
)

data class BookDetail(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("title") val title: String = "",
    @SerializedName("author_sort") val authorSort: String = "",
    @SerializedName("authors") val authors: List<String> = emptyList(),
    @SerializedName("publisher") val publisher: String = "",
    @SerializedName("isbn") val isbn: String? = "",
    @SerializedName("tags") val tags: List<String> = emptyList(),
    @SerializedName("rating") val rating: Double = 0.0,
    @SerializedName("series") val series: String? = "",
    @SerializedName("series_index") val seriesIndex: String? = "",
    @SerializedName("comments") val comments: String = "",
    @SerializedName("pubdate") val pubdate: String = "",
    @SerializedName("cover") val cover: String = "",
    @SerializedName("img") val img: String = "",
    @SerializedName("thumb") val thumb: String = "",
    @SerializedName("fmt_epub") val fmtEpub: String = "",
    @SerializedName("fmt_pdf") val fmtPdf: String = "",
    @SerializedName("fmt_azw3") val fmtAzw3: String = "",
    @SerializedName("fmt_mobi") val fmtMobi: String = "",
    @SerializedName("available_formats") val availableFormats: String = "",
    @SerializedName("files") val files: List<BookFile> = emptyList(),
    @SerializedName("language") val language: String = "",
    @SerializedName("count_visit") val countVisit: Int = 0,
    @SerializedName("count_download") val countDownload: Int = 0,
    @SerializedName("scope") val scope: String = "public",
    @SerializedName("timestamp") val timestamp: String = "",
    @SerializedName("collector") val collector: String = "",
    @SerializedName("state") val state: ReadState? = null
)

data class BookFile(
    @SerializedName("format") val format: String = "",
    @SerializedName("size") val size: Long = 0,
    @SerializedName("href") val href: String = ""
)

fun Book.coverPath(): String = cover.ifBlank { img.ifBlank { thumb } }

fun BookDetail.coverPath(): String = cover.ifBlank { img.ifBlank { thumb } }

data class ReadState(
    @SerializedName("page") val page: Int = 0,
    @SerializedName("percentage") val percentage: Double = 0.0,
    @SerializedName("updated") val updated: String = "",
    @SerializedName("favorite") val favorite: Boolean = false,
    @SerializedName("shelf") val shelf: Boolean = false,
    @SerializedName("wants") val wants: Boolean = false,
    @SerializedName("read_state") val readState: Int = 0
)

/**
 * 加入/移出书架请求体：服务端 `/api/book/{id}/shelf` 用 json_decode 解析，
 * 必须发 JSON：`{"shelf": true}` / `{"shelf": false}`。
 */
data class ShelfToggleRequest(
    @SerializedName("shelf") val shelf: Boolean
)
