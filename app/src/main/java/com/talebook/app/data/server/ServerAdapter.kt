package com.talebook.app.data.server

import com.talebook.app.data.model.Book
import com.talebook.app.data.model.BookDetail
import com.talebook.app.data.model.CategoryItem
import com.talebook.app.data.repository.IndexData
import com.talebook.app.data.model.NavGroup
import com.talebook.app.data.model.ReadState

data class BookPage(
    val books: List<Book>,
    val total: Int? = null,
    val nextUrl: String? = null
)

sealed class ServerLoginResult {
    data class Success(
        val mode: String,
        val username: String,
        val nickname: String
    ) : ServerLoginResult()
    data class Failure(val message: String) : ServerLoginResult()
    object BasicAuthOnly : ServerLoginResult()
}

interface ServerAdapter {
    val type: ServerType
    val capabilities: ServerCapabilities

    suspend fun loginWithPassword(
        username: String,
        password: String,
        captchaCode: String = "",
        geetest: com.talebook.app.data.repository.GeetestParams? = null
    ): ServerLoginResult

    suspend fun loginGuest(
        captchaCode: String = "",
        geetest: com.talebook.app.data.repository.GeetestParams? = null
    ): ServerLoginResult

    suspend fun unlockSite(
        inviteCode: String,
        captchaCode: String = "",
        geetest: com.talebook.app.data.repository.GeetestParams? = null
    ): ServerLoginResult

    suspend fun getIndex(): Result<IndexData>
    suspend fun getRecent(): Result<List<Book>>
    suspend fun getHot(): Result<List<Book>>
    suspend fun getReading(): Result<List<Book>>
    suspend fun getShelf(): Result<List<Book>>
    suspend fun getBookDetail(id: Int): Result<BookDetail>
    suspend fun getBookNav(): Result<List<NavGroup>>
    suspend fun getCategoryList(meta: String): Result<List<CategoryItem>>
    suspend fun getCategoryBooks(meta: String, name: String): Result<List<Book>>
    suspend fun search(query: String): Result<List<Book>>
    suspend fun toggleShelf(bookId: Int, inShelf: Boolean): Result<Unit>
    suspend fun getReadState(bookId: Int): Result<ReadState>
    suspend fun signOut(): Result<Unit>
}