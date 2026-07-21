package com.talebook.app.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.talebook.app.data.repository.SettingsRepository
import com.talebook.app.ui.screens.*
import kotlinx.coroutines.launch

@Composable
fun NavGraph(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val settingsRepository = remember { SettingsRepository(context) }
    val scope = rememberCoroutineScope()
    val isLoggedIn by settingsRepository.isLoggedIn.collectAsState(initial = false)
    val readerMode by settingsRepository.readerMode.collectAsState(initial = SettingsRepository.READER_LOCAL)

    NavHost(
        navController = navController,
        startDestination = if (isLoggedIn) "home" else "login"
    ) {
        composable("login") {
            LoginScreen(
                settingsRepository = settingsRepository,
                onLoginSuccess = {
                    navController.navigate("home") {
                        popUpTo("login") { inclusive = true }
                    }
                },
                onAnonymousEnter = {
                    navController.navigate("home") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }

        composable("home") {
            MainTabsScreen(
                settingsRepository = settingsRepository,
                onBookClick = { bookId -> navController.navigate("book/$bookId") },
                onReadBook = { bookId ->
                    if (readerMode == SettingsRepository.READER_LOCAL) {
                        navController.navigate("local_reader/$bookId")
                    } else {
                        navController.navigate("reader/$bookId")
                    }
                },
                onNavigateSearch = { navController.navigate("search") },
                onNavigateLibrary = { navController.navigate("library") },
                onLogout = {
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onOpenCacheList = { navController.navigate("cached_books") },
                onOpenNotesManagement = {
                    scope.launch {
                        settingsRepository.saveStartTab(SettingsRepository.START_TAB_SETTINGS)
                        navController.navigate("notes_management")
                    }
                }
            )
        }

        composable("search") {
            SearchScreen(
                onBookClick = { bookId -> navController.navigate("book/$bookId") },
                onBack = { navController.popBackStack() }
            )
        }

        composable("library") {
            LibraryScreen(
                onBookClick = { bookId -> navController.navigate("book/$bookId") },
                onBack = { navController.popBackStack() }
            )
        }

        composable("book/{bookId}", arguments = listOf(navArgument("bookId") { type = NavType.IntType })) {
            val bookId = it.arguments?.getInt("bookId") ?: return@composable
            BookDetailScreen(
                bookId = bookId,
                readerMode = readerMode,
                onBack = { navController.popBackStack() },
                onRead = { id ->
                    if (readerMode == SettingsRepository.READER_LOCAL) {
                        navController.navigate("local_reader/$id")
                    } else {
                        navController.navigate("reader/$id")
                    }
                },
                onReadFullscreen = { id -> navController.navigate("reader/$id?fullscreen=true") }
            )
        }

        composable(
            "reader/{bookId}?fullscreen={fullscreen}",
            arguments = listOf(
                navArgument("bookId") { type = NavType.IntType },
                navArgument("fullscreen") { type = NavType.BoolType; defaultValue = false }
            )
        ) {
            val bookId = it.arguments?.getInt("bookId") ?: return@composable
            val isFullscreen = it.arguments?.getBoolean("fullscreen") ?: false
            ReaderScreen(
                bookId = bookId,
                isFullscreen = isFullscreen,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            "local_reader/{bookId}?locator={locator}",
            arguments = listOf(
                navArgument("bookId") { type = NavType.IntType },
                navArgument("locator") { type = NavType.StringType; defaultValue = "" }
            )
        ) {
            val bookId = it.arguments?.getInt("bookId") ?: return@composable
            val locator = it.arguments?.getString("locator")?.takeIf { value -> value.isNotBlank() }
            LocalReaderScreen(
                bookId = bookId,
                initialLocatorJson = locator,
                onBack = { navController.popBackStack() }
            )
        }

        composable("settings") {
            SettingsScreen(
                settingsRepository = settingsRepository,
                onBack = { navController.popBackStack() },
                onLogout = {
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onOpenCacheList = { navController.navigate("cached_books") },
                onOpenNotesManagement = {
                    scope.launch {
                        settingsRepository.saveStartTab(SettingsRepository.START_TAB_SETTINGS)
                        navController.navigate("notes_management")
                    }
                }
            )
        }

        composable("cached_books") {
            CachedBooksScreen(
                onBack = { navController.popBackStack() },
                onOpenBookDetail = { bookId -> navController.navigate("book/$bookId") },
                onReadLocalBook = { bookId -> navController.navigate("local_reader/$bookId") }
            )
        }

        composable("notes_management") {
            NotesManagementScreen(
                onBack = {
                    scope.launch {
                        settingsRepository.saveStartTab(SettingsRepository.START_TAB_SETTINGS)
                        navController.navigate("home") {
                            popUpTo("home") { inclusive = true }
                        }
                    }
                },
                onOpenReaderAtNote = { bookId, locatorJson ->
                    navController.navigate("local_reader/$bookId?locator=${Uri.encode(locatorJson)}")
                }
            )
        }
    }
}
