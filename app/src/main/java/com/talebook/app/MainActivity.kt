package com.talebook.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import androidx.fragment.app.FragmentActivity
import com.talebook.app.data.repository.SettingsRepository
import com.talebook.app.ui.navigation.NavGraph
import com.talebook.app.ui.theme.TaleReaderTheme
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val forceDark = intent.getBooleanExtra("forceDark", false)
        val startBookId = intent.getIntExtra("bookId", 0)
        android.util.Log.d(
            "TaleMain",
            "onCreate intent extras: forceDark=$forceDark startBookId=$startBookId"
        )
        if (forceDark) {
            lifecycleScope.launch {
                val repo = SettingsRepository(applicationContext)
                repo.saveThemeMode(SettingsRepository.THEME_DARK)
                android.util.Log.d("TaleMain", "forceDark: themeMode 写入 DataStore 为 DARK")
            }
        }

        enableEdgeToEdge()
        setContent {
            val settingsRepository = remember(applicationContext) {
                SettingsRepository(applicationContext)
            }
            val themeMode by settingsRepository.themeMode.collectAsState(
                initial = SettingsRepository.THEME_AUTO
            )
            val isDark = when (themeMode) {
                SettingsRepository.THEME_LIGHT -> false
                SettingsRepository.THEME_DARK -> true
                else -> isSystemInDarkTheme()
            }
            TaleReaderTheme(darkTheme = isDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavGraph(navController = navController)
                    if (startBookId > 0) {
                        LaunchedEffect(startBookId) {
                            android.util.Log.d(
                                "TaleMain",
                                "deeplink: navigate to reader/$startBookId?fullscreen=true"
                            )
                            navController.navigate("reader/$startBookId?fullscreen=true")
                        }
                    }
                }
            }
        }
    }
}
