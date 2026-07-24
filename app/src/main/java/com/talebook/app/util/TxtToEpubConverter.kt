package com.talebook.app.util

import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PushbackInputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.time.Instant
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class TxtConversionResult(
    val encoding: String,
    val chapterCount: Int,
    val contentCount: Int
)

object TxtToEpubConverter {

    const val CONVERTER_VERSION = 2
    private const val MAX_FILE_SIZE = 50 * 1024 * 1024
    private const val SAMPLE_SIZE = 64 * 1024
    private const val TARGET_CHUNK_CHARS = 120 * 1024

    private val chapterPatterns = listOf(
        Regex("^第[\\d一二三四五六七八九十百千万零〇两]+[章节回卷部集篇].*"),
        Regex("^(序章|楔子|引子|前言|正文|后记|尾声|番外)(\\s|$|[:：].*)"),
        Regex("^Chapter\\s+\\d+.*", RegexOption.IGNORE_CASE)
    )

    private data class ContentItem(
        val id: String,
        val href: String,
        val title: String,
        val inToc: Boolean
    )

    fun convert(txtFile: File, title: String, cacheDir: File): File {
        val epubFile = File(cacheDir, "${txtFile.name}.epub")
        txtFile.inputStream().use { input ->
            convertFromStream(input, title, epubFile, sourceSizeBytes = txtFile.length())
        }
        return epubFile
    }

    fun convertFromStream(
        input: InputStream,
        title: String,
        outputEpubFile: File,
        sourceSizeBytes: Long = -1L
    ): TxtConversionResult {
        require(outputEpubFile.parentFile?.exists() == true) {
            "输出目录不存在: ${outputEpubFile.parent}"
        }
        require(sourceSizeBytes <= 0L || sourceSizeBytes <= MAX_FILE_SIZE) {
            "文件过大，暂不支持超过 50MB 的 txt"
        }

        val prepared = prepareReader(input)
        val escapedTitle = escapeXml(title.ifBlank { "未命名 TXT" })
        val contentItems = mutableListOf<ContentItem>()
        var detectedChapterCount = 0

        createEpub(outputEpubFile) { zip ->
            BufferedReader(prepared.reader).use { reader ->
                var currentTitle = escapedTitle
                var currentChunk = StringBuilder()
                var chapterTocPending = true
                var hasChapters = false
                var fallbackPart = 1
                var contentIndex = 0

                fun writeChunk(forceToc: Boolean = false) {
                    if (currentChunk.isBlank()) return
                    val fallbackTitle = "第${fallbackPart++}部分"
                    val titleForItem = if (hasChapters) currentTitle else fallbackTitle
                    val inToc = if (hasChapters) chapterTocPending || forceToc else true
                    val href = "content_$contentIndex.xhtml"
                    val id = "content_$contentIndex"
                    writeZipEntry(zip, "OEBPS/$href", buildXhtmlChunk(titleForItem, currentChunk.toString()))
                    contentItems += ContentItem(id = id, href = href, title = titleForItem, inToc = inToc)
                    currentChunk = StringBuilder()
                    contentIndex += 1
                    if (hasChapters) chapterTocPending = false
                }

                reader.lineSequence().forEach { rawLine ->
                    val line = rawLine.trimEnd('\r')
                    val trimmed = line.trim()
                    if (isChapterTitle(trimmed)) {
                        writeChunk()
                        hasChapters = true
                        detectedChapterCount += 1
                        currentTitle = escapeXml(trimmed)
                        chapterTocPending = true
                        currentChunk.append("<h1>").append(currentTitle).append("</h1>\n")
                        return@forEach
                    }

                    if (trimmed.isBlank()) return@forEach
                    val paragraph = "<p>${escapeXml(line)}</p>\n"
                    if (currentChunk.length + paragraph.length > TARGET_CHUNK_CHARS) {
                        writeChunk()
                    }
                    currentChunk.append(paragraph)
                }

                writeChunk(forceToc = true)
                if (contentItems.isEmpty()) {
                    val href = "content_0.xhtml"
                    writeZipEntry(zip, "OEBPS/$href", buildXhtmlChunk(escapedTitle, "<p></p>\n"))
                    contentItems += ContentItem("content_0", href, escapedTitle, true)
                }
            }

            writeZipEntry(zip, "OEBPS/content.opf", buildOpf(contentItems, escapedTitle))
            writeZipEntry(zip, "OEBPS/nav.xhtml", buildNav(contentItems, escapedTitle))
        }

        return TxtConversionResult(
            encoding = prepared.charset.displayName(),
            chapterCount = detectedChapterCount,
            contentCount = contentItems.size
        )
    }

