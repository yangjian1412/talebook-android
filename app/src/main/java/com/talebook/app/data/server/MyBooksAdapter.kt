package com.talebook.app.data.server

import com.talebook.app.data.api.RetrofitClient
import com.talebook.app.data.model.Book
import com.talebook.app.data.model.BookDetail
import com.talebook.app.data.model.CategoryItem
import com.talebook.app.data.model.MyBooksAccessRequest
import com.talebook.app.data.model.NavGroup
import com.talebook.app.data.model.ReadState
import com.talebook.app.data.model.WantsToggleRequest
import com.talebook.app.data.repository.GeetestParams
import com.talebook.app.data.repository.IndexData

class MyBooksAdapter : ServerAdapter {
    override val type = ServerType.MYBOOKS
    override val capabilities = ServerCapabilities.MYBOOKS

    private val api get() = RetrofitClient.getApi()

    override suspend fun loginWithPassword(
        username: String,
        password: String,
        captchaCode: String,
        geetest: GeetestParams?
    ): ServerLoginResult {
        val r = api.loginWithPassword(username, password, "")
        @Suppress("DEPRECATION")
        val httpCode = r.code()
        val body = r.body()
        return when {
            !r.isSuccessful -> ServerLoginResult.Failure("登录失败 (HTTP $httpCode)")
            body == null -> ServerLoginResult.Failure("登录失败")
            body.err == "ok" -> ServerLoginResult.Success(
                mode = "password", username = username,
                nickname = body.user?.nickname?.takeIf { it.isNotBlank() } ?: username
            )
            else -> ServerLoginResult.Failure(body.msg ?: "登录失败")
        }
    }

    override suspend fun loginGuest(captchaCode: String, geetest: GeetestParams?): ServerLoginResult {
        val r = api.loginWithPassword("", "", "")
        @Suppress("DEPRECATION")
        val httpCode = r.code()
        val body = r.body()
        return when {
            !r.isSuccessful -> ServerLoginResult.Failure("登录失败 (HTTP $httpCode)")
            body == null -> ServerLoginResult.Failure("登录失败")
            body.err == "ok" -> ServerLoginResult.Success(
                mode = "guest", username = "访客",
                nickname = body.user?.nickname?.takeIf { it.isNotBlank() } ?: "访客"
            )
            else -> ServerLoginResult.Failure(body.msg ?: "登录失败")
        }
    }

    override suspend fun unlockSite(
        inviteCode: String,
        captchaCode: String,
        geetest: GeetestParams?
    ): ServerLoginResult {
        val r = api.mybooksAccess(MyBooksAccessRequest(inviteCode))
        @Suppress("DEPRECATION")
        val httpCode = r.code()
        val body = r.body()
        return when {
            !r.isSuccessful -> ServerLoginResult.Failure("解锁失败 (HTTP $httpCode)")
            body == null -> ServerLoginResult.Failure("解锁失败")
            body.err == "ok" || body.err == "free" -> ServerLoginResult.Success(
                mode = "code", username = "访客",
                nickname = body.user?.nickname?.takeIf { it.isNotBlank() } ?: "访客"
            )
            else -> ServerLoginResult.Failure(body.msg ?: "解锁失败")
        }
    }

    override suspend fun getIndex(): Result<IndexData> = runCatching {
        val resp = api.getIndex()
        val b = resp.body()
        if (resp.isSuccessful && b?.err == "ok") IndexData(
            randomBooks = b.randomBooks ?: emptyList(),
            newBooks = b.newBooks ?: emptyList()
        ) else throw IllegalStateException(b?.msg ?: "")
    }

    override suspend fun getRecent(): Result<List<Book>> = runCatching {
        val resp = api.getRecent()
        val b = resp.body()
        if (resp.isSuccessful && b?.err == "ok") b.books ?: emptyList() else throw IllegalStateException(b?.msg ?: "")
    }

    override suspend fun getHot(): Result<List<Book>> = runCatching {
        val resp = api.getHot()
        val b = resp.body()
        if (resp.isSuccessful && b?.err == "ok") b.books ?: emptyList() else throw IllegalStateException(b?.msg ?: "")
    }

    override suspend fun getReading(): Result<List<Book>> = runCatching {
        val resp = api.getReadingBooks()
        val b = resp.body()
        if (resp.isSuccessful && b?.err == "ok") b.books ?: emptyList() else throw IllegalStateException(b?.msg ?: "")
    }

    override suspend fun getShelf(): Result<List<Book>> = runCatching {
        val resp = api.getWants()
        val b = resp.body()
        if (resp.isSuccessful && b?.err == "ok") b.books ?: emptyList() else throw IllegalStateException(b?.msg ?: "")
    }

    override suspend fun getBookDetail(id: Int): Result<BookDetail> = runCatching {
        val resp = api.getBookDetail(id)
        val b = resp.body()
        if (resp.isSuccessful && b?.err == "ok" && b?.book != null) b.book
        else throw IllegalStateException(b?.msg ?: "Book not found")
    }

    override suspend fun getBookNav(): Result<List<NavGroup>> = runCatching {
        val resp = api.getBookNav()
        val b = resp.body()
        if (resp.isSuccessful && b?.err == "ok") b.navs ?: emptyList() else throw IllegalStateException(b?.msg ?: "")
    }

    override suspend fun getCategoryList(meta: String): Result<List<CategoryItem>> = runCatching {
        val resp = api.getCategoryList(meta)
        val b = resp.body()
        if (resp.isSuccessful && b?.err == "ok") b.items ?: emptyList() else throw IllegalStateException(b?.msg ?: "")
    }

    override suspend fun getCategoryBooks(meta: String, name: String): Result<List<Book>> = runCatching {
        val resp = api.getCategoryBooks(meta, name)
        val b = resp.body()
        if (resp.isSuccessful && b?.err == "ok") b.books ?: emptyList() else throw IllegalStateException(b?.msg ?: "")
    }

    override suspend fun search(query: String): Result<List<Book>> = runCatching {
        val resp = api.search(query)
        val b = resp.body()
        if (resp.isSuccessful && b?.err == "ok") b.books ?: emptyList() else throw IllegalStateException(b?.msg ?: "")
    }

    override suspend fun toggleShelf(bookId: Int, inShelf: Boolean): Result<Unit> = runCatching {
        val resp = api.toggleWants(bookId, WantsToggleRequest(wants = inShelf))
        val b = resp.body()
        if (resp.isSuccessful && b?.err == "ok") Unit else throw IllegalStateException(b?.msg ?: "")
    }

    override suspend fun getReadState(bookId: Int): Result<ReadState> = runCatching {
        val resp = api.getReadState(bookId)
        val b = resp.body()
        val s = b?.book?.state
        ReadState(
            favorite = s?.favorite ?: 0,
            wants = s?.wants ?: 0,
            readState = s?.readState ?: 0
        )
    }

    override suspend fun signOut(): Result<Unit> = runCatching {
        api.signOut()
        RetrofitClient.clearCurrentHostCookies()
    }
}