package com.talebook.app.reader

import android.content.Context
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
    override suspend fun stream(request: HttpRequest) = delegate.stream(
        request.copy {
            val cookie = RetrofitClient.cookieHeader()
            if (cookie.isNotBlank()) {
                setHeader("Cookie", cookie)
            }
        }
    )
}
