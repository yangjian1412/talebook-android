package com.talebook.app.data.server

enum class ServerType(val key: String, val displayName: String) {
    TALEEBOOK("talebook", "Talebook"),
    MYBOOKS("mybooks", "MyBooks"),
    OPDS("opds", "OPDS 通用");

    companion object {
        fun fromKey(key: String?): ServerType = when (key) {
            "mybooks" -> MYBOOKS
            "opds" -> OPDS
            else -> TALEEBOOK
        }
    }
}

data class ServerCapabilities(
    val supportsLogin: Boolean = true,
    val supportsGuest: Boolean = true,
    val supportsInviteCode: Boolean = false,
    val supportsCaptcha: Boolean = false,
    val supportsShelf: Boolean = false,
    val supportsFavorite: Boolean = false,
    val supportsReadState: Boolean = false,
    val supportsCategories: Boolean = true,
    val supportsSearch: Boolean = true,
    val supportsReading: Boolean = false,
    val supportsHot: Boolean = true,
    val supportsRecent: Boolean = true,
    val supportsIndex: Boolean = true,
    val requiresBasicAuth: Boolean = false
) {
    companion object {
        val TALEEBOOK = ServerCapabilities(
            supportsLogin = true,
            supportsGuest = true,
            supportsInviteCode = true,
            supportsCaptcha = true,
            supportsShelf = true,
            supportsFavorite = true,
            supportsReadState = true,
            supportsReading = true
        )
        val MYBOOKS = ServerCapabilities(
            supportsLogin = true,
            supportsGuest = true,
            supportsInviteCode = true,
            supportsCaptcha = false,
            supportsShelf = true,
            supportsFavorite = true,
            supportsReadState = true,
            supportsReading = true
        )
        val OPDS = ServerCapabilities(
            supportsLogin = false,
            supportsGuest = true,
            supportsInviteCode = false,
            supportsCaptcha = false,
            supportsShelf = false,
            supportsFavorite = false,
            supportsReadState = false,
            supportsCategories = true,
            supportsSearch = true,
            supportsReading = false,
            supportsHot = true,
            supportsRecent = true,
            supportsIndex = true,
            requiresBasicAuth = true
        )

        fun forType(type: ServerType): ServerCapabilities = when (type) {
            ServerType.TALEEBOOK -> TALEEBOOK
            ServerType.MYBOOKS -> MYBOOKS
            ServerType.OPDS -> OPDS
        }
    }
}