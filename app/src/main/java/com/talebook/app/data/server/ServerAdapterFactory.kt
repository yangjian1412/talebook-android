package com.talebook.app.data.server

import com.talebook.app.data.repository.LibraryServerConfig

object ServerAdapterFactory {
    fun create(config: LibraryServerConfig): ServerAdapter {
        val type = ServerType.fromKey(config.serverType)
        return when (type) {
            ServerType.TALEEBOOK -> TalebookAdapter()
            ServerType.MYBOOKS -> MyBooksAdapter()
            ServerType.OPDS -> OpdsAdapter(
                baseUrl = config.baseUrl,
                basicUser = config.httpBasicUser.orEmpty(),
                basicPass = config.httpBasicPass.orEmpty()
            )
        }
    }
}