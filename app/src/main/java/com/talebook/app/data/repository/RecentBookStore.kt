package com.talebook.app.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.talebook.app.data.model.Book

object RecentBookStore {
    private const val PREFS = "recent_books"
    private const val KEY_BOOKS = "books"
    private const val MAX_SIZE = 30
    private val gson = Gson()

    fun add(context: Context, book: Book) {
        val books = get(context).toMutableList()
        books.removeAll { it.id == book.id }
        books.add(0, book)
        save(context, books.take(MAX_SIZE))
    }

    fun get(context: Context): List<Book> {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_BOOKS, "")
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val type = object : TypeToken<List<Book>>() {}.type
            gson.fromJson<List<Book>>(json, type)
        }.getOrDefault(emptyList())
    }

    private fun save(context: Context, books: List<Book>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BOOKS, gson.toJson(books))
            .apply()
    }
}
