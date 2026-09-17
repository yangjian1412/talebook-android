package com.talebook.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.talebook.app.data.repository.AuthRepository
import com.talebook.app.data.repository.CaptchaImage
import com.talebook.app.data.repository.CaptchaRepository
import com.talebook.app.data.repository.CaptchaStatus
import com.talebook.app.data.repository.LoginResult
import com.talebook.app.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class LoginMode { PASSWORD, GUEST }

enum class CaptchaUiState { DISABLED, IMAGE, GEETEST, UNKNOWN, LOADING }

data class LoginUiState(
    val mode: LoginMode = LoginMode.PASSWORD,
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
    val captchaUi: CaptchaUiState = CaptchaUiState.DISABLED,
    val captchaScene: String = "login",
    val captchaImageBase64: String = "",
    val captchaCode: String = "",
    val showGeetestHint: Boolean = false,
    val pendingUnlock: Boolean = false,
    val unlockHint: String? = null
)

class LoginViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    private val authRepository = AuthRepository()
    private val captchaRepository = CaptchaRepository()

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private var welcomeCaptchaCode: String = ""
    private var loginCaptchaCode: String = ""
    private val sceneCaptchaNeeded: MutableMap<String, CaptchaStatus> = mutableMapOf()

    init {
        viewModelScope.launch {
            val server = settingsRepository.activeLibraryServer.first()
            _uiState.value = _uiState.value.copy(
                mode = if (server.loginMode == "guest") LoginMode.GUEST else LoginMode.PASSWORD,
                username = server.username,
                password = server.password
            )
            if (server.isPrivateMode && !server.siteAccessCode.isNullOrBlank()) {
                val status = captchaRepository.probe("welcome")
                sceneCaptchaNeeded["welcome"] = status
                showScene("welcome", status)
            } else {
                val status = captchaRepository.probe("login")
                sceneCaptchaNeeded["login"] = status
                showScene("login", status)
            }
        }
    }

    fun setMode(mode: LoginMode) {
        _uiState.value = _uiState.value.copy(mode = mode, error = null)
        if (mode == LoginMode.PASSWORD) {
            viewModelScope.launch {
                val status = captchaRepository.probe("login")
                sceneCaptchaNeeded["login"] = status
                showScene("login", status)
            }
        }
    }

    fun setUsername(username: String) {
        _uiState.value = _uiState.value.copy(username = username, error = null)
    }

    fun setPassword(password: String) {
        _uiState.value = _uiState.value.copy(password = password, error = null)
    }

    fun setCaptchaCode(code: String) {
        _uiState.value = _uiState.value.copy(captchaCode = code, error = null)
        when (_uiState.value.captchaScene) {
            "welcome" -> welcomeCaptchaCode = code
            else -> loginCaptchaCode = code
        }
    }

    fun dismissGeetestHint() {
        _uiState.value = _uiState.value.copy(showGeetestHint = false)
    }

    fun refreshCaptcha() {
        viewModelScope.launch {
            if (_uiState.value.captchaUi != CaptchaUiState.IMAGE) return@launch
            fetchCaptchaImage()
        }
    }

    private suspend fun showScene(scene: String, status: CaptchaStatus) {
        _uiState.value = _uiState.value.copy(
            captchaUi = CaptchaUiState.LOADING,
            captchaScene = scene,
            captchaImageBase64 = ""
        )
        when (status) {
            is CaptchaStatus.Disabled -> _uiState.value = _uiState.value.copy(
                captchaUi = CaptchaUiState.DISABLED,
                captchaImageBase64 = "",
                captchaCode = ""
            )
            is CaptchaStatus.Image -> {
                _uiState.value = _uiState.value.copy(captchaUi = CaptchaUiState.IMAGE)
                fetchCaptchaImage()
            }
            is CaptchaStatus.Geetest -> _uiState.value = _uiState.value.copy(
                captchaUi = CaptchaUiState.GEETEST,
                captchaImageBase64 = "",
                captchaCode = "",
                showGeetestHint = true
            )
            is CaptchaStatus.Unknown -> _uiState.value = _uiState.value.copy(
                captchaUi = CaptchaUiState.UNKNOWN,
                captchaImageBase64 = "",
                captchaCode = ""
            )
        }
    }

    private suspend fun fetchCaptchaImage() {
        val img: CaptchaImage? = captchaRepository.fetchImage()
        when (_uiState.value.captchaScene) {
            "welcome" -> welcomeCaptchaCode = ""
            else -> loginCaptchaCode = ""
        }
        _uiState.value = _uiState.value.copy(
            captchaImageBase64 = img?.imageBase64 ?: "",
            captchaCode = ""
        )
    }

    fun login() {
        val state = _uiState.value
        if (state.isLoading) return

        val captchaNeeded = state.captchaUi == CaptchaUiState.IMAGE
        val code = when (state.captchaScene) {
            "welcome" -> welcomeCaptchaCode
            else -> loginCaptchaCode
        }
        if (captchaNeeded && code.isBlank()) {
            _uiState.value = state.copy(error = "请先输入人机验证")
            return
        }

        _uiState.value = state.copy(isLoading = true, error = null, unlockHint = null)

        viewModelScope.launch {
            val server = settingsRepository.activeLibraryServer.first()
            val welcomeNeeded = server.isPrivateMode && !server.siteAccessCode.isNullOrBlank()
            val currentScene = state.captchaScene

            if (welcomeNeeded && currentScene == "welcome") {
                val unlockResult = authRepository.unlockSite(server.siteAccessCode, welcomeCaptchaCode)
                if (unlockResult is LoginResult.Failure) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = unlockResult.message
                    )
                    if (_uiState.value.captchaUi == CaptchaUiState.IMAGE) fetchCaptchaImage()
                    return@launch
                }
                val loginStatus = captchaRepository.probe("login")
                sceneCaptchaNeeded["login"] = loginStatus
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    unlockHint = "站点已解锁，请输入登录验证码"
                )
                showScene("login", loginStatus)
                return@launch
            }

            if (state.mode == LoginMode.PASSWORD) {
                if (state.username.isBlank() || state.password.isBlank()) {
                    _uiState.value = state.copy(isLoading = false, error = "请输入账号和密码")
                    return@launch
                }
            }

            if (welcomeNeeded && sceneCaptchaNeeded["welcome"] !is CaptchaStatus.Image && state.captchaScene != "welcome") {
                val wStatus = captchaRepository.probe("welcome")
                sceneCaptchaNeeded["welcome"] = wStatus
                if (wStatus is CaptchaStatus.Image) {
                    _uiState.value = _uiState.value.copy(isLoading = false, unlockHint = "请先完成站点解锁")
                    showScene("welcome", wStatus)
                    return@launch
                }
            }

            val loginCode = if (sceneCaptchaNeeded["login"] is CaptchaStatus.Image) loginCaptchaCode else ""
            val result = when (state.mode) {
                LoginMode.PASSWORD -> authRepository.loginWithPassword(state.username, state.password, loginCode)
                LoginMode.GUEST -> authRepository.loginWithPassword("", "", loginCode)
            }

            when (result) {
                is LoginResult.Success -> {
                    settingsRepository.saveLoginInfo(
                        mode = result.mode,
                        username = result.username,
                        nickname = result.nickname
                    )
                    settingsRepository.saveLoginSecret(
                        mode = result.mode,
                        username = result.username,
                        password = state.password,
                        accessCode = "",
                        nickname = result.nickname
                    )
                    _uiState.value = _uiState.value.copy(isLoading = false, success = true)
                }
                is LoginResult.Failure -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = result.message)
                    if (_uiState.value.captchaUi == CaptchaUiState.IMAGE) fetchCaptchaImage()
                }
            }
        }
    }
}