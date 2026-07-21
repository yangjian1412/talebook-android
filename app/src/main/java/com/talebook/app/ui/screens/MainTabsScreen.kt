package com.talebook.app.ui.screens

import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.talebook.app.data.repository.SettingsRepository

@Composable
fun MainTabsScreen(
    settingsRepository: SettingsRepository,
    onBookClick: (Int) -> Unit,
    onReadBook: (Int) -> Unit,
    onNavigateSearch: () -> Unit,
    onNavigateLibrary: () -> Unit,
    onLogout: () -> Unit,
    onOpenCacheList: () -> Unit,
    onOpenNotesManagement: () -> Unit
) {
    val startTab by settingsRepository.startTab.collectAsState(initial = "")
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var startTabApplied by rememberSaveable { mutableStateOf(false) }
    var recentRefreshSignal by remember { mutableIntStateOf(0) }

    LaunchedEffect(startTab) {
        if (!startTabApplied && startTab.isNotBlank()) {
            selectedTab = startTab.toTabIndex()
            startTabApplied = true
            if (selectedTab == 0) recentRefreshSignal++
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(modifier = Modifier.height(56.dp)) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0; recentRefreshSignal++ },
                    icon = { Icon(Icons.Default.History, contentDescription = "最近阅读") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.MenuBook, contentDescription = "书库") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "设置") }
                )
            }
        }
    ) { padding ->
        when (selectedTab) {
            0 -> RecentReadingScreen(
                contentPadding = padding,
                refreshSignal = recentRefreshSignal,
                onBookClick = onBookClick,
                onReadBook = onReadBook
            )
            1 -> HomeScreen(
                contentPadding = padding,
                onBookClick = onBookClick,
                onNavigateSearch = onNavigateSearch,
                onNavigateLibrary = onNavigateLibrary
            )
            2 -> SettingsScreen(
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

private fun String.toTabIndex(): Int = when (this) {
    SettingsRepository.START_TAB_LIBRARY -> 1
    SettingsRepository.START_TAB_SETTINGS -> 2
    else -> 0
}
