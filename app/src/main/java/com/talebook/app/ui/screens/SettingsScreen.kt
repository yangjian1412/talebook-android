package com.talebook.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.talebook.app.data.api.RetrofitClient
import com.talebook.app.data.repository.AuthRepository
import com.talebook.app.data.repository.CaptchaRepository
import com.talebook.app.data.repository.CaptchaStatus
import com.talebook.app.data.repository.LibraryServerConfig
import com.talebook.app.data.repository.LoginResult
import com.talebook.app.data.repository.ReaderBackupRepository
import com.talebook.app.data.repository.ReaderCacheRepository
import com.talebook.app.data.repository.SettingsRepository
import com.talebook.app.data.server.ServerType
import com.talebook.app.reader.tts.EdgeSpeech
import com.talebook.app.reader.tts.TtsAudioPlayer
import com.talebook.app.reader.tts.TtsHttp
import com.talebook.app.reader.tts.TtsListener
import com.talebook.app.reader.tts.TtsPresets
import com.talebook.app.reader.tts.TtsRequestFormat
import com.talebook.app.reader.tts.TtsService
import com.talebook.app.reader.tts.TtsSpeechOptions
import com.talebook.app.reader.tts.TtsVoice
import com.talebook.app.ui.components.LoginCaptchaDialog
import com.talebook.app.ui.components.UnlockSiteDialog
import com.talebook.app.ui.theme.AppAccentPalette
import com.talebook.app.ui.theme.ThemePresets
import com.talebook.app.ui.theme.toColor
import com.talebook.app.ui.screens.CompactSwitch
import com.talebook.app.viewmodel.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    contentPadding: PaddingValues = PaddingValues(),
    showBackButton: Boolean = true,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onOpenCacheList: () -> Unit = {},
    onOpenNotesManagement: () -> Unit = {},
    onOpenLocalLibrary: () -> Unit = {}
) {
    val nickname by settingsRepository.nickname.collectAsState(initial = "")
    val loginMode by settingsRepository.loginMode.collectAsState(initial = "")
    val themeMode by settingsRepository.themeMode.collectAsState(initial = SettingsRepository.THEME_AUTO)
    val appAccent by settingsRepository.appAccent.collectAsState(initial = ThemePresets.accents.first().id)
    val startTab by settingsRepository.startTab.collectAsState(initial = SettingsRepository.START_TAB_RECENT)
    val servers by settingsRepository.libraryServers.collectAsState(initial = emptyList())
    val activeServerId by settingsRepository.activeLibraryServerId.collectAsState(initial = SettingsRepository.DEFAULT_SERVER_ID)
    val skipAuth by settingsRepository.skipAuth.collectAsState(initial = false)
    val homeTabs by settingsRepository.homeTabs.collectAsState(initial = SettingsRepository.HOME_TABS_BOTH)
    val autoRefreshHomeOnEnter by settingsRepository.readerAutoRefreshHomeOnEnter.collectAsState(initial = false)
    val showTabLabel by settingsRepository.showTabLabel.collectAsState(initial = true)
    val cacheLimitMb by settingsRepository.readerCacheLimitMb.collectAsState(initial = SettingsRepository.DEFAULT_CACHE_LIMIT_MB)
    val autoCacheOnWifi by settingsRepository.readerAutoCacheOnWifi.collectAsState(initial = false)
    val context = LocalContext.current
    val appVersionName = remember(context) { installedVersionName(context) }
    var showChangelog by remember { mutableStateOf(false) }
    var editCacheLimit by remember(cacheLimitMb) { mutableStateOf(cacheLimitMb.toString()) }
    var cacheMessage by remember { mutableStateOf<String?>(null) }
    var cacheSizeText by remember { mutableStateOf("未统计") }
    var backupMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var editingServer by remember { mutableStateOf<LibraryServerConfig?>(null) }
    var showServerDialog by remember { mutableStateOf(false) }
    var pendingUnlock by remember { mutableStateOf<UnlockPendingState?>(null) }
    var pendingLogin by remember { mutableStateOf<LoginPendingState?>(null) }
    var serverLoginError by remember { mutableStateOf<String?>(null) }
    val authRepository = remember { AuthRepository() }
    val readerCacheRepository = remember { ReaderCacheRepository() }
    val readerBackupRepository = remember { ReaderBackupRepository() }
    val captchaRepository = remember { CaptchaRepository() }
    val pickBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                readerBackupRepository.importFromUri(context.applicationContext, uri).fold(
                    onSuccess = { msg ->
                        backupMessage = msg
                        HomeViewModel.clearCache(context.applicationContext)
                    },
                    onFailure = { e -> backupMessage = e.message ?: "导入失败" }
                )
            }
        }
    }

    fun handleServerSave(server: LibraryServerConfig) {
        scope.launch {
            RetrofitClient.updateBaseUrl(server.baseUrl)
            if (server.isPrivateMode) {
                val welcomeStatus = captchaRepository.probe("welcome")
                if (welcomeStatus !is CaptchaStatus.Disabled) {
                    pendingUnlock = UnlockPendingState(server = server, url = server.baseUrl)
                    return@launch
                }
            }
            val savedId = settingsRepository.upsertLibraryServer(server)
            settingsRepository.setActiveLibraryServer(savedId)
            val saved = server.copy(id = savedId)
            val loginStatus = captchaRepository.probe("login")
            if (loginStatus is CaptchaStatus.Image) {
                pendingLogin = LoginPendingState(
                    server = saved,
                    url = server.baseUrl,
                    username = if (server.loginMode == "password") server.username else "",
                    password = if (server.loginMode == "password") server.password else "",
                    isGuest = server.loginMode == "guest"
                )
                return@launch
            }
            val result = when (saved.loginMode) {
                "password" -> authRepository.loginWithPassword(saved.username, saved.password)
                "guest" -> authRepository.loginWithPassword("", "")
                else -> LoginResult.Failure("unknown mode")
            }
            when (result) {
                is LoginResult.Success -> {
                    settingsRepository.saveLoginInfo(result.mode, result.username, result.nickname)
                    settingsRepository.saveLoginSecret(
                        result.mode, result.username,
                        if (saved.loginMode == "guest") "" else saved.password,
                        "", result.nickname
                    )
                    editingServer = null
                    showServerDialog = false
                }
                is LoginResult.Failure -> {
                    serverLoginError = result.message
                }
            }
        }
    }

    LaunchedEffect(homeTabs) {
        val valid = when (homeTabs) {
            SettingsRepository.HOME_TABS_LOCAL -> startTab == SettingsRepository.START_TAB_RECENT || startTab == SettingsRepository.START_TAB_LOCAL || startTab == SettingsRepository.START_TAB_SETTINGS
            SettingsRepository.HOME_TABS_LIBRARY -> startTab == SettingsRepository.START_TAB_RECENT || startTab == SettingsRepository.START_TAB_LIBRARY || startTab == SettingsRepository.START_TAB_SETTINGS
            else -> true
        }
        if (!valid) {
            scope.launch { settingsRepository.saveStartTab(SettingsRepository.START_TAB_RECENT) }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("确认登出？") },
            text = { Text("登出后将清除当前会话，需要重新登录才能访问书籍。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        scope.launch {
                            authRepository.signOut()
                            settingsRepository.clearLogin()
                            onLogout()
                        }
                    }
                ) {
                    Text("登出", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("导入阅读数据备份") },
            text = { Text("请选择之前导出的 JSON 备份文件（通常在 Download/talebook/）。已有阅读进度可能被覆盖，书签和笔记会追加导入。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showImportDialog = false
                        pickBackupLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*"))
                    }
                ) { Text("选择文件") }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) { Text("取消") }
            }
        )
    }

    val activeServer by settingsRepository.activeLibraryServer.collectAsState(initial = null)

    if (showServerDialog) {
        ServerEditDialog(
            server = editingServer,
            defaultName = activeServer?.name.orEmpty(),
            onDismiss = { showServerDialog = false; editingServer = null },
            onSave = { server -> handleServerSave(server) }
        )
    }

    pendingUnlock?.let { state ->
        UnlockSiteDialog(
            serverUrl = state.url,
            onDismiss = {
                pendingUnlock = null
                editingServer = null
                showServerDialog = false
            },
            onSuccess = { inviteCode ->
                scope.launch {
                    val savedId = settingsRepository.upsertLibraryServer(
                        state.server.copy(siteAccessCode = inviteCode)
                    )
                    settingsRepository.setActiveLibraryServer(savedId)
                    val updatedServer = state.server.copy(id = savedId, siteAccessCode = inviteCode)
                    pendingUnlock = null
                    val loginStatus = captchaRepository.probe("login")
                    if (loginStatus is CaptchaStatus.Image) {
                        pendingLogin = LoginPendingState(
                            server = updatedServer,
                            url = state.url,
                            username = if (updatedServer.loginMode == "password") updatedServer.username else "",
                            password = if (updatedServer.loginMode == "password") updatedServer.password else "",
                            isGuest = updatedServer.loginMode == "guest"
                        )
                    } else {
                        val result = when (updatedServer.loginMode) {
                            "password" -> authRepository.loginWithPassword(updatedServer.username, updatedServer.password)
                            "guest" -> authRepository.loginWithPassword("", "")
                            else -> LoginResult.Failure("unknown mode")
                        }
                        when (result) {
                            is LoginResult.Success -> {
                                settingsRepository.saveLoginInfo(result.mode, result.username, result.nickname)
                                settingsRepository.saveLoginSecret(
                                    result.mode, result.username,
                                    if (updatedServer.loginMode == "guest") "" else updatedServer.password,
                                    "", result.nickname
                                )
                                editingServer = null
                                showServerDialog = false
                            }
                            is LoginResult.Failure -> {
                                serverLoginError = result.message
                            }
                        }
                    }
                }
            }
        )
    }

    pendingLogin?.let { state ->
        LoginCaptchaDialog(
            serverUrl = state.url,
            username = state.username,
            password = state.password,
            isGuest = state.isGuest,
            onDismiss = {
                pendingLogin = null
                editingServer = null
                showServerDialog = false
            },
            onSuccess = { mode, username, nickname ->
                scope.launch {
                    settingsRepository.saveLoginInfo(mode, username, nickname)
                    settingsRepository.saveLoginSecret(
                        mode,
                        username,
                        if (state.isGuest) "" else state.password,
                        "",
                        nickname
                    )
                    pendingLogin = null
                    editingServer = null
                    showServerDialog = false
                }
            }
        )
    }

    serverLoginError?.let { msg ->
        AlertDialog(
            onDismissRequest = { serverLoginError = null },
            title = { Text("登录失败") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { serverLoginError = null }) { Text("关闭") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(bottom = contentPadding.calculateBottomPadding())
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = "书库与账号",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = if (loginMode.isBlank()) "当前未登录" else "当前：${nickname.ifBlank { "访客" }} · ${if (loginMode == "guest") "访客登录" else "账号密码登录"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            servers.forEach { server ->
                ElevatedCard(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(server.name.ifBlank { server.baseUrl }, style = MaterialTheme.typography.titleSmall)
                                Text(server.baseUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = if (server.loginMode.isBlank()) "未登录" else "${server.nickname.ifBlank { server.username.ifBlank { "已登录" } }} · ${if (server.loginMode == "guest") "访客" else "账号密码"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (server.id == activeServerId) {
                                Text("当前", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { scope.launch { settingsRepository.setActiveLibraryServer(server.id) } },
                                enabled = server.id != activeServerId,
                                modifier = Modifier.weight(1f)
                            ) { Text("切换") }
                            OutlinedButton(
                                onClick = { editingServer = server; showServerDialog = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("编辑")
                            }
                            if (server.id != SettingsRepository.DEFAULT_SERVER_ID) {
                                OutlinedButton(
                                    onClick = { scope.launch { settingsRepository.deleteLibraryServer(server.id) } },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("删除")
                                }
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { editingServer = null; showServerDialog = true },
                    modifier = Modifier.weight(1f)
                ) { Text("新增书库") }
                OutlinedButton(
                    onClick = { showLogoutDialog = true },
                    enabled = loginMode.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Logout, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("登出当前")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "外观",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("软件主题色", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            AccentPicker(
                accents = ThemePresets.accents,
                selected = appAccent,
                onSelect = { accent -> scope.launch { settingsRepository.saveAppAccent(accent) } }
            )
            Spacer(modifier = Modifier.height(16.dp))
            Column(modifier = Modifier.selectableGroup()) {
                ThemeOptionRow(
                    selected = themeMode == SettingsRepository.THEME_LIGHT,
                    icon = Icons.Default.LightMode,
                    label = "白天",
                    onSelect = { scope.launch { settingsRepository.saveThemeMode(SettingsRepository.THEME_LIGHT) } }
                )
                ThemeOptionRow(
                    selected = themeMode == SettingsRepository.THEME_DARK,
                    icon = Icons.Default.DarkMode,
                    label = "夜间",
                    onSelect = { scope.launch { settingsRepository.saveThemeMode(SettingsRepository.THEME_DARK) } }
                )
                ThemeOptionRow(
                    selected = themeMode == SettingsRepository.THEME_AUTO,
                    icon = Icons.Default.Brightness6,
                    label = "自动（跟随系统）",
                    onSelect = { scope.launch { settingsRepository.saveThemeMode(SettingsRepository.THEME_AUTO) } }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "主页与标签",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "显示哪些标签",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(4.dp))
            Column(modifier = Modifier.selectableGroup()) {
                ReaderOptionRow(
                    selected = homeTabs == SettingsRepository.HOME_TABS_BOTH,
                    icon = Icons.Default.MenuBook,
                    label = "书库 + 本地",
                    description = "同时显示书库与本地书架 Tab",
                    onSelect = {
                        scope.launch { settingsRepository.saveHomeTabs(SettingsRepository.HOME_TABS_BOTH) }
                    }
                )
                ReaderOptionRow(
                    selected = homeTabs == SettingsRepository.HOME_TABS_LIBRARY,
                    icon = Icons.Default.Public,
                    label = "只显示书库",
                    description = "隐藏本地书架 Tab",
                    onSelect = {
                        scope.launch { settingsRepository.saveHomeTabs(SettingsRepository.HOME_TABS_LIBRARY) }
                    }
                )
                ReaderOptionRow(
                    selected = homeTabs == SettingsRepository.HOME_TABS_LOCAL,
                    icon = Icons.Default.Folder,
                    label = "只显示本地",
                    description = "隐藏书库 Tab",
                    onSelect = {
                        scope.launch { settingsRepository.saveHomeTabs(SettingsRepository.HOME_TABS_LOCAL) }
                    }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "主页是哪个",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(4.dp))
            Column(modifier = Modifier.selectableGroup()) {
                ThemeOptionRow(
                    selected = startTab == SettingsRepository.START_TAB_RECENT,
                    icon = Icons.Default.MenuBook,
                    label = "最近阅读",
                    onSelect = { scope.launch { settingsRepository.saveStartTab(SettingsRepository.START_TAB_RECENT) } }
                )
                if (homeTabs != SettingsRepository.HOME_TABS_LOCAL) {
                    ThemeOptionRow(
                        selected = startTab == SettingsRepository.START_TAB_LIBRARY,
                        icon = Icons.Default.Public,
                        label = "书库",
                        onSelect = { scope.launch { settingsRepository.saveStartTab(SettingsRepository.START_TAB_LIBRARY) } }
                    )
                }
                if (homeTabs != SettingsRepository.HOME_TABS_LIBRARY) {
                    ThemeOptionRow(
                        selected = startTab == SettingsRepository.START_TAB_LOCAL,
                        icon = Icons.Default.Folder,
                        label = "本地书架",
                        onSelect = { scope.launch { settingsRepository.saveStartTab(SettingsRepository.START_TAB_LOCAL) } }
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("底部标签显示中文")
                CompactSwitch(
                    checked = showTabLabel,
                    onCheckedChange = { value ->
                        scope.launch { settingsRepository.saveShowTabLabel(value) }
                    }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("进入书库自动更新")
                CompactSwitch(
                    checked = autoRefreshHomeOnEnter,
                    onCheckedChange = { value ->
                        scope.launch { settingsRepository.saveReaderAutoRefreshHomeOnEnter(value) }
                    }
                )
            }
            Text(
                text = "开启后进入书库主页会立即向服务端拉新数据；关闭时仅显示缓存，需要手动刷新。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            TtsSettingsSection(settingsRepository)

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "阅读数据",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onOpenNotesManagement,
                modifier = Modifier.fillMaxWidth()
            ) { Text("查看和导出笔记") }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    scope.launch {
                        readerBackupRepository.exportToDownloads(context.applicationContext).fold(
                            onSuccess = { path -> backupMessage = "已导出到 $path" },
                            onFailure = { e -> backupMessage = e.message ?: "导出失败" }
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("导出阅读记录 / 书签 / 笔记") }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showImportDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text("从文件导入阅读数据备份") }
            backupMessage?.let { msg ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "缓存管理",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = editCacheLimit,
                onValueChange = { editCacheLimit = it.filter { ch -> ch.isDigit() }.take(5) },
                label = { Text("本地阅读缓存上限 (MB)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text("当前缓存占用：$cacheSizeText", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            settingsRepository.saveReaderCacheLimitMb(editCacheLimit.toIntOrNull() ?: SettingsRepository.DEFAULT_CACHE_LIMIT_MB)
                            cacheMessage = "缓存上限已保存"
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("保存上限") }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val removed = readerCacheRepository.clearAll(context.applicationContext)
                            cacheMessage = "已清理 ${String.format(java.util.Locale.US, "%.1f", removed / 1024.0 / 1024.0)} MB"
                            cacheSizeText = "0.0 MB"
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("一键清理") }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val size = readerCacheRepository.totalSizeBytes(context.applicationContext)
                        cacheSizeText = "${String.format(java.util.Locale.US, "%.1f", size / 1024.0 / 1024.0)} MB"
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("统计缓存占用") }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onOpenCacheList,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.MenuBook, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("查看已缓存")
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Wi-Fi 下自动缓存", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "打开本地阅读器时，如果当前是 Wi-Fi，会后台缓存本书",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoCacheOnWifi,
                    onCheckedChange = { enabled ->
                        scope.launch { settingsRepository.saveReaderAutoCacheOnWifi(enabled) }
                    },
                    modifier = Modifier.height(32.dp).scale(0.82f)
                )
            }
            cacheMessage?.let { msg ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "关于",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (appVersionName.isBlank()) "Tale Book" else "Tale Book v$appVersionName",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "TaleBook 安卓客户端",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/yangjian1412/talebook-android")
                        )
                        context.startActivity(intent)
                    }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Public,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "GitHub 主页",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "github.com/yangjian1412/talebook-android",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    Icons.Default.OpenInNew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showChangelog = true }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "查看完整更新日志",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "点击查看各版本详细改动",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showChangelog) {
        ChangelogDialog(onDismiss = { showChangelog = false })
    }
}

/**
 * 这台机器上装的版本号。
 *
 * 读安装包自己的 versionName，不写在代码里，省得每次发版都要记得改这一处。
 */
private fun installedVersionName(context: Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull().orEmpty()

@Composable
private fun ReaderOptionRow(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    description: String,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onSelect,
                role = Role.RadioButton
            )
            .padding(vertical = 8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(icon, contentDescription = null)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ThemeOptionRow(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onSelect,
                role = Role.RadioButton
            )
            .padding(vertical = 8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(icon, contentDescription = null)
        Spacer(modifier = Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * 「朗读」一节：选哪一套朗读，配几套在线服务，选用其中一套。
 *
 * 手机内置不用配任何东西；在线的可以配好几套（自建服务、MiMo、Edge 各来一套都行），
 * 配的时候可以读模型、读音色、先试听，确认能用再去读书，不然读一半才发现连不上很难看。
 */
@Composable
private fun TtsSettingsSection(settingsRepository: SettingsRepository) {
    val scope = rememberCoroutineScope()
    val services by settingsRepository.ttsServices.collectAsState(initial = emptyList())
    val activeServiceId by settingsRepository.ttsActiveServiceId.collectAsState(initial = TtsService.BUILT_IN_ID)
    val wifiOnly by settingsRepository.ttsWifiOnly.collectAsState(initial = true)
    var editingService by remember { mutableStateOf<TtsService?>(null) }
    var deletingService by remember { mutableStateOf<TtsService?>(null) }

    Text(text = "朗读", style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(8.dp))
    Text(text = "用哪一套朗读", style = MaterialTheme.typography.titleSmall)
    Spacer(modifier = Modifier.height(4.dp))
    Column(modifier = Modifier.selectableGroup()) {
        ReaderOptionRow(
            selected = activeServiceId == TtsService.BUILT_IN_ID,
            icon = Icons.Default.PhoneAndroid,
            label = TtsService.BUILT_IN_LABEL,
            description = TtsRequestFormat.BUILT_IN.description,
            onSelect = { scope.launch { settingsRepository.setTtsActiveService(TtsService.BUILT_IN_ID) } }
        )
        services.forEach { service ->
            TtsServiceRow(
                service = service,
                selected = service.id == activeServiceId,
                onSelect = { scope.launch { settingsRepository.setTtsActiveService(service.id) } },
                onEdit = { editingService = service },
                onAskDelete = { deletingService = service }
            )
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedButton(
        onClick = { editingService = TtsService(id = "", format = TtsRequestFormat.OPENAI_SPEECH) },
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Add, contentDescription = null)
        Spacer(modifier = Modifier.width(6.dp))
        Text("添加朗读服务")
    }
    Spacer(modifier = Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("只在 Wi-Fi 下用在线服务", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "开着时用移动数据会自动改回${TtsService.BUILT_IN_LABEL}的；关掉则移动数据也走在线服务。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        CompactSwitch(
            checked = wifiOnly,
            onCheckedChange = { value -> scope.launch { settingsRepository.setTtsWifiOnly(value) } }
        )
    }

    deletingService?.let { doomed ->
        AlertDialog(
            onDismissRequest = { deletingService = null },
            title = { Text("删掉这套朗读服务？") },
            text = { Text("要删掉「${doomed.displayName}」。正在用它的会改回${TtsService.BUILT_IN_LABEL}的。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = doomed.id
                        deletingService = null
                        scope.launch { settingsRepository.deleteTtsService(target) }
                    }
                ) {
                    Text("删掉", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingService = null }) { Text("取消") }
            }
        )
    }

    editingService?.let { service ->
        TtsServiceEditDialog(
            service = service,
            onDismiss = { editingService = null },
            onSaved = { draft ->
                val isNewService = service.id.isBlank()
                scope.launch {
                    if (draft.isEdge) {
                        // Edge 不用配地址和 Key，能改的只有用哪个音色。
                        settingsRepository.setTtsServiceVoice(TtsService.EDGE_ID, draft.voice)
                    } else {
                        val saved = settingsRepository.saveTtsService(draft)
                        if (isNewService) settingsRepository.setTtsActiveService(saved.id)
                    }
                    editingService = null
                }
            }
        )
    }
}

/** 列表里的一条：选它、改它（Edge 只能改音色）、删它（现成的那两套删不掉）。 */
@Composable
private fun TtsServiceRow(
    service: TtsService,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onAskDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onSelect,
                role = Role.RadioButton
            )
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = if (service.isEdge) Icons.Default.Public else Icons.Default.Cloud,
            contentDescription = null
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(service.displayName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = service.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
            Icon(
                if (service.isEdge) Icons.Default.Tune else Icons.Default.Edit,
                contentDescription = if (service.isEdge) "选音色" else "编辑"
            )
        }
        if (!service.isFixed) {
            IconButton(onClick = onAskDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "删除")
            }
        }
    }
}

/**
 * 配一套朗读服务：名字、走哪种接口、地址和口令、模型、音色，外加试听。
 *
 * Edge 那一套不用配地址和 Key，只让它挑音色。
 * 模型和音色都可以从服务读出来再选，也可以自己填；服务不给列表时用预置的那几个。
 */
@Composable
private fun TtsServiceEditDialog(
    service: TtsService,
    onDismiss: () -> Unit,
    onSaved: (TtsService) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(service.name) }
    var format by remember { mutableStateOf(service.format) }
    var baseUrl by remember { mutableStateOf(service.baseUrl) }
    var apiKey by remember { mutableStateOf(service.apiKey) }
    var model by remember { mutableStateOf(service.model) }
    var voice by remember { mutableStateOf(service.voice) }
    var showApiKey by remember { mutableStateOf(false) }
    var modelOptions by remember { mutableStateOf(presetModelOptions(service.format)) }
    var voiceOptions by remember { mutableStateOf(presetVoiceOptions(service.format)) }
    var modelMenuExpanded by remember { mutableStateOf(false) }
    var voiceMenuExpanded by remember { mutableStateOf(false) }
    var fetching by remember { mutableStateOf(false) }
    var previewing by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    fun draft(): TtsService = service.copy(
        name = name.trim(),
        format = format,
        baseUrl = baseUrl.trim(),
        apiKey = apiKey.trim(),
        model = model.trim(),
        voice = voice.trim()
    )

    // 试听用的播放器；关掉这个对话框就放掉声道。
    val previewPlayer = remember {
        TtsAudioPlayer(context, object : TtsListener {
            override fun onSpeakStarted(utteranceId: String) = Unit
            override fun onSpeakFinished(utteranceId: String) = Unit
            override fun onSpeakFailed(message: String) { notice = "试听失败：$message" }
        })
    }
    DisposableEffect(Unit) {
        onDispose { previewPlayer.release() }
    }

    fun fetchLists() {
        val current = draft()
        fetching = true
        notice = "正在读…"
        scope.launch {
            val models = when (current.format) {
                TtsRequestFormat.EDGE, TtsRequestFormat.BUILT_IN -> presetModelOptions(current.format)
                else -> runCatching { withContext(Dispatchers.IO) { TtsHttp.models(current) } }.getOrNull().orEmpty()
                    .ifEmpty { presetModelOptions(current.format) }
            }
            val voices = when (current.format) {
                TtsRequestFormat.EDGE -> runCatching { withContext(Dispatchers.IO) { EdgeSpeech.voicesBlocking() } }
                    .getOrNull().orEmpty().ifEmpty { presetVoiceOptions(current.format) }
                TtsRequestFormat.BUILT_IN -> presetVoiceOptions(current.format)
                else -> runCatching { withContext(Dispatchers.IO) { TtsHttp.voices(current) } }.getOrNull().orEmpty()
                    .ifEmpty { presetVoiceOptions(current.format) }
            }
            modelOptions = models
            voiceOptions = voices
            fetching = false
            notice = when {
                models.isNotEmpty() && voices.isNotEmpty() ->
                    "读到 ${models.size} 个模型、${voices.size} 个音色；选一个就行，也可以自己填"

                voices.isNotEmpty() -> "读到 ${voices.size} 个音色；选一个就行，也可以自己填"
                models.isNotEmpty() -> "读到 ${models.size} 个模型；这个服务没给音色列表，自己填一个"
                else -> "这个服务没读到列表；模型和音色自己填也行"
            }
        }
    }

    fun previewVoice() {
        val current = draft()
        if (!current.isUsable) {
            notice = "先填服务地址"
            return
        }
        previewing = true
        notice = "正在取试听音频…"
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val options = TtsSpeechOptions(voice = current.voice)
                    if (current.format == TtsRequestFormat.EDGE) {
                        EdgeSpeech.synthesize(PREVIEW_TEXT, options)
                    } else {
                        TtsHttp.synthesize(current, PREVIEW_TEXT, options)
                    }
                }
            }
            previewing = false
            result.fold(
                onSuccess = { bytes ->
                    val started = previewPlayer.play("talebook-tts-preview", bytes)
                    notice = if (started) {
                        "正在试听（${String.format(java.util.Locale.US, "%.0f", bytes.size / 1024.0)} KB）"
                    } else {
                        "取回来的音频放不出来，换一个能出 MP3 的模型试试"
                    }
                },
                onFailure = { error -> notice = "试听失败：${error.message ?: "连不上服务"}" }
            )
        }
    }

    // Edge 那一套只是要挑音色：打开就把微软的音色列表读出来，省得还要再点一下。
    LaunchedEffect(service.id) {
        if (service.isEdge) fetchLists()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    service.isEdge -> "Edge 用哪个音色"
                    service.id.isBlank() -> "添加朗读服务"
                    else -> "改这套朗读服务"
                }
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (service.isEdge) {
                    Text(
                        text = "微软 Edge 的在线语音：免费、不用填地址和 Key，这里只挑用哪个音色。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("叫什么名字") },
                        placeholder = { Text("比如：家里的电脑、MiMo") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Text(text = "哪种接口", style = MaterialTheme.typography.titleSmall)
                    Column(modifier = Modifier.selectableGroup()) {
                        TtsRequestFormat.addable.forEach { item ->
                            ThemeOptionRow(
                                selected = format == item,
                                icon = Icons.Default.Cloud,
                                label = when (item) {
                                    TtsRequestFormat.OPENAI_SPEECH -> "OpenAI 兼容的 /audio/speech"
                                    else -> "OpenAI 兼容的 /chat/completions（MiMo）"
                                },
                                onSelect = {
                                    format = item
                                    modelOptions = presetModelOptions(item)
                                    voiceOptions = presetVoiceOptions(item)
                                }
                            )
                        }
                    }
                    Text(
                        text = format.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it.trim() },
                        label = { Text("服务地址") },
                        placeholder = {
                            Text(if (format == TtsRequestFormat.OPENAI_CHAT) TtsPresets.MIMO_BASE_URL else "https://192.168.1.20:8880/v1")
                        },
                        supportingText = { Text("填到 /v1 为止，后面的路径由程序自己接") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                    )
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it.trim() },
                        label = { Text("API Key（自建服务可以留空）") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { showApiKey = !showApiKey }) {
                                Icon(
                                    if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showApiKey) "隐藏" else "显示"
                                )
                            }
                        }
                    )
                    PickerFieldRow(
                        label = "模型",
                        value = model,
                        placeholder = "模型名",
                        options = modelOptions,
                        selectedId = model,
                        expanded = modelMenuExpanded,
                        onExpandedChange = { modelMenuExpanded = it },
                        onValueChange = { model = it },
                        onPick = { picked ->
                            model = picked
                            modelMenuExpanded = false
                        }
                    )
                }
                PickerFieldRow(
                    label = "音色",
                    value = voice,
                    placeholder = if (format == TtsRequestFormat.EDGE) TtsPresets.EDGE_VOICES.first().id else "音色名",
                    options = voiceOptions,
                    selectedId = voice,
                    expanded = voiceMenuExpanded,
                    onExpandedChange = { voiceMenuExpanded = it },
                    onValueChange = { voice = it },
                    onPick = { picked ->
                        voice = picked
                        voiceMenuExpanded = false
                    }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { fetchLists() },
                        modifier = Modifier.weight(1f),
                        enabled = !fetching
                    ) { Text(if (fetching) "读取中…" else if (service.isEdge) "读音色列表" else "读模型和音色") }
                    OutlinedButton(
                        onClick = { previewVoice() },
                        modifier = Modifier.weight(1f),
                        enabled = !previewing
                    ) {
                        Icon(Icons.Default.VolumeUp, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (previewing) "试听中…" else "试听")
                    }
                }
                notice?.let { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSaved(draft()) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** 一个文本框加一个下拉：能自己填，也能从读出来的列表里选。 */
@Composable
private fun PickerFieldRow(
    label: String,
    value: String,
    placeholder: String,
    options: List<TtsVoice>,
    selectedId: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onValueChange: (String) -> Unit,
    onPick: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text(placeholder) },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Box {
                OutlinedButton(
                    onClick = { onExpandedChange(true) },
                    enabled = options.isNotEmpty(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) { Text("选") }
                DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(option.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (option.label != option.id) {
                                        Text(
                                            text = option.id,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            },
                            onClick = { onPick(option.id) }
                        )
                    }
                }
            }
        }
        if (selectedId.isNotBlank() && options.none { option -> option.id == selectedId }) {
            Text(
                text = "这个${label}不在读出来的列表里，照用也行",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 服务没给模型列表时的候选。 */
private fun presetModelOptions(format: TtsRequestFormat): List<TtsVoice> = when (format) {
    TtsRequestFormat.OPENAI_CHAT -> TtsPresets.MIMO_MODELS
    TtsRequestFormat.OPENAI_SPEECH -> listOf(TtsVoice(TtsPresets.OPENAI_SPEECH_MODEL, TtsPresets.OPENAI_SPEECH_MODEL))
    TtsRequestFormat.EDGE, TtsRequestFormat.BUILT_IN -> emptyList()
}

/** 服务没给音色列表时的候选。 */
private fun presetVoiceOptions(format: TtsRequestFormat): List<TtsVoice> = when (format) {
    TtsRequestFormat.OPENAI_CHAT -> TtsPresets.MIMO_VOICES
    TtsRequestFormat.OPENAI_SPEECH -> TtsPresets.OPENAI_VOICES
    TtsRequestFormat.EDGE -> TtsPresets.EDGE_VOICES
    TtsRequestFormat.BUILT_IN -> emptyList()
}

/** 试听用的一句话。 */
private const val PREVIEW_TEXT = "这是一段试听，用来确认地址、口令和音色都能用。"

@Composable
private fun AccentPicker(
    accents: List<AppAccentPalette>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        accents.forEach { accent ->
            Column(modifier = Modifier.width(40.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(if (accent.id == selected) 32.dp else 28.dp)
                        .clip(CircleShape)
                        .background(accent.lightPrimary.toColor())
                        .clickable { onSelect(accent.id) }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = accent.label,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (accent.id == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ServerEditDialog(
    server: LibraryServerConfig?,
    defaultName: String = "",
    onDismiss: () -> Unit,
    onSave: (LibraryServerConfig) -> Unit
) {
    var name by remember(server, defaultName) { mutableStateOf(server?.name ?: defaultName) }
    var baseUrl by remember(server) { mutableStateOf(server?.baseUrl ?: "https://") }
    var username by remember(server) { mutableStateOf(server?.username.orEmpty()) }
    var password by remember(server) { mutableStateOf(server?.password.orEmpty()) }
    var loginMode by remember(server) { mutableStateOf(server?.loginMode?.ifBlank { "password" } ?: "password") }
    var isPrivateMode by remember(server) { mutableStateOf(server?.isPrivateMode == true) }
    var serverType by remember(server) {
        mutableStateOf(ServerType.fromKey(server?.serverType ?: "talebook"))
    }
    var basicUser by remember(server) { mutableStateOf(server?.httpBasicUser.orEmpty()) }
    var basicPass by remember(server) { mutableStateOf(server?.httpBasicPass.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (server == null) "新增书库" else "编辑书库") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("书库名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("服务器地址") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "服务端类型",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ServerType.values().forEach { type ->
                        FilterChip(
                            selected = serverType == type,
                            onClick = { serverType = type },
                            label = { Text(type.displayName) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                if (serverType == ServerType.OPDS) {
                    Text(
                        text = "OPDS 通用协议（HTTP Basic 认证），保存后会用 Basic 认证测试连接。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = basicUser,
                        onValueChange = { basicUser = it },
                        label = { Text("用户名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = basicPass,
                        onValueChange = { basicPass = it },
                        label = { Text("密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
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
                        CompactSwitch(
                            checked = isPrivateMode,
                            onCheckedChange = { isPrivateMode = it }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = loginMode == "password",
                            onClick = { loginMode = "password" },
                            label = { Text("账号密码") }
                        )
                        FilterChip(
                            selected = loginMode == "guest",
                            onClick = { loginMode = "guest" },
                            label = { Text("访客登录") }
                        )
                    }
                    if (loginMode == "password") {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("账号") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("密码") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            text = "访客登录无需填写凭据，保存后可直接连接。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isPrivateMode) {
                        Text(
                            text = "保存后会弹出站点访问码 + 验证码窗口，完成后写入 cookie。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        LibraryServerConfig(
                            id = server?.id.orEmpty(),
                            name = name.ifBlank { baseUrl },
                            baseUrl = baseUrl,
                            loginMode = if (serverType == ServerType.OPDS) "guest" else loginMode,
                            username = if (serverType == ServerType.OPDS) "" else if (loginMode == "password") username else "访客",
                            password = if (serverType == ServerType.OPDS) "" else if (loginMode == "password") password else "",
                            accessCode = "",
                            isPrivateMode = serverType != ServerType.OPDS && isPrivateMode,
                            siteAccessCode = server?.siteAccessCode.orEmpty(),
                            nickname = server?.nickname.orEmpty(),
                            serverType = serverType.key,
                            httpBasicUser = if (serverType == ServerType.OPDS) basicUser else "",
                            httpBasicPass = if (serverType == ServerType.OPDS) basicPass else "",
                            createdAt = server?.createdAt ?: System.currentTimeMillis()
                        )
                    )
                },
                enabled = baseUrl.isNotBlank()
            ) { Text(if (server == null) "添加并登录" else "保存并重新登录") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

private data class UnlockPendingState(
    val server: LibraryServerConfig,
    val url: String
)

private data class LoginPendingState(
    val server: LibraryServerConfig,
    val url: String,
    val username: String,
    val password: String,
    val isGuest: Boolean
)

@Composable
private fun ChangelogDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("更新日志") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                ChangelogSection(
                    version = "3.4.3",
                    items = listOf(
                        "朗读可以换引擎（设置 → 朗读）：列表里现成就有两套，手机内置的不联网、也没有次数限制；微软 Edge 的在线语音免费、不用配任何东西，只需挑一个音色。",
                        "「添加朗读服务」可以加好几套，两种 OpenAI 兼容的接口任选：/audio/speech（自建服务、Kokoro-FastAPI 等）和 /chat/completions（小米 MiMo 这类）；每套存自己的名字、地址、Key、模型和音色，读的时候选一套用。",
                        "配的时候可以读模型和音色列表（也能自己填），还能先试听一句，确认能用再去读书。",
                        "默认只在 Wi-Fi 下用在线服务（可以关掉）；网络不合适或者在线那套读不成，会自动改用手机内置的并说明原因。下一句的音频会提前取好，句与句之间不会明显空一下，音频只在内存里播放，不写文件、也不占阅读缓存。",
                        "折叠屏支持在外屏显示本书封面（测试功能，只在小米 18 Fold 上验证过）：展开用内屏读书时外屏显示封面，不用时会把外屏下电；开关在阅读面板的工具栏里，阅读设置里也有说明和已知前提。"
                    )
                )
                ChangelogSection(
                    version = "3.4.3beta3",
                    items = listOf(
                        "关闭「外屏显示封面」功能、退到后台、退出阅读器、退出应用时，会给外屏下电。",
                        "如果系统坚持要点亮外屏，会改为禁用它的电源；下次显示封面时自动启用回来。发生折叠后外屏成为当前屏幕时不下电。",
                        "熄屏再点亮后，外屏封面会自动恢复，不需要重新打开开关。",
                        "应用被强制停止时若来不及下电，下次启动会自动补做。"
                    )
                )
                ChangelogSection(
                    version = "3.4.3beta2",
                    items = listOf(
                        "阅读器顶栏新增「外屏显示封面」入口：只在使用内屏时出现，实心封面图标表示已开启，线框图标表示已关闭。",
                        "修复修改字号、行距、翻页方式或「横屏双页显示」等设置时会顺带关闭外屏封面的问题。",
                        "发生折叠动作时自动关闭该开关；改其他显示设置不会联动关闭。"
                    )
                )
                ChangelogSection(
                    version = "3.4.3beta",
                    items = listOf(
                        "新增折叠屏外屏显示封面（Beta，测试功能）：展开用内屏看书时，把本书封面显示在外屏给旁边的人看。",
                        "入口在阅读器高级设置，只在使用内屏时可以打开；每次进入阅读器默认关闭，不做持久化。",
                        "优先使用官方 androidx.window 背屏区域能力；系统不提供该能力时直连内置外屏。",
                        "已知限制：部分折叠屏展开时系统默认不给外屏供电，应用会自己尝试点亮外屏；没有封面的书（本地导入、外部打开）不会显示；合上手机时外屏封面会收起，重新展开自动恢复。"
                    )
                )
                ChangelogSection(
                    version = "3.4.2",
                    items = listOf(
                        "支持从文件管理器等外部应用直接打开 EPUB / PDF / TXT 文件。",
                        "在系统「打开方式」中可选择本应用作为默认阅读器。"
                    )
                )
                ChangelogSection(
                    version = "3.4.1",
                    items = listOf(
                        "修复百分比跳转始终落在最近章节开头的问题（按真实页定位）。",
                        "内核升级至 Readium 3.4.0。",
                        "PDF 滚动为竖向、翻页为左右横向；修复 PDF 共1页问题。",
                        "进度百分比与跳转保留两位小数。",
                        "修复状态栏顶栏颜色未生效问题。"
                    )
                )
                ChangelogSection(
                    version = "2.3.2",
                    items = listOf(
                        "新增自定义字体功能。",
                        "新增四个预设图片背景。",
                        "修复配置导入无法找到文件的bug。"
                    )
                )
                ChangelogSection(
                    version = "2.3.1",
                    items = listOf(
                        "增加上下页边距调整。",
                        "修复上下页边距无法调整的bug。"
                    )
                )
                ChangelogSection(
                    version = "2.3.0",
                    items = listOf(
                        "阅读器字体选择改为4种：默认、宋体（serif）、黑体（sans-serif）、等宽（monospace）。",
                        "修复字间距、行间距、段间距设置不生效的问题。",
                        "移除页边距上下左右分开设置功能，改为统一页边距。",
                        "修复跳转进度后页面布局不一致的问题。",
                        "修复折叠屏横屏闪退的 bug。",
                        "修复跳转进度后的显示 bug。"
                    )
                )
                ChangelogSection(
                    version = "2.3.0alpha",
                    items = listOf(
                        "多服务端类型支持：登录页与设置页可切换 Talebook / MyBooks / OPDS 三种服务端类型。",
                        "OPDS 通用协议：通过 HTTP Basic 认证接入标准 OPDS 1.2 书库（不支持书架 / 收藏 / 阅读状态）。",
                        "MyBooks 适配：基于 PoxenStudio/mybooks（fork 自 talebook v25.06.26），调用 /api/access、/api/wants、/api/all，无需 captcha。",
                        "服务端架构抽象：新增 ServerType 枚举、ServerCapabilities、ServerAdapter 接口与 Factory；原有 Talebook API 调用端点接入 TalebookAdapter。",
                        "OPDS 客户端：自实现轻量 OpdsClient（HTTP Basic 拦截器）、OpdsRepository、OpdsXmlParser、XmlLite（基于 XmlPullParser）。",
                        "LibraryServerConfig 扩展：新增 serverType、httpBasicUser、httpBasicPass 字段，全部 nullable 以兼容老数据。",
                        "Book 模型扩展：新增 wants / shelf / favorite / readState / downloadUrl / source 字段，适配多种服务端共用的归一模型。",
                        "SettingsRepository 新增 activeServerType / activeHttpBasicUser / activeHttpBasicPass Flow，供 UI 持久化用户上次选择。",
                        "设置页与登录页：书库配置对话框加服务端类型 FilterChip；OPDS Basic Auth 输入位置调整，避免重复表单。",
                        "OPDS 凭据保存：OpdsLoginForm 连接测试成功后调用 saveServerType 写入 Basic Auth 凭据，重启 App 自动预填。"
                    )
                )
                ChangelogSection(
                    version = "2.2.3beta3",
                    items = listOf(
                        "极验（GeeTest v4）原生支持：服务端启用极验 captcha 时，App 在登录页和设置页书库配置自动探测 api/captcha/config，使用内置 WebView 加载 gt4.js SDK 完成拼图/滑动验证。",
                        "验证完成后自动提交 4 个参数（lot_number、captcha_output、pass_token、gen_time）到 api/welcome / api/user/sign_in，由服务端调极验 /validate 完成最终校验。",
                        "新组件 GeetestCaptchaView（app/src/main/java/com/talebook/app/ui/components/GeetestCaptchaView.kt）：通过 addJavascriptInterface(\"AndroidBridge\") 把极验结果回传到 Kotlin，DisposableEffect 中 destroy() 释放内存。",
                        "HTML 容器位于 app/src/main/assets/geetest.html，按需加载极验 SDK。",
                        "API 扩展：TalebookApi.loginWithCode / loginWithPassword 新增 4 个可选字段（极验）；AuthRepository 新增 GeetestParams 数据类；unlockSite / loginWithPassword 增加可选 geetest 参数。",
                        "极验与 image captcha 共存：CaptchaStatus.Image 显示 PNG + 输入框，CaptchaStatus.Geetest 显示 WebView，验证完成后自动 POST 请求，不需要再点确认按钮。",
                        "与现有登录模式完全兼容：不启用 captcha、image captcha、极验三种模式按服务端配置自动选择，cookie 按 host 隔离互不影响；多书库切换与会话持久化逻辑不变。",
                        "已知风险：极验 SDK 依赖境外 CDN（static.geetest.com、gcaptcha4.geetest.com），国内网络下可能超时；部分国产 ROM 替换 WebView 实现可能报\"环境不安全\"。",
                        "测试状态：当前 APK 已本地启动测试，尚未在真实极验服务端验证端到端流程。"
                    )
                )
                ChangelogSection(
                    version = "2.2.3beta2",
                    items = listOf(
                        "完整人机验证支持：客户端支持服务端图形验证码（image provider）。",
                        "服务端启用 captcha 时，登录页与设置页书库配置会自动探测 api/captcha/config，需要时显示验证码图片 + 输入框 + 刷新按钮。",
                        "登录前必须输入正确的验证码，验证码 cookie 由 OkHttp 持久化（2 分钟有效）。",
                        "设置页多书库登录体验统一：每次新增/编辑书库都走完整登录流程——探测 unlockSite → 探测 login captcha → 必要时依次弹访问码对话框和登录验证码对话框。",
                        "抽象 UnlockSiteDialog / LoginCaptchaDialog 共用组件（app/src/main/java/com/talebook/app/ui/components/CaptchaDialogs.kt），登录页与设置页复用同一套实现。"
                    )
                )
                ChangelogSection(
                    version = "2.2.3beta1",
                    items = listOf(
                        "修复 Talebook v26.9.1+ 站点访问码登录失败：客户端 api/welcome 字段名由 code 修正为服务端要求的 invite_code。",
                        "支持 Talebook 私人模式（INVITE_MODE）：服务器配置对话框新增\"是否启用私人模式\"开关，启用后增加\"私人模式访问码\"输入项。",
                        "登录流程会先调用 api/welcome 解锁站点，再走账号密码/访问码/访客登录。",
                        "老用户数据兼容：LibraryServerConfig 新增字段为 nullable，老 JSON 数据反序列化不再崩溃。",
                        "补充（人机验证支持）：服务端启用图形验证码时自动探测 api/captcha/config 并展示图片 + 输入框；失败自动刷新；兼容未启用 captcha 的服务端。"
                    )
                )
                ChangelogSection(
                    version = "2.2.2beta2",
                    items = listOf(
                        "移除应用内置的默认服务器地址：首次启动必须手动配置服务器 URL，不再自动填充任何默认地址。",
                        "阅读时显示当前书名与章节标题：在底部进度条下方居中显示\"书名 > 章节名\"，各最多 12 字后省略号截断。",
                        "高级设置拆分为独立开关：阅读时隐藏状态栏 / 隐藏时间 / 隐藏书名和章节 三个独立开关，默认都显示。",
                        "字号范围 50%~300%、行距 0.5~3、页边距 0.5~3、亮度 0~100%、字间距 0~10、段间距 0~4。"
                    )
                )
                ChangelogSection(
                    version = "2.2.2beta",
                    items = listOf(
                        "朗读段落高亮：正在朗读的句子所在段落以浅蓝高亮显示，跟随朗读自动翻页。",
                        "音频焦点处理：朗读时来电或其他应用播放音频会自动暂停，结束后自动恢复。",
                        "朗读面板新增电池优化提示与\"电池优化设置\"按钮，解决部分机型后台无法播放的问题。"
                    )
                )
                ChangelogSection(
                    version = "2.2.2alpha",
                    items = listOf(
                        "朗读锁屏播放：开启朗读后锁屏可继续播放（后台 Foreground Service + WakeLock 实现，无通知栏控件）。",
                        "朗读设置面板：改为从顶部滑入的浮动面板，与迷你播放条上下共存。",
                        "朗读控制图标化：迷你播放条与设置面板的\"播放/暂停/上一句/下一句/关闭\"按钮改用标准 Material 图标。",
                        "状态栏适配：迷你播放条与设置面板自动避让状态栏与刘海屏。"
                    )
                )
                ChangelogSection(
                    version = "2.2.1b",
                    items = listOf(
                        "修复部分 EPUB 书籍无法设置字体/字号的问题。",
                        "改进\"强制使用出版社字体\"开关逻辑：关闭时通过 CSS !important 强制覆盖出版社的内联样式（包括字号和字体），使所有书籍的字体字号可正常调整。"
                    )
                )
                ChangelogSection(
                    version = "2.2.0",
                    items = listOf(
                        "移除在线阅读器，所有阅读入口统一走 Readium 本地阅读器。",
                        "新增本地书架：通过 SAF 添加本地文件夹，不复制源文件，支持 epub / pdf / txt。",
                        "TXT 本地阅读升级：流式转换为 EPUB、自动识别章节目录、缓存转换结果、支持 UTF-8 / UTF-16 / GB18030 / GBK。",
                        "优化书库打开体验：主页缓存、下拉刷新、未配置服务器空态。",
                        "软件不再强制登录，可跳过验证直接进入本地书架与设置。",
                        "全局隐藏导航栏；阅读器默认保留状态栏，可在高级设置中强制隐藏。",
                        "阅读器固定为沉浸式覆盖工具栏，不再提供非全屏阅读方式。",
                        "可分开配置上下和左右页边距。",
                        "最近阅读新增书源标记，可区分本地书和书库书。",
                        "增加开屏画面，已登录状态不再显示登录页面。",
                        "重做阅读器主题和背景系统，EPUB 书页层通过 Readium preferences 应用背景色与文字色。",
                        "修复本地书进度、书签、笔记按本地书 ID 保存和读取的问题。"
                    )
                )
                ChangelogSection(
                    version = "2.1.0",
                    items = listOf(
                        "完善了缓存管理，增加缓存下载进度管理。",
                        "增加了字体和背景颜色设置及预设。",
                        "增加了笔记按书导出 md 格式。",
                        "修复了部分书无法阅读的 bug。",
                        "软件本身的主题颜色设置。",
                        "重构显示方式，设计为三标签显示方式并自定义配置首页。",
                        "增加最近阅读首页，增加编辑功能、置顶/移顶、收起分组。",
                        "增加多书库支持。",
                        "修复了部分书无法阅读的 bug。",
                        "修复远程阅读大字体 EPUB 在 WebView 下出现白屏的问题，新增\"强制使用出版社字体\"开关。"
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

@Composable
private fun ChangelogSection(version: String, items: List<String>) {
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = version,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(modifier = Modifier.height(4.dp))
    items.forEach { item ->
        Text(
            text = "• $item",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
        )
    }
}
