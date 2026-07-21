package com.talebook.app.reader

import android.content.Context
import android.util.Log
import com.talebook.app.data.api.RetrofitClient
import org.readium.adapter.pdfium.document.PdfiumDocumentFactory
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.http.HttpClient
import org.readium.r2.shared.util.http.HttpRequest
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

class ReadiumEngine(context: Context) {
    val httpClient: HttpClient = TalebookReadiumHttpClient()
    val assetRetriever = AssetRetriever(context.contentResolver, httpClient)
    val publicationOpener = PublicationOpener(
        publicationParser = DefaultPublicationParser(
            context = context.applicationContext,
            httpClient = httpClient,
            assetRetriever = assetRetriever,
            pdfFactory = PdfiumDocumentFactory(context.applicationContext)
        )
    )
}

private class TalebookReadiumHttpClient(
    private val delegate: HttpClient = DefaultHttpClient()
) : HttpClient {
    override suspend fun stream(request: HttpRequest): org.readium.r2.shared.util.Try<org.readium.r2.shared.util.http.HttpStreamResponse, org.readium.r2.shared.util.http.HttpError> {
        val originalRange = request.headers["Range"]?.firstOrNull()
            ?: request.headers["range"]?.firstOrNull()
            .orEmpty()
        val adjustedRequest = request.copy {
            val cookie = RetrofitClient.cookieHeader()
            if (cookie.isNotBlank()) {
                setHeader("Cookie", cookie)
            }
        }
        return delegate.stream(adjustedRequest).also { result ->
        val range = adjustedRequest.headers["Range"]?.joinToString() ?: adjustedRequest.headers["range"]?.joinToString().orEmpty()
        val multiRange = range.substringAfter("bytes=", "").contains(',')
        result.onSuccess { stream ->
            val response = stream.response
            Log.d(
                "TaleReadiumHttp",
                "url=${adjustedRequest.url} range=$range originalRange=$originalRange multiRange=$multiRange status=${response.statusCode} " +
                    "contentRange=${response.header("Content-Range").orEmpty()} " +
                    "length=${response.contentLength ?: -1} type=${response.mediaType}"
            )
        }.onFailure { error ->
            Log.w("TaleReadiumHttp", "url=${adjustedRequest.url} range=$range originalRange=$originalRange multiRange=$multiRange error=${error.message}")
        }
    }
    }
}
