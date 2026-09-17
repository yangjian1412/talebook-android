package com.talebook.app.data.repository

import com.talebook.app.data.api.RetrofitClient
import com.talebook.app.data.model.ApiResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.talebook.app.data.repository.SettingsRepository

sealed class LoginResult {
    data class Success(
        val mode: String,
        val username: String,
        val nickname: String
    ) : LoginResult()
    data class Failure(val message: String) : LoginResult()
}

class AuthRepository {
    private val api get() = RetrofitClient.getApi()

    suspend fun unlockSite(inviteCode: String): LoginResult = withContext(Dispatchers.IO) {
        try {
            val resp = api.loginWithCode(inviteCode)
            val body = resp.body()
            when {
                !resp.isSuccessful -> LoginResult.Failure("站点访问码校验失败 (HTTP ${resp.code()})")
                body == null -> LoginResult.Failure("站点访问码校验失败")
                body.err == "ok" || body.err == "free" -> LoginResult.Success(
                    mode = "code",
                    username = "访客",
                    nickname = body.user?.nickname?.takeIf { it.isNotBlank() } ?: "访客"
                )
                body.err == "captcha.invalid" -> LoginResult.Failure("服务端启用了人机验证，请先在 Web 端完成登录或联系管理员")
                else -> LoginResult.Failure(body.msg ?: "站点访问码错误 (${body.err})")
            }
        } catch (e: Exception) {
            LoginResult.Failure(e.message ?: "网络错误")
        }
    }

    suspend fun loginWithCode(code: String): LoginResult = withContext(Dispatchers.IO) {
        try {
            val resp = api.loginWithCode(code)
            parseLoginResponse(resp.code(), resp.body(), resp.errorBody()?.string(), mode = "code", username = "访客")
        } catch (e: Exception) {
            LoginResult.Failure(e.message ?: "网络错误")
        }
    }

    suspend fun loginWithPassword(username: String, password: String): LoginResult = withContext(Dispatchers.IO) {
        try {
            val resp = api.loginWithPassword(username.trim().lowercase(), password)
            parseLoginResponse(resp.code(), resp.body(), resp.errorBody()?.string(), mode = "password", username = username)
        } catch (e: Exception) {
            LoginResult.Failure(e.message ?: "网络错误")
        }
    }

    suspend fun signOut(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            api.signOut()
            RetrofitClient.clearCurrentHostCookies()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseLoginResponse(
        httpCode: Int,
        body: ApiResponse<Any>?,
        errorText: String?,
        mode: String,
        username: String
    ): LoginResult {
        if (httpCode != 200 || body == null) {
            val msg = body?.msg ?: errorText ?: "登录失败 (HTTP $httpCode)"
            return LoginResult.Failure(msg)
        }
        if (body.err != "ok") {
            return LoginResult.Failure(body.msg ?: body.err)
        }
        val nickname = body.user?.nickname?.takeIf { it.isNotBlank() } ?: username
        return LoginResult.Success(
            mode = mode,
            username = username,
            nickname = nickname
        )
    }
}
