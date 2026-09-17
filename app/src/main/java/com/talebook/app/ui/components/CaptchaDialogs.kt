package com.talebook.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import android.graphics.BitmapFactory
import android.util.Base64
import com.talebook.app.data.api.RetrofitClient
import com.talebook.app.data.api.TalebookApi
import com.talebook.app.data.model.ApiResponse
import com.talebook.app.data.repository.CaptchaImage
import com.talebook.app.data.repository.CaptchaRepository
import com.talebook.app.data.repository.CaptchaStatus
import kotlinx.coroutines.launch

@Composable
fun CaptchaImageView(base64: String) {
    val bitmap = remember(base64) {
        if (base64.isBlank()) null
        else runCatching {
            val raw = if (base64.contains(",")) base64.substringAfter(",") else base64
            val bytes = Base64.decode(raw, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = "验证码图片",
            modifier = Modifier.height(48.dp).width(140.dp)
        )
    } else {
        Box(
            modifier = Modifier.height(48.dp).width(140.dp),
            contentAlignment = Alignment.Center
        ) {
            if (base64.isBlank()) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text(
                    "加载失败",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun UnlockSiteDialog(
    serverUrl: String,
    onDismiss: () -> Unit,
    onSuccess: (inviteCode: String) -> Unit
) {
    var inviteCode by remember { mutableStateOf("") }
    var captchaCode by remember { mutableStateOf("") }
    var captchaImage by remember { mutableStateOf("") }
    var captchaProbe by remember { mutableStateOf<CaptchaStatus?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val repository = remember { CaptchaRepository() }

    LaunchedEffect(Unit) {
        RetrofitClient.updateBaseUrl(serverUrl)
        val status = repository.probe("welcome")
        captchaProbe = status
        if (status is CaptchaStatus.Image) {
            val img: CaptchaImage? = repository.fetchImage()
            captchaImage = img?.imageBase64 ?: ""
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text("解锁站点") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = inviteCode,
                    onValueChange = { inviteCode = it; error = null },
                    label = { Text("私人模式访问码") },
                    placeholder = { Text("服务端「管理 → 系统设置 → 邀请/访问码」配置") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                val probe = captchaProbe
                when {
                    probe is CaptchaStatus.Image -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CaptchaImageView(base64 = captchaImage)
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = {
                                scope.launch {
                                    val img: CaptchaImage? = repository.fetchImage()
                                    captchaImage = img?.imageBase64 ?: ""
                                    captchaCode = ""
                                }
                            }, enabled = !isLoading) {
                                Icon(Icons.Default.Refresh, contentDescription = "刷新验证码")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = captchaCode,
                            onValueChange = { captchaCode = it; error = null },
                            label = { Text("验证码") },
                            singleLine = true,
                            isError = error != null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    probe is CaptchaStatus.Geetest || probe is CaptchaStatus.Unknown -> {
                        Text(
                            "服务端启用了极验或未知的人机验证，请先在 Web 端完成首次登录以建立 cookie。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    else -> {
                        Text(
                            "请输入服务端配置的私人模式访问码。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                error?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (inviteCode.isBlank()) {
                        error = "请输入私人模式访问码"
                        return@TextButton
                    }
                    val needsCaptcha = captchaProbe is CaptchaStatus.Image
                    if (needsCaptcha && captchaCode.isBlank()) {
                        error = "请输入人机验证"
                        return@TextButton
                    }
                    isLoading = true
                    error = null
                    scope.launch {
                        try {
                            RetrofitClient.updateBaseUrl(serverUrl)
                            val api: TalebookApi = RetrofitClient.getApi()
                            val code = if (needsCaptcha) captchaCode else ""
                            val resp = api.loginWithCode(inviteCode, code)
                            val body: ApiResponse<Any>? = resp.body()
                            when {
                                !resp.isSuccessful -> {
                                    error = "网络错误 (HTTP ${resp.code()})"
                                    refreshIfNeeded(repository, needsCaptcha) { img, c ->
                                        captchaImage = img; captchaCode = c
                                    }
                                }
                                body == null -> {
                                    error = "服务端返回空"
                                    refreshIfNeeded(repository, needsCaptcha) { img, c ->
                                        captchaImage = img; captchaCode = c
                                    }
                                }
                                body.err == "ok" || body.err == "free" -> {
                                    isLoading = false
                                    onSuccess(inviteCode)
                                    return@launch
                                }
                                body.err == "captcha.invalid" -> {
                                    error = "人机验证失败：${body.msg ?: "请重新输入"}"
                                    refreshIfNeeded(repository, true) { img, c ->
                                        captchaImage = img; captchaCode = c
                                    }
                                }
                                else -> {
                                    error = body.msg ?: "解锁失败 (${body.err})"
                                    refreshIfNeeded(repository, needsCaptcha) { img, c ->
                                        captchaImage = img; captchaCode = c
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            error = e.message ?: "网络错误"
                        }
                        isLoading = false
                    }
                },
                enabled = !isLoading
            ) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text("解锁")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) { Text("取消") }
        }
    )
}

private suspend fun refreshIfNeeded(
    repository: CaptchaRepository,
    needsCaptcha: Boolean,
    setter: (String, String) -> Unit
) {
    if (needsCaptcha) {
        val img: CaptchaImage? = repository.fetchImage()
        setter(img?.imageBase64 ?: "", "")
    }
}

@Composable
fun LoginCaptchaDialog(
    serverUrl: String,
    username: String,
    password: String,
    isGuest: Boolean,
    onDismiss: () -> Unit,
    onSuccess: (mode: String, username: String, nickname: String) -> Unit
) {
    var captchaCode by remember { mutableStateOf("") }
    var captchaImage by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val repository = remember { CaptchaRepository() }

    LaunchedEffect(Unit) {
        RetrofitClient.updateBaseUrl(serverUrl)
        val img: CaptchaImage? = repository.fetchImage()
        captchaImage = img?.imageBase64 ?: ""
    }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text("登录人机验证") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CaptchaImageView(base64 = captchaImage)
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = {
                        scope.launch {
                            val img: CaptchaImage? = repository.fetchImage()
                            captchaImage = img?.imageBase64 ?: ""
                            captchaCode = ""
                        }
                    }, enabled = !isLoading) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新验证码")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = captchaCode,
                    onValueChange = { captchaCode = it; error = null },
                    label = { Text("验证码") },
                    singleLine = true,
                    isError = error != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (captchaCode.isBlank()) {
                        error = "请输入人机验证"
                        return@TextButton
                    }
                    isLoading = true
                    error = null
                    scope.launch {
                        try {
                            RetrofitClient.updateBaseUrl(serverUrl)
                            val api: TalebookApi = RetrofitClient.getApi()
                            val resp = if (isGuest) {
                                api.loginWithPassword("", "", captchaCode)
                            } else {
                                api.loginWithPassword(username, password, captchaCode)
                            }
                            val body: ApiResponse<Any>? = resp.body()
                            when {
                                !resp.isSuccessful -> {
                                    error = "网络错误 (HTTP ${resp.code()})"
                                    val img: CaptchaImage? = repository.fetchImage()
                                    captchaImage = img?.imageBase64 ?: ""
                                    captchaCode = ""
                                }
                                body == null -> {
                                    error = "服务端返回空"
                                    val img: CaptchaImage? = repository.fetchImage()
                                    captchaImage = img?.imageBase64 ?: ""
                                    captchaCode = ""
                                }
                                body.err == "ok" -> {
                                    val mode = if (isGuest) "guest" else "password"
                                    val u = if (isGuest) "访客" else username
                                    val nick = body.user?.nickname?.takeIf { it.isNotBlank() } ?: u
                                    isLoading = false
                                    onSuccess(mode, u, nick)
                                    return@launch
                                }
                                body.err == "captcha.invalid" -> {
                                    error = "人机验证失败：${body.msg ?: "请重新输入"}"
                                    val img: CaptchaImage? = repository.fetchImage()
                                    captchaImage = img?.imageBase64 ?: ""
                                    captchaCode = ""
                                }
                                else -> {
                                    error = body.msg ?: "登录失败 (${body.err})"
                                    val img: CaptchaImage? = repository.fetchImage()
                                    captchaImage = img?.imageBase64 ?: ""
                                    captchaCode = ""
                                }
                            }
                        } catch (e: Exception) {
                            error = e.message ?: "网络错误"
                        }
                        isLoading = false
                    }
                },
                enabled = !isLoading
            ) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text("登录")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) { Text("取消") }
        }
    )
}