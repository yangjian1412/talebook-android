package com.talebook.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.talebook.app.data.repository.SettingsRepository

private data class TabSpec(val key: String, val title: String, val titleChinese: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
fun MainTabsScreen(
    settingsRepository: SettingsRepository,
    onBookClick: (Int) -> Unit,
    onReadBook: (Int) -> Unit,
    onReadLocalBook: (Long) -> Unit = {},
    onNavigateSearch: () -> Unit,
    onNavigateLibrary: () -> Unit,
    onLogout: () -> Unit,
    onOpenCacheList: () -> Unit,
    onOpenNotesManagement: () -> Unit,
    onOpenLocalLibrary: () -> Unit = {}
) {
    val startTab by settingsRepository.startTab.collectAsState(initial = "")
    val homeTabs by settingsRepository.homeTabs.collectAsState(initial = SettingsRepository.HOME_TABS_BOTH)
    val skipAuth by settingsRepository.skipAuth.collectAsState(initial = false)
    val autoRefreshHomeOnEnter by settingsRepository.readerAutoRefreshHomeOnEnter.collectAsState(initial = false)
    val showTabLabel by settingsRepository.showTabLabel.collectAsState(initial = true)
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var startTabApplied by rememberSaveable { mutableStateOf(false) }
    var recentRefreshSignal by remember { mutableIntStateOf(0) }
    var lastLibraryEnter by remember { mutableIntStateOf(0) }

    val tabs = remember(homeTabs, skipAuth) {
        buildList {
            if (!skipAuth) {
                add(TabSpec("recent", "最近阅读", "最近", Icons.Default.History))
            }
            if (homeTabs != SettingsRepository.HOME_TABS_LIBRARY) {
                add(TabSpec("local", "本地书架", "本地", Icons.Default.LibraryBooks))
            }
            if (homeTabs != SettingsRepository.HOME_TABS_LOCAL) {
                add(TabSpec("library", "在线书库", "书库", Icons.Default.MenuBook))
            }
            add(TabSpec("settings", "设置", "设置", Icons.Default.Settings))
        }
    }

    LaunchedEffect(startTab, tabs) {
        if (!startTabApplied && startTab.isNotBlank()) {
            val target = tabs.indexOfFirst { it.key == startTab.toTabKey() }
            if (target >= 0) {
                selectedTab = target
                if (target == 0) recentRefreshSignal++
            }
            startTabApplied = true
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(modifier = Modifier.height(56.dp)) {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = {
                            selectedTab = index
                            if (tab.key == "recent") recentRefreshSignal++
                            if (tab.key == "library") lastLibraryEnter++
                        },
                        icon = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(tab.icon, contentDescription = tab.title)
                                if (showTabLabel) {
                                    Text(
                                        text = tab.titleChinese,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { padding ->
        val current = tabs.getOrNull(selectedTab)
        when (current?.key) {
            "recent" -> RecentReadingScreen(
                contentPadding = padding,
                refreshSignal = recentRefreshSignal,
                onBookClick = onBookClick,
                onReadBook = onReadBook,
                onReadLocalBook = onReadLocalBook
            )
            "library" -> HomeScreen(
                contentPadding = padding,
                onBookClick = onBookClick,
                onNavigateSearch = onNavigateSearch,
                onNavigateLibrary = onNavigateLibrary,
                autoRefreshOnEnter = autoRefreshHomeOnEnter,
                libraryEnterSignal = lastLibraryEnter
            )
            "local" -> LocalLibraryScreen(
                contentPadding = padding,
                onOpenLocalLibrary = onOpenLocalLibrary,
                onBookClick = onBookClick,
                onReadBook = onReadBook,
                onReadLocalBook = onReadLocalBook
            )
            else -> SettingsScreen(
                settingsRepository = settingsRepository,
                contentPadding = padding,
                showBackButton = false,
                onBack = {},
                onLogout = onLogout,
                onOpenCacheList = onOpenCacheList,
                onOpenNotesManagement = onOpenNotesManagement
            )
        }
    }
}

private fun String.toTabKey(): String = when (this) {
    SettingsRepository.START_TAB_LIBRARY -> "library"
    SettingsRepository.START_TAB_LOCAL -> "local"
    SettingsRepository.START_TAB_SETTINGS -> "settings"
    else -> "recent"
}
