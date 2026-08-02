package com.talebook.app.ui.screens

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
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.talebook.app.data.api.RetrofitClient
import com.talebook.app.data.repository.SettingsRepository
import com.talebook.app.viewmodel.LoginMode
import com.talebook.app.viewmodel.LoginUiState
import com.talebook.app.viewmodel.LoginViewModel
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

    val serverUrl by settingsRepository.serverUrl.collectAsState(initial = "https://book.liufenyi.xyz:9973")
    val serverName by settingsRepository.serverName.collectAsState(initial = "")
    var showServerConfig by remember { mutableStateOf(false) }
    var editUrl by remember(serverUrl) { mutableStateOf(serverUrl) }
    var editName by remember(serverName) { mutableStateOf(serverName) }
    var urlSavedHint by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(uiState.success) {
        if (uiState.success) {
            onLoginSuccess()
        }
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
                            placeholder = { Text("https://book.liufenyi.xyz:9973") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                RetrofitClient.updateBaseUrl(editUrl)
                                scope.launch {
                                    settingsRepository.saveServerUrl(editUrl)
                                    settingsRepository.saveServerName(editName)
                                    urlSavedHint = true
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
                                "已保存，下次启动生效",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Tab 切换
            TabRow(
                selectedTabIndex = when (uiState.mode) {
                    LoginMode.CODE -> 0
                    LoginMode.PASSWORD -> 1
                    LoginMode.GUEST -> 2
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = uiState.mode == LoginMode.CODE,
                    onClick = { viewModel.setMode(LoginMode.CODE) },
                    text = { Text("访问码") },
                    icon = { Icon(Icons.Default.VpnKey, contentDescription = null) }
                )
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
                LoginMode.CODE -> CodeLoginForm(uiState, viewModel)
                LoginMode.PASSWORD -> PasswordLoginForm(uiState, viewModel)
                LoginMode.GUEST -> {
                    Text(
                        text = "无需账号密码，以访客身份浏览公共内容",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
private fun CodeLoginForm(
    uiState: LoginUiState,
    viewModel: LoginViewModel
) {
    OutlinedTextField(
        value = uiState.code,
        onValueChange = viewModel::setCode,
        label = { Text("访问码") },
        placeholder = { Text("请输入服务器设置的访问码") },
        leadingIcon = { Icon(Icons.Default.VpnKey, contentDescription = null) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
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
}