    private data class PreparedReader(val reader: InputStreamReader, val charset: Charset)

    private fun prepareReader(input: InputStream): PreparedReader {
        val pushback = PushbackInputStream(input, SAMPLE_SIZE + 4)
        val sample = ByteArray(SAMPLE_SIZE)
        val count = pushback.read(sample)
        val actualSample = if (count > 0) sample.copyOf(count) else ByteArray(0)
        val bomSize = bomSize(actualSample)
        val charset = detectCharset(actualSample)
        if (count > bomSize) {
            pushback.unread(actualSample, bomSize, count - bomSize)
        }
        return PreparedReader(InputStreamReader(pushback, charset), charset)
    }

    private fun bomSize(bytes: ByteArray): Int = when {
        bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() -> 3
        bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> 2
        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> 2
        else -> 0
    }

    private fun detectCharset(bytes: ByteArray): Charset {
        return when {
            bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() -> Charsets.UTF_8
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> Charset.forName("UTF-16BE")
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> Charset.forName("UTF-16LE")
            canDecode(bytes, Charsets.UTF_8) -> Charsets.UTF_8
            isCharsetAvailable("GB18030") -> Charset.forName("GB18030")
            isCharsetAvailable("GBK") -> Charset.forName("GBK")
            else -> Charsets.UTF_8
        }
    }

    private fun canDecode(bytes: ByteArray, charset: Charset): Boolean {
        return try {
            charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun isCharsetAvailable(name: String): Boolean = runCatching { Charset.forName(name) }.isSuccess

    private fun isChapterTitle(line: String): Boolean {
        if (line.length !in 2..80) return false
        return chapterPatterns.any { it.matches(line) }
    }

    private fun buildXhtmlChunk(title: String, body: String): String {
        return """<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml">
<head><title>$title</title><meta charset="utf-8"/><meta name="viewport" content="width=device-width, initial-scale=1"/></head>
<body>$body</body>
</html>"""
    }

    private fun buildOpf(items: List<ContentItem>, escapedTitle: String): String {
        val uuid = UUID.randomUUID().toString()
        val manifest = buildString {
            appendLine("""    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>""")
            for (item in items) {
                appendLine("""    <item id="${item.id}" href="${item.href}" media-type="application/xhtml+xml"/>""")
            }
        }
        val spine = buildString {
            for (item in items) {
                appendLine("""    <itemref idref="${item.id}"/>""")
            }
        }
        return """<?xml version="1.0" encoding="utf-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="book-id">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="book-id">urn:uuid:$uuid</dc:identifier>
    <dc:title>$escapedTitle</dc:title>
    <dc:language>zh-CN</dc:language>
    <meta property="dcterms:modified">${Instant.now().toString()}</meta>
  </metadata>
  <manifest>
$manifest  </manifest>
  <spine>
$spine  </spine>
</package>"""
    }

    private fun buildNav(items: List<ContentItem>, escapedTitle: String): String {
        val tocItems = items.filter { it.inToc }.ifEmpty { items.take(1) }
        val toc = buildString {
            for (item in tocItems) {
                appendLine("""      <li><a href="${item.href}">${item.title}</a></li>""")
            }
        }
        return """<?xml version="1.0" encoding="utf-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
<head><title>$escapedTitle</title><meta charset="utf-8"/><meta name="viewport" content="width=device-width, initial-scale=1"/></head>
<body>
  <nav epub:type="toc">
    <h1>$escapedTitle</h1>
    <ol>
$toc    </ol>
  </nav>
</body>
</html>"""
    }

    private fun buildContainer(): String {
        return """<?xml version="1.0" encoding="utf-8"?>
<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>"""
    }

    private fun createEpub(epubFile: File, writeContent: (ZipOutputStream) -> Unit) {
        val mimetypeBytes = "application/epub+zip".toByteArray(Charsets.UTF_8)
        val crc32 = CRC32()
        crc32.update(mimetypeBytes)

        ZipOutputStream(FileOutputStream(epubFile)).use { zip ->
            zip.putNextEntry(ZipEntry("mimetype").apply {
                method = ZipEntry.STORED
                crc = crc32.value
                size = mimetypeBytes.size.toLong()
                compressedSize = mimetypeBytes.size.toLong()
            })
            zip.write(mimetypeBytes)
            zip.closeEntry()

            writeZipEntry(zip, "META-INF/container.xml", buildContainer())
            writeContent(zip)
        }
    }

    private fun writeZipEntry(zip: ZipOutputStream, path: String, content: String) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
