package com.talebook.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.talebook.app.data.repository.AuthRepository
import com.talebook.app.data.repository.LoginResult
import com.talebook.app.data.repository.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

sealed class SplashNavigation {
    object GoHome : SplashNavigation()
    object GoLogin : SplashNavigation()
}

class SplashViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    private val _navigation = MutableSharedFlow<SplashNavigation>()
    val navigation: SharedFlow<SplashNavigation> = _navigation.asSharedFlow()
    private var verifyJob: Job? = null

    fun verify() {
        verifyJob?.cancel()
        verifyJob = viewModelScope.launch {
            val result = withTimeoutOrNull(5000) {
                runCatching {
                    val skipAuth = settingsRepository.skipAuth.first()
                    if (skipAuth) return@runCatching SplashNavigation.GoHome
                    val server = settingsRepository.activeLibraryServer.first()
                    if (server.loginMode.isBlank()) return@runCatching SplashNavigation.GoLogin
                    val authRepo = AuthRepository()
                    if (server.isPrivateMode && !server.siteAccessCode.isNullOrBlank()) {
                        val unlockResult = authRepo.unlockSite(server.siteAccessCode)
                        if (unlockResult !is LoginResult.Success) return@runCatching SplashNavigation.GoLogin
                    }
                    val loginResult = when (server.loginMode) {
                        "guest" -> authRepo.loginWithPassword("", "")
                        else -> if (server.username.isNotBlank() && server.password.isNotBlank())
                            authRepo.loginWithPassword(server.username, server.password) else null
                    }
                    if (loginResult is LoginResult.Success) SplashNavigation.GoHome
                    else SplashNavigation.GoLogin
                }.getOrNull()
            }
            _navigation.emit(result ?: SplashNavigation.GoLogin)
        }
    }

    override fun onCleared() {
        verifyJob?.cancel()
    }
}