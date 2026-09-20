package com.talebook.app

import android.os.Bundle
import android.view.KeyEvent
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import androidx.fragment.app.FragmentActivity
import com.talebook.app.data.repository.SettingsRepository
import com.talebook.app.reader.ReadiumUiEvents
import com.talebook.app.ui.navigation.NavGraph
import com.talebook.app.ui.theme.TaleReaderTheme
import com.talebook.app.ui.theme.ThemePresets
import com.talebook.app.ui.theme.toColor
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    var volumeKeyPageTurnEnabled: Boolean = false
    var volumeKeyPageTurnToast: String? = null
    var currentReaderSessionId: Long? = null
    var currentReaderScrollMode: Boolean = false

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
        applyGlobalSystemBars()

        lifecycleScope.launch {
            // Migration logic removed
        }

        setContent {
            val settingsRepository = remember(applicationContext) {
                SettingsRepository(applicationContext)
            }
            val themeMode by settingsRepository.themeMode.collectAsState(
                initial = SettingsRepository.THEME_AUTO
            )
            val appAccent by settingsRepository.appAccent.collectAsState(initial = ThemePresets.accents.first().id)
            val systemDark = isSystemInDarkTheme()
            val isDark = when (themeMode) {
                SettingsRepository.THEME_LIGHT -> false
                SettingsRepository.THEME_DARK -> true
                else -> systemDark
            }
            val primaryColor = ThemePresets.accentPrimary(appAccent, isDark).toColor()
            TaleReaderTheme(darkTheme = isDark, primaryColor = primaryColor) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavGraph(navController = navController)
                }
            }
        }
    }

    private fun applyGlobalSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.navigationBars())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.navigationBars())
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (handleVolumeKey(keyCode, isUp = false)) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (handleVolumeKey(keyCode, isUp = true)) return true
        return super.onKeyUp(keyCode, event)
    }

    private fun handleVolumeKey(keyCode: Int, isUp: Boolean): Boolean {
        if (!volumeKeyPageTurnEnabled) return false
        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) return false
        if (isUp) return true
        val sessionId = currentReaderSessionId ?: return true
        if (currentReaderScrollMode) {
            if (volumeKeyPageTurnToast != "shown") {
                volumeKeyPageTurnToast = "shown"
                android.widget.Toast.makeText(
                    this,
                    "音量键翻页仅在翻页模式生效",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            ReadiumUiEvents.emitGoBackwardKey(sessionId)
        } else {
            ReadiumUiEvents.emitGoForwardKey(sessionId)
        }
        return true
    }
}
