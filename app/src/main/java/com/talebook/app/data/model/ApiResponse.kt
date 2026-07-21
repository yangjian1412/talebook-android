package com.talebook.app.data.model

import com.google.gson.annotations.SerializedName

data class ApiResponse<T>(
    @SerializedName("err") val err: String = "ok",
    @SerializedName("msg") val msg: String? = null,
    @SerializedName("total") val total: Int? = null,
    @SerializedName("books") val books: List<Book>? = null,
    @SerializedName("items") val items: List<CategoryItem>? = null,
    @SerializedName("random_books") val randomBooks: List<Book>? = null,
    @SerializedName("new_books") val newBooks: List<Book>? = null,
    @SerializedName("sys") val sys: SysInfo? = null,
    @SerializedName("user") val user: UserInfo? = null,
    @SerializedName("navs") val navs: List<NavGroup>? = null,
    @SerializedName("book") val book: BookDetail? = null,
    @SerializedName("data") val data: T? = null
)

data class SysInfo(
    @SerializedName("books") val books: Int = 0,
    @SerializedName("tags") val tags: Int = 0,
    @SerializedName("authors") val authors: Int = 0,
    @SerializedName("publishers") val publishers: Int = 0,
    @SerializedName("series") val series: Int = 0,
    @SerializedName("version") val version: String = "",
    @SerializedName("title") val title: String = ""
)

data class UserInfo(
    @SerializedName("is_login") val isLogin: Boolean = false,
    @SerializedName("is_admin") val isAdmin: Boolean = false,
    @SerializedName("nickname") val nickname: String = "",
    @SerializedName("username") val username: String = "",
    @SerializedName("avatar") val avatar: String = ""
)

data class CategoryItem(
    @SerializedName("id") val id: Int = 0,
    @SerializedName("name") val name: String = "",
    @SerializedName("count") val count: Int = 0
)

data class NavGroup(
    @SerializedName("legend") val legend: String = "",
    @SerializedName("tags") val tags: List<NavTag> = emptyList()
)

data class NavTag(
    @SerializedName("name") val name: String = "",
    @SerializedName("count") val count: Int = 0
)
