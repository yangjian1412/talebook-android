package com.talebook.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.talebook.app.data.repository.SettingsRepository
import com.talebook.app.ui.screens.*

@Composable
fun NavGraph(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val settingsRepository = remember { SettingsRepository(context) }
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
            HomeScreen(
                onBookClick = { bookId -> navController.navigate("book/$bookId") },
                onNavigateSearch = { navController.navigate("search") },
                onNavigateLibrary = { navController.navigate("library") },
                onNavigateSettings = { navController.navigate("settings") }
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

        composable("local_reader/{bookId}", arguments = listOf(navArgument("bookId") { type = NavType.IntType })) {
            val bookId = it.arguments?.getInt("bookId") ?: return@composable
            LocalReaderScreen(
                bookId = bookId,
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
                onOpenCacheList = { navController.navigate("cached_books") }
            )
        }

        composable("cached_books") {
            CachedBooksScreen(
                onBack = { navController.popBackStack() },
                onOpenBookDetail = { bookId -> navController.navigate("book/$bookId") },
                onReadLocalBook = { bookId -> navController.navigate("local_reader/$bookId") }
            )
        }
    }
}
