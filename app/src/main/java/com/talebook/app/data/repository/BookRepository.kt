package com.talebook.app.data.repository

import com.talebook.app.data.api.RetrofitClient
import com.talebook.app.data.model.*

class BookRepository {
    private val api get() = RetrofitClient.getApi()

    suspend fun getIndex(random: Int = 12, recent: Int = 12): Result<IndexData> {
        return try {
            val resp = api.getIndex(random, recent)
            if (resp.isSuccessful && resp.body()?.err == "ok") {
                val body = resp.body()!!
                Result.success(IndexData(
                    randomBooks = body.randomBooks ?: emptyList(),
                    newBooks = body.newBooks ?: emptyList()
                ))
            } else {
                Result.failure(Exception(resp.body()?.msg ?: "Unknown error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getLibrary(
        publisher: String? = null,
        author: String? = null,
        tag: String? = null
    ): Result<List<Book>> {
        return try {
            val resp = api.getLibrary(publisher = publisher, author = author, tag = tag)
            if (resp.isSuccessful && resp.body()?.err == "ok") {
                Result.success(resp.body()?.books ?: emptyList())
            } else {
                Result.failure(Exception(resp.body()?.msg ?: "Unknown error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun search(query: String): Result<List<Book>> {
        return try {
            val resp = api.search(query)
            if (resp.isSuccessful && resp.body()?.err == "ok") {
                Result.success(resp.body()?.books ?: emptyList())
            } else {
                Result.failure(Exception(resp.body()?.msg ?: "Unknown error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getRecent(): Result<List<Book>> {
        return try {
            val resp = api.getRecent()
            if (resp.isSuccessful && resp.body()?.err == "ok") {
                Result.success(resp.body()?.books ?: emptyList())
            } else {
                Result.failure(Exception(resp.body()?.msg ?: "Unknown error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getReading(): Result<List<Book>> {
        return try {
            val resp = api.getReadingBooks()
            if (resp.isSuccessful && resp.body()?.err == "ok") {
                Result.success(resp.body()?.books ?: emptyList())
            } else {
                Result.failure(Exception(resp.body()?.msg ?: "Unknown error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getShelf(): Result<List<Book>> {
        return try {
            val resp = api.getShelf()
            if (resp.isSuccessful && resp.body()?.err == "ok") {
                Result.success(resp.body()?.books ?: emptyList())
            } else {
                Result.failure(Exception(resp.body()?.msg ?: "Unknown error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleShelf(bookId: Int, inShelf: Boolean): Result<Unit> {
        return try {
            val resp = api.toggleShelf(bookId, com.talebook.app.data.model.ShelfToggleRequest(shelf = inShelf))
            if (resp.isSuccessful && resp.body()?.err == "ok") {
                Result.success(Unit)
            } else {
                Result.failure(Exception(resp.body()?.msg ?: "Unknown error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getHot(): Result<List<Book>> {
        return try {
            val resp = api.getHot()
            if (resp.isSuccessful && resp.body()?.err == "ok") {
                Result.success(resp.body()?.books ?: emptyList())
            } else {
                Result.failure(Exception(resp.body()?.msg ?: "Unknown error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getBookDetail(bookId: Int): Result<BookDetail> {
        return try {
            val resp = api.getBookDetail(bookId)
            if (resp.isSuccessful && resp.body()?.err == "ok") {
                val book = resp.body()?.book
                if (book != null) {
                    Result.success(book)
                } else {
                    Result.failure(Exception("Book not found"))
                }
            } else {
                Result.failure(Exception(resp.body()?.msg ?: "Unknown error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getBookNav(): Result<List<NavGroup>> {
        return try {
            val resp = api.getBookNav()
            if (resp.isSuccessful && resp.body()?.err == "ok") {
                Result.success(resp.body()?.navs ?: emptyList())
            } else {
                Result.failure(Exception(resp.body()?.msg ?: "Unknown error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCategoryList(meta: String): Result<List<CategoryItem>> {
        return try {
            val resp = api.getCategoryList(meta)
            if (resp.isSuccessful && resp.body()?.err == "ok") {
                Result.success(resp.body()?.items ?: emptyList())
            } else {
                Result.failure(Exception(resp.body()?.msg ?: "Unknown error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCategoryBooks(meta: String, name: String): Result<List<Book>> {
        return try {
            val resp = api.getCategoryBooks(meta, name)
            if (resp.isSuccessful && resp.body()?.err == "ok") {
                Result.success(resp.body()?.books ?: emptyList())
            } else {
                Result.failure(Exception(resp.body()?.msg ?: "Unknown error"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getReadState(bookId: Int): Result<ReadState> {
        return try {
            val resp = api.getReadState(bookId)
            if (resp.isSuccessful && resp.body()?.err == "ok") {
                val body = resp.body()
                val state = body?.book?.state
                if (state != null) {
                    Result.success(state)
                } else {
                    Result.success(ReadState())
                }
            } else {
                Result.success(ReadState())
            }
        } catch (e: Exception) {
            Result.success(ReadState())
        }
    }

}

data class IndexData(
    val randomBooks: List<Book>,
    val newBooks: List<Book>
)
