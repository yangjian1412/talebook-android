package com.talebook.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import android.graphics.BitmapFactory
import android.util.Base64
import com.talebook.app.data.api.RetrofitClient
import com.talebook.app.data.repository.SettingsRepository
import com.talebook.app.data.server.ServerType
import com.talebook.app.ui.components.CaptchaImageView
import com.talebook.app.ui.components.UnlockSiteDialog
import com.talebook.app.viewmodel.CaptchaUiState
import com.talebook.app.viewmodel.LoginMode
import com.talebook.app.viewmodel.LoginUiState
import com.talebook.app.viewmodel.LoginViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    settingsRepository: SettingsRepository,
    onLoginSuccess: () -> Unit,
    onAnonymousEnter: () -> Unit = {},
    onSkipAuth: (() -> Unit)? = null,
    resumeBookId: Int? = null,
) {
    val factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LoginViewModel(settingsRepository) as T
        }
    }
    val viewModel: LoginViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsState()

    val serverUrl by settingsRepository.serverUrl.collectAsState(initial = "")
    val serverName by settingsRepository.serverName.collectAsState(initial = "")
    val serverPrivateMode by settingsRepository.serverPrivateMode.collectAsState(initial = false)
    val serverTypeStr by settingsRepository.activeServerType.collectAsState(initial = "talebook")
    val storedBasicUser by settingsRepository.activeHttpBasicUser.collectAsState(initial = "")
    val storedBasicPass by settingsRepository.activeHttpBasicPass.collectAsState(initial = "")
    var showServerConfig by remember { mutableStateOf(false) }
    var editUrl by remember(serverUrl) { mutableStateOf(serverUrl) }
    var editName by remember(serverName) { mutableStateOf(serverName) }
    var editPrivateMode by remember(serverPrivateMode) { mutableStateOf(serverPrivateMode) }
    var editServerType by remember(serverTypeStr) { mutableStateOf(serverTypeStr) }
    var editBasicUser by remember(storedBasicUser) { mutableStateOf(storedBasicUser) }
    var editBasicPass by remember(storedBasicPass) { mutableStateOf(storedBasicPass) }
    var urlSavedHint by remember { mutableStateOf(false) }
    var showServerCaptchaDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(showServerCaptchaDialog) {
        if (showServerCaptchaDialog) {
            try {
                RetrofitClient.updateBaseUrl(editUrl)
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(uiState.success) {
        if (uiState.success) {
            onLoginSuccess()
        }
    }

    if (uiState.showGeetestHint) {
        AlertDialog(
            onDismissRequest = viewModel::dismissGeetestHint,
            title = { Text("需要极验验证") },
            text = {
                Text(
                    "你的服务端启用了极验（GeeTest）人机验证。" +
                            "极验需要在浏览器中加载其 SDK 才能完成验证，" +
                            "App 暂不内置支持。请先用浏览器打开服务器登录页面完成账号验证，" +
                            "后续会话由 cookie 维持。"
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissGeetestHint) {
                    Text("我知道了")
                }
            }
        )
    }

    if (showServerCaptchaDialog) {
        UnlockSiteDialog(
            serverUrl = editUrl,
            onDismiss = {
                showServerCaptchaDialog = false
                showServerConfig = false
            },
            onSuccess = {
                showServerCaptchaDialog = false
                showServerConfig = false
            }
        )
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            Text(
                text = "Tale Book",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "TaleBook 安卓客户端",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 服务器地址（可折叠）
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("服务器", style = MaterialTheme.typography.labelSmall)
                            Text(
                                serverUrl,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { showServerConfig = !showServerConfig }) {
                            Icon(
                                if (showServerConfig) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null
                            )
                        }
                    }

                    if (showServerConfig) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = editName,
                            onValueChange = { editName = it },
                            label = { Text("服务器名称") },
                            placeholder = { Text("可选名称") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = editUrl,
                            onValueChange = { editUrl = it; urlSavedHint = false },
                            label = { Text("服务器地址") },
                            placeholder = { Text("https://你的服务器地址") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "服务端类型",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ServerType.values().forEach { type ->
                                FilterChip(
                                    selected = editServerType == type.key,
                                    onClick = { editServerType = type.key },
                                    label = { Text(type.displayName) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        if (editServerType == "opds") {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "OPDS 通用协议（HTTP Basic 认证）保存服务器设置后请在下方连接窗口填写用户名和密码",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("是否启用私人模式", style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "服务端开启了 INVITE_MODE 时需要先输入站点访问码解锁",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = editPrivateMode,
                                    onCheckedChange = { editPrivateMode = it }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                RetrofitClient.updateBaseUrl(editUrl)
                                scope.launch {
                                    val existingAccessCode = settingsRepository.serverSiteAccessCode.first()
                                    settingsRepository.saveServerUrl(editUrl)
                                    settingsRepository.saveServerName(editName)
                                    settingsRepository.saveServerPrivacy(editPrivateMode, existingAccessCode)
                                    settingsRepository.saveServerType(editServerType, "", "")
                                    urlSavedHint = true
                                    if (editServerType != "opds" && editPrivateMode) {
                                        showServerCaptchaDialog = true
                                    } else {
                                        showServerConfig = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("保存服务器设置")
                        }
                        if (urlSavedHint) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (editServerType == "opds")
                                    "已保存，OPDS 连接不需要登录"
                                else if (editPrivateMode)
                                    "已保存，请完成人机验证"
                                else
                                    "已保存",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (!showServerConfig) {
                if (editServerType == "opds") {
                    OpdsLoginForm(
                        serverUrl = editUrl,
                        basicUser = editBasicUser,
                        basicPass = editBasicPass,
                        onBasicUserChange = { editBasicUser = it },
                        onBasicPassChange = { editBasicPass = it },
                        onSuccess = onLoginSuccess,
                        settingsRepository = settingsRepository
                    )
                } else {
                    // Tab 切换
                    TabRow(
                        selectedTabIndex = when (uiState.mode) {
                            LoginMode.PASSWORD -> 0
                            LoginMode.GUEST -> 1
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Tab(
                            selected = uiState.mode == LoginMode.PASSWORD,
                            onClick = { viewModel.setMode(LoginMode.PASSWORD) },
                            text = { Text("账号密码") },
                            icon = { Icon(Icons.Default.Person, contentDescription = null) }
                        )
                        Tab(
                            selected = uiState.mode == LoginMode.GUEST,
                            onClick = { viewModel.setMode(LoginMode.GUEST) },
                            text = { Text("游客") },
                            icon = { Icon(Icons.Default.PersonOutline, contentDescription = null) }
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    when (uiState.mode) {
                        LoginMode.PASSWORD -> PasswordLoginForm(uiState, viewModel)
                        LoginMode.GUEST -> {
                            Text(
                                text = "无需账号密码，以访客身份浏览公共内容",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (uiState.error != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = uiState.error!!,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { viewModel.login() },
                enabled = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("登录中...")
                } else {
                    Text("登录")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 跳过验证直接进入
            OutlinedButton(
                onClick = { onSkipAuth?.invoke() ?: onAnonymousEnter() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PersonOutline, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("跳过验证直接进入（可稍后在设置中配置服务器与登录）")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun OpdsLoginForm(
    serverUrl: String,
    basicUser: String,
    basicPass: String,
    onBasicUserChange: (String) -> Unit,
    onBasicPassChange: (String) -> Unit,
    onSuccess: () -> Unit,
    settingsRepository: SettingsRepository
) {
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val repository = remember(serverUrl, basicUser, basicPass) {
        com.talebook.app.data.opds.OpdsRepository(serverUrl, basicUser, basicPass)
    }

    Text(
        text = "OPDS 通用协议",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "OPDS 服务端不支持账号密码登录，使用 HTTP Basic 认证。\n连接时使用上述用户名/密码访问服务端。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(16.dp))

    OutlinedTextField(
        value = basicUser,
        onValueChange = { onBasicUserChange(it); error = null },
        label = { Text("用户名") },
        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(12.dp))
    OutlinedTextField(
        value = basicPass,
        onValueChange = { onBasicPassChange(it); error = null },
        label = { Text("密码") },
        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth()
    )
    if (error != null) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = error ?: "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
    Spacer(modifier = Modifier.height(24.dp))
    Button(
        onClick = {
            if (basicUser.isBlank()) {
                error = "请输入用户名"
                return@Button
            }
            isLoading = true
            error = null
            scope.launch {
                val result = repository.testConnection()
                if (result.isSuccess) {
                    settingsRepository.saveServerType("opds", basicUser, basicPass)
                    isLoading = false
                    onSuccess()
                } else {
                    isLoading = false
                    error = "连接失败：${result.exceptionOrNull()?.message ?: "未知错误"}"
                }
            }
        },
        enabled = !isLoading,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("连接中...")
        } else {
            Text("连接")
        }
    }
}

@Composable
private fun PasswordLoginForm(
    uiState: LoginUiState,
    viewModel: LoginViewModel
) {
    var passwordVisible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = uiState.username,
        onValueChange = viewModel::setUsername,
        label = { Text("用户名") },
        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedTextField(
        value = uiState.password,
        onValueChange = viewModel::setPassword,
        label = { Text("密码") },
        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
        trailingIcon = {
            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                Icon(
                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = null
                )
            }
        },
        singleLine = true,
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth()
    )

    if (uiState.captchaUi == CaptchaUiState.IMAGE) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "人机验证",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CaptchaImageView(base64 = uiState.captchaImageBase64)
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = { viewModel.refreshCaptcha() }) {
                Icon(Icons.Default.Refresh, contentDescription = "刷新验证码")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = uiState.captchaCode,
            onValueChange = viewModel::setCaptchaCode,
            label = { Text("验证码") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            modifier = Modifier.fillMaxWidth()
        )
    } else if (uiState.captchaUi == CaptchaUiState.GEETEST || uiState.captchaUi == CaptchaUiState.UNKNOWN) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = if (uiState.captchaUi == CaptchaUiState.GEETEST)
                "服务端启用了极验验证，需先在 Web 端登录后再回到 App"
            else
                "服务端启用了人机验证，请按提示操作",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}
