package com.talebook.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Public
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.talebook.app.data.api.RetrofitClient
import com.talebook.app.data.repository.AuthRepository
import com.talebook.app.data.repository.LibraryServerConfig
import com.talebook.app.data.repository.LoginResult
import com.talebook.app.data.repository.ReaderBackupRepository
import com.talebook.app.data.repository.ReaderCacheRepository
import com.talebook.app.data.repository.SettingsRepository
import com.talebook.app.ui.theme.AppAccentPalette
import com.talebook.app.ui.theme.ThemePresets
import com.talebook.app.ui.theme.toColor
import com.talebook.app.ui.screens.CompactSwitch
import com.talebook.app.viewmodel.HomeViewModel
import kotlinx.coroutines.launch

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
    var editCacheLimit by remember(cacheLimitMb) { mutableStateOf(cacheLimitMb.toString()) }
    var cacheMessage by remember { mutableStateOf<String?>(null) }
    var cacheSizeText by remember { mutableStateOf("未统计") }
    var backupMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var editingServer by remember { mutableStateOf<LibraryServerConfig?>(null) }
    var showServerDialog by remember { mutableStateOf(false) }
    val authRepository = remember { AuthRepository() }
    val readerCacheRepository = remember { ReaderCacheRepository() }
    val readerBackupRepository = remember { ReaderBackupRepository() }

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
            title = { Text("确认导入阅读数据？") },
            text = { Text("将从 Download/talebook/ 导入最近一次备份。已有阅读进度可能被覆盖，书签和笔记会追加导入。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showImportDialog = false
                        scope.launch {
                            readerBackupRepository.importLatestFromDownloads(context.applicationContext).fold(
                                onSuccess = { msg ->
                                    backupMessage = msg
                                    HomeViewModel.clearCache(context.applicationContext)
                                },
                                onFailure = { e -> backupMessage = e.message ?: "导入失败" }
                            )
                        }
                    }
                ) { Text("导入") }
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
            onSave = { server ->
                scope.launch {
                    val id = settingsRepository.upsertLibraryServer(server)
                    RetrofitClient.updateBaseUrl(server.baseUrl)
                    val authRepo = AuthRepository()
                    val loginResult = when (server.loginMode) {
                        "password" -> authRepo.loginWithPassword(server.username, server.password)
                        "code" -> authRepo.loginWithCode(server.accessCode)
                        "guest" -> authRepo.loginWithPassword("", "")
                        else -> LoginResult.Failure("unknown mode")
                    }
                    if (loginResult is com.talebook.app.data.repository.LoginResult.Success) {
                        settingsRepository.saveLoginInfo(loginResult.mode, loginResult.username, loginResult.nickname)
                        settingsRepository.saveLoginSecret(loginResult.mode, loginResult.username, server.password, server.accessCode, loginResult.nickname)
                    }
                    showServerDialog = false
                    editingServer = null
                }
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
                text = if (loginMode.isBlank()) "当前未登录" else "当前：${nickname.ifBlank { "访客" }} · ${if (loginMode == "code") "访问码登录" else "账号密码登录"}",
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
                                    text = if (server.loginMode.isBlank()) "未登录" else "${server.nickname.ifBlank { server.username.ifBlank { "已登录" } }} · ${if (server.loginMode == "code") "访问码" else "账号密码"}",
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
            ) { Text("导入最近一次阅读数据备份") }
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
                text = "Tale Book v2.2.0",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "TaleBook 安卓客户端",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "更新日志：移除在线阅读器；全局隐藏导航栏；阅读状态栏可隐藏；左右/上下独立页边距；主页缓存首屏 + 右上/下拉刷新；跳过验证直接进入；本地书架 (SAF 文件夹、不复制)；最近阅读来源标签。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

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
    var accessCode by remember(server) { mutableStateOf(server?.accessCode.orEmpty()) }
    var loginMode by remember(server) { mutableStateOf(server?.loginMode?.ifBlank { "password" } ?: "password") }

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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = loginMode == "password",
                        onClick = { loginMode = "password" },
                        label = { Text("账号密码") }
                    )
                    FilterChip(
                        selected = loginMode == "code",
                        onClick = { loginMode = "code" },
                        label = { Text("访问码") }
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
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (loginMode == "code") {
                    OutlinedTextField(
                        value = accessCode,
                        onValueChange = { accessCode = it },
                        label = { Text("访问码") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        text = "访客登录无需填写凭据，保存后可直接连接。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                            loginMode = loginMode,
                            username = if (loginMode == "password") username else "访客",
                            password = if (loginMode == "password") password else "",
                            accessCode = if (loginMode == "code") accessCode else "",
                            nickname = server?.nickname.orEmpty(),
                            createdAt = server?.createdAt ?: System.currentTimeMillis()
                        )
                    )
                },
                enabled = baseUrl.isNotBlank()
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
