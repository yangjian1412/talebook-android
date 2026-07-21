package com.talebook.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.talebook.app.data.repository.AuthRepository
import com.talebook.app.data.repository.LoginResult
import com.talebook.app.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class LoginMode { CODE, PASSWORD }

data class LoginUiState(
    val mode: LoginMode = LoginMode.PASSWORD,
    val code: String = "",
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false
)

class LoginViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    private val authRepository = AuthRepository()

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun setMode(mode: LoginMode) {
        _uiState.value = _uiState.value.copy(mode = mode, error = null)
    }

    fun setCode(code: String) {
        _uiState.value = _uiState.value.copy(code = code, error = null)
    }

    fun setUsername(username: String) {
        _uiState.value = _uiState.value.copy(username = username, error = null)
    }

    fun setPassword(password: String) {
        _uiState.value = _uiState.value.copy(password = password, error = null)
    }

    fun login() {
        val state = _uiState.value
        if (state.isLoading) return

        _uiState.value = state.copy(isLoading = true, error = null)

        viewModelScope.launch {
            val result = when (state.mode) {
                LoginMode.CODE -> {
                    if (state.code.isBlank()) {
                        _uiState.value = _uiState.value.copy(isLoading = false, error = "请输入访问码")
                        return@launch
                    }
                    authRepository.loginWithCode(state.code)
                }
                LoginMode.PASSWORD -> {
                    if (state.username.isBlank() || state.password.isBlank()) {
                        _uiState.value = _uiState.value.copy(isLoading = false, error = "请输入账号和密码")
                        return@launch
                    }
                    authRepository.loginWithPassword(state.username, state.password)
                }
            }

            when (result) {
                is LoginResult.Success -> {
                    settingsRepository.saveLoginInfo(
                        mode = result.mode,
                        username = result.username,
                        nickname = result.nickname
                    )
                    _uiState.value = _uiState.value.copy(isLoading = false, success = true)
                }
                is LoginResult.Failure -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = result.message)
                }
            }
        }
    }
}
