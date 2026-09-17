package com.talebook.app.data.server

import com.talebook.app.data.model.Book
import com.talebook.app.data.model.BookDetail
import com.talebook.app.data.model.CategoryItem
import com.talebook.app.data.repository.IndexData
import com.talebook.app.data.model.NavGroup
import com.talebook.app.data.model.ReadState
import com.talebook.app.data.model.ShelfToggleRequest
import com.talebook.app.data.opds.OpdsBrowseResult
import com.talebook.app.data.opds.OpdsFeed
import com.talebook.app.data.opds.OpdsRepository
import com.talebook.app.data.repository.GeetestParams

class OpdsAdapter(
    baseUrl: String,
    basicUser: String,
    basicPass: String
) : ServerAdapter {
    override val type = ServerType.OPDS
    override val capabilities = ServerCapabilities.OPDS

    private val opds = OpdsRepository(baseUrl, basicUser, basicPass)

    override suspend fun loginWithPassword(
        username: String, password: String, captchaCode: String, geetest: GeetestParams?
    ): ServerLoginResult {
        return ServerLoginResult.BasicAuthOnly
    }

    override suspend fun loginGuest(captchaCode: String, geetest: GeetestParams?): ServerLoginResult {
        val r = opds.testConnection()
        return if (r.isSuccess) ServerLoginResult.Success("guest", "访客", "访客")
        else ServerLoginResult.Failure(r.exceptionOrNull()?.message ?: "连接失败")
    }

    override suspend fun unlockSite(
        inviteCode: String, captchaCode: String, geetest: GeetestParams?
    ): ServerLoginResult = ServerLoginResult.Failure("OPDS 服务端不支持站点解锁")

    override suspend fun getIndex(): Result<IndexData> = runCatching {
        opds.browse().getOrThrow().let {
            IndexData(randomBooks = it.books, newBooks = emptyList())
        }
    }

    override suspend fun getRecent(): Result<List<Book>> = runCatching {
        opds.browse().getOrThrow().books
    }

    override suspend fun getHot(): Result<List<Book>> = runCatching {
        opds.browse().getOrThrow().books
    }

    override suspend fun getReading(): Result<List<Book>> = Result.success(emptyList())

    override suspend fun getShelf(): Result<List<Book>> = Result.success(emptyList())

    override suspend fun getBookDetail(id: Int): Result<BookDetail> =
        Result.failure(UnsupportedOperationException("OPDS 详情请使用 browse/feed"))

    override suspend fun getBookNav(): Result<List<NavGroup>> = Result.success(emptyList())

    override suspend fun getCategoryList(meta: String): Result<List<CategoryItem>> =
        Result.success(emptyList())

    override suspend fun getCategoryBooks(meta: String, name: String): Result<List<Book>> =
        Result.success(emptyList())

    override suspend fun search(query: String): Result<List<Book>> =
        opds.search(query).map { it.books }

    override suspend fun toggleShelf(bookId: Int, inShelf: Boolean): Result<Unit> =
        Result.failure(UnsupportedOperationException("OPDS 不支持书架"))

    override suspend fun getReadState(bookId: Int): Result<ReadState> =
        Result.success(ReadState())

    override suspend fun signOut(): Result<Unit> = Result.success(Unit)
}