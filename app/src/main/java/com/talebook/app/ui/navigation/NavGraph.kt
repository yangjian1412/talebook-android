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
    NavHost(
        navController = navController,
        startDestination = "splash"
    ) {
        composable("splash") {
            SplashScreen(
                settingsRepository = settingsRepository,
                onGoHome = {
                    navController.navigate("home") {
                        popUpTo("splash") { inclusive = true }
                    }
                },
                onGoLogin = {
                    navController.navigate("login") {
                        popUpTo("splash") { inclusive = true }
                    }
                }
            )
        }

        composable(
            "login?bookId={bookId}",
            arguments = listOf(navArgument("bookId") { type = NavType.IntType; defaultValue = -1 })
        ) { entry ->
            val resumeBookId = entry.arguments?.getInt("bookId")?.takeIf { it > 0 }
            LoginScreen(
                settingsRepository = settingsRepository,
                onLoginSuccess = {
                    val id = resumeBookId
                    if (id != null) {
                        navController.navigate("local_reader/$id") {
                            popUpTo("login") { inclusive = true }
                        }
                    } else {
                        navController.navigate("home") {
                            popUpTo("login") { inclusive = true }
                        }
                    }
                },
                onSkipAuth = {
                    navController.navigate("home") {
                        popUpTo("login") { inclusive = true }
                    }
                },
                resumeBookId = resumeBookId
            )
        }

        composable("home") {
            MainTabsScreen(
                settingsRepository = settingsRepository,
                onBookClick = { bookId -> navController.navigate("book/$bookId") },
                onReadBook = { bookId ->
                    navController.navigate("local_reader/$bookId")
                },
                onReadLocalBook = { localBookId ->
                    navController.navigate("local_reader/local/$localBookId")
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
                onBack = { navController.popBackStack() },
                onRead = { id ->
                    navController.navigate("local_reader/$id")
                }
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
                onBack = { navController.popBackStack() },
                onOpenLogin = { id ->
                    navController.navigate("login?bookId=$id")
                },
                onOpenSettings = { navController.navigate("settings") }
            )
        }

        composable(
            "local_reader/local/{localBookId}",
            arguments = listOf(navArgument("localBookId") { type = NavType.LongType })
        ) {
            val localBookId = it.arguments?.getLong("localBookId") ?: return@composable
            LocalReaderScreen(
                localBookId = localBookId,
                onBack = { navController.popBackStack() },
                onOpenSettings = { navController.navigate("settings") }
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
                },
                onOpenLocalLibrary = { navController.navigate("local_library") }
            )
        }

        composable("local_library") {
            LocalLibraryScreen(
                onBookClick = { bookId -> navController.navigate("book/$bookId") },
                onReadLocalBook = { localBookId -> navController.navigate("local_reader/local/$localBookId") },
                onBack = { navController.popBackStack() }
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
