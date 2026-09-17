package com.talebook.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.talebook.app.data.api.RetrofitClient
import com.talebook.app.reader.ReaderScrollTapSpeed
import com.talebook.app.ui.theme.ThemePresets
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class LibraryServerConfig(
    val id: String,
    val name: String,
    val baseUrl: String,
    val loginMode: String = "",
    val username: String = "",
    val password: String = "",
    val accessCode: String = "",
    val isPrivateMode: Boolean = false,
    val siteAccessCode: String? = "",
    val nickname: String = "",
    val serverType: String? = "talebook",
    val httpBasicUser: String? = "",
    val httpBasicPass: String? = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

class SettingsRepository(private val context: Context) {
    companion object {
        const val DEFAULT_SERVER_ID = "default"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
        const val THEME_AUTO = "auto"
        const val READER_LOCAL = "local"
        const val START_TAB_RECENT = "recent"
        const val START_TAB_LIBRARY = "library"
        const val START_TAB_LOCAL = "local"
        const val START_TAB_SETTINGS = "settings"
        const val HOME_TABS_LIBRARY = "library"
        const val HOME_TABS_LOCAL = "local"
        const val HOME_TABS_BOTH = "both"

        private val SERVER_URL_KEY = stringPreferencesKey("server_url")
        private val SERVER_NAME_KEY = stringPreferencesKey("server_name")
        private val USERNAME_KEY = stringPreferencesKey("username")
        private val NICKNAME_KEY = stringPreferencesKey("nickname")
        private val LOGIN_MODE_KEY = stringPreferencesKey("login_mode")  // "code" / "password" / ""
        private val USER_ID_KEY = intPreferencesKey("user_id")
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")  // "light" / "dark" / "auto"
        private val DAY_THEME_PRESET_KEY = stringPreferencesKey("day_theme_preset")
        private val NIGHT_THEME_PRESET_KEY = stringPreferencesKey("night_theme_preset")
        private val APP_ACCENT_KEY = stringPreferencesKey("app_accent")
        private val DAY_CUSTOM_BACKGROUND_KEY = stringPreferencesKey("day_custom_background")
        private val DAY_CUSTOM_TEXT_KEY = stringPreferencesKey("day_custom_text")
        private val SKIP_AUTH_KEY = booleanPreferencesKey("skip_auth")
        private val HOME_TABS_KEY = stringPreferencesKey("home_tabs")
        private val READER_CACHE_LIMIT_MB_KEY = intPreferencesKey("reader_cache_limit_mb")
        private val READER_AUTO_CACHE_ON_WIFI_KEY = booleanPreferencesKey("reader_auto_cache_on_wifi")
        private val READER_FONT_SCALE_KEY = floatPreferencesKey("reader_font_scale")
        private val READER_FONT_FAMILY_KEY = stringPreferencesKey("reader_font_family")
        private val READER_LINE_HEIGHT_KEY = floatPreferencesKey("reader_line_height")
        private val READER_BRIGHTNESS_KEY = floatPreferencesKey("reader_brightness")
        private val READER_SCROLL_MODE_KEY = booleanPreferencesKey("reader_scroll_mode")
        private val READER_SYSTEM_BRIGHTNESS_KEY = booleanPreferencesKey("reader_system_brightness")
        private val READER_THEME_KEY = stringPreferencesKey("reader_theme")
        private val READER_TAP_PAGE_TURN_KEY = booleanPreferencesKey("reader_tap_page_turn")
        private val READER_PAGE_TURN_MODE_KEY = stringPreferencesKey("reader_page_turn_mode")
        private val READER_PAGE_MARGINS_KEY = floatPreferencesKey("reader_page_margins")
        private val READER_PAGE_MARGIN_HORIZONTAL_KEY = floatPreferencesKey("reader_page_margin_horizontal")
        private val READER_PAGE_MARGIN_VERTICAL_KEY = floatPreferencesKey("reader_page_margin_vertical")
        private val READER_PARAGRAPH_SPACING_KEY = floatPreferencesKey("reader_paragraph_spacing")
        private val READER_LETTER_SPACING_KEY = floatPreferencesKey("reader_letter_spacing")
        private val READER_PUBLISHER_STYLES_KEY = booleanPreferencesKey("reader_publisher_styles")
        private val READER_FORCE_PUBLISHER_FONTS_KEY = booleanPreferencesKey("reader_force_publisher_fonts")
        private val READER_KEEP_SCREEN_ON_KEY = booleanPreferencesKey("reader_keep_screen_on")
        private val READER_HIDE_STATUS_BAR_KEY = booleanPreferencesKey("reader_hide_status_bar_in_reader")
        private val READER_HIDE_TIME_KEY = booleanPreferencesKey("reader_hide_time_in_reader")
        private val READER_HIDE_CHAPTER_PATH_KEY = booleanPreferencesKey("reader_hide_chapter_path_in_reader")
        private val READER_AUTO_REFRESH_HOME_ON_ENTER_KEY = booleanPreferencesKey("reader_auto_refresh_home_on_enter")
        private val READER_PAGE_MARGIN_SEPARATE_MODE_KEY = booleanPreferencesKey("reader_page_margin_separate_mode")
        private val SHOW_TAB_LABEL_KEY = booleanPreferencesKey("show_tab_label")
        private val READER_TOOLBAR_LABELS_KEY = booleanPreferencesKey("reader_toolbar_labels")
        private val READER_HIDE_TOOLBAR_LABELS_KEY = booleanPreferencesKey("reader_hide_toolbar_labels")
        private val READER_PAGE_ANIMATION_KEY = stringPreferencesKey("reader_page_animation")
        private val READER_SCROLL_TAP_PAGE_TURN_KEY = stringPreferencesKey("reader_scroll_tap_page_turn_v2")
        private val READER_SCROLL_TAP_PAGE_TURN_OLD_KEY = booleanPreferencesKey("reader_scroll_tap_page_turn")
        private val READER_SCROLL_KEEP_LINE_KEY = booleanPreferencesKey("reader_scroll_keep_line")
        private val READER_VOLUME_KEY_PAGE_TURN_KEY = booleanPreferencesKey("reader_volume_key_page_turn")
        private val READER_FORCE_TAP_ANIMATION_KEY = booleanPreferencesKey("reader_force_tap_animation")
        private val READER_BACKGROUND_COLOR_KEY = stringPreferencesKey("reader_background_color")
        private val READER_TEXT_COLOR_KEY = stringPreferencesKey("reader_text_color")
        private val READER_CUSTOM_THEME_ENABLED_KEY = booleanPreferencesKey("reader_custom_theme_enabled")
        private val TTS_SPEECH_RATE_KEY = floatPreferencesKey("tts_speech_rate")
        private val TTS_PITCH_KEY = floatPreferencesKey("tts_pitch")
        private val TTS_VOICE_NAME_KEY = stringPreferencesKey("tts_voice_name")
        private val TTS_SLEEP_ENABLED_KEY = booleanPreferencesKey("tts_sleep_enabled")
        private val TTS_SLEEP_MINUTES_KEY = intPreferencesKey("tts_sleep_minutes")
        private val LIBRARY_SERVERS_KEY = stringPreferencesKey("library_servers_json")
        private val ACTIVE_LIBRARY_SERVER_ID_KEY = stringPreferencesKey("active_library_server_id")
        private val START_TAB_KEY = stringPreferencesKey("start_tab")
        const val DEFAULT_CACHE_LIMIT_MB = 1024
    }

    private val gson = Gson()
    private val serverListType = object : TypeToken<List<LibraryServerConfig>>() {}.type

    val serverUrl: Flow<String> = context.dataStore.data.map { prefs ->
        activeServerFromPrefs(prefs).baseUrl
    }

    val serverName: Flow<String> = context.dataStore.data.map { prefs ->
        activeServerFromPrefs(prefs).name.ifBlank { prefs[SERVER_NAME_KEY] ?: "" }
    }

    val serverPrivateMode: Flow<Boolean> = context.dataStore.data.map { prefs ->
        activeServerFromPrefs(prefs).isPrivateMode
    }

    val serverSiteAccessCode: Flow<String> = context.dataStore.data.map { prefs ->
        activeServerFromPrefs(prefs).siteAccessCode.orEmpty()
    }

    val username: Flow<String> = context.dataStore.data.map { prefs ->
        activeServerFromPrefs(prefs).username.ifBlank { prefs[USERNAME_KEY] ?: "" }
    }

    val nickname: Flow<String> = context.dataStore.data.map { prefs ->
        activeServerFromPrefs(prefs).nickname.ifBlank { prefs[NICKNAME_KEY] ?: "" }
    }

    val loginMode: Flow<String> = context.dataStore.data.map { prefs ->
        activeServerFromPrefs(prefs).loginMode.ifBlank { prefs[LOGIN_MODE_KEY] ?: "" }
    }

    val password: Flow<String> = context.dataStore.data.map { prefs ->
        activeServerFromPrefs(prefs).password
    }

    val accessCode: Flow<String> = context.dataStore.data.map { prefs ->
        activeServerFromPrefs(prefs).accessCode
    }

    val userId: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[USER_ID_KEY] ?: 0
    }

val isLoggedIn: Flow<Boolean> = context.dataStore.data.map { prefs ->
        if (prefs[SKIP_AUTH_KEY] == true) return@map true
        val mode = activeServerFromPrefs(prefs).loginMode.ifBlank { prefs[LOGIN_MODE_KEY] ?: "" }
        mode.isNotEmpty()
    }

    val skipAuth: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[SKIP_AUTH_KEY] ?: false
    }

    val homeTabs: Flow<String> = context.dataStore.data.map { prefs ->
        when (prefs[HOME_TABS_KEY]) {
            HOME_TABS_LIBRARY, HOME_TABS_LOCAL, HOME_TABS_BOTH -> prefs[HOME_TABS_KEY] ?: HOME_TABS_BOTH
            else -> HOME_TABS_BOTH
        }
    }

    val showTabLabel: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[SHOW_TAB_LABEL_KEY] ?: true
    }

    val readerToolbarLabels: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[READER_TOOLBAR_LABELS_KEY] ?: false
    }

    val readerHideToolbarLabels: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[READER_HIDE_TOOLBAR_LABELS_KEY] ?: true
    }

    val libraryServers: Flow<List<LibraryServerConfig>> = context.dataStore.data.map { prefs ->
        serversFromPrefs(prefs)
    }

    val activeLibraryServerId: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[ACTIVE_LIBRARY_SERVER_ID_KEY] ?: DEFAULT_SERVER_ID
    }

    val activeLibraryServer: Flow<LibraryServerConfig> = context.dataStore.data.map { prefs ->
        activeServerFromPrefs(prefs)
    }

        val startTab: Flow<String> = context.dataStore.data.map { prefs ->
        when (val value = prefs[START_TAB_KEY]) {
            START_TAB_RECENT, START_TAB_LIBRARY, START_TAB_LOCAL, START_TAB_SETTINGS -> value
            else -> START_TAB_RECENT
        }
    }

    val themeMode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[THEME_MODE_KEY] ?: THEME_AUTO
    }

    val dayThemePreset: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[DAY_THEME_PRESET_KEY]?.takeIf { value -> ThemePresets.day.any { it.id == value } } ?: ThemePresets.DAY_SYSTEM
    }

    val nightThemePreset: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[NIGHT_THEME_PRESET_KEY]?.takeIf { value -> ThemePresets.night.any { it.id == value } } ?: ThemePresets.NIGHT_CHARCOAL
    }

    val appAccent: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[APP_ACCENT_KEY]?.takeIf { value -> ThemePresets.accents.any { it.id == value } } ?: ThemePresets.accents.first().id
    }

    val dayCustomBackground: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[DAY_CUSTOM_BACKGROUND_KEY]?.toLongOrNull() ?: ThemePresets.day.first { it.id == ThemePresets.DAY_CUSTOM }.background
    }

    val dayCustomText: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[DAY_CUSTOM_TEXT_KEY]?.toLongOrNull() ?: ThemePresets.day.first { it.id == ThemePresets.DAY_CUSTOM }.text
    }

    val readerCustomBackground: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[READER_BACKGROUND_COLOR_KEY]?.toLongOrNull() ?: 0L
    }

    val readerCustomText: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[READER_TEXT_COLOR_KEY]?.toLongOrNull() ?: 0L
    }

    val readerCustomThemeEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[READER_CUSTOM_THEME_ENABLED_KEY] ?: false
    }

    val readerCacheLimitMb: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[READER_CACHE_LIMIT_MB_KEY] ?: DEFAULT_CACHE_LIMIT_MB
    }

    val readerAutoCacheOnWifi: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[READER_AUTO_CACHE_ON_WIFI_KEY] ?: false
    }

    val readerFontScale: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[READER_FONT_SCALE_KEY] ?: 1.0f
    }

    val readerFontFamily: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[READER_FONT_FAMILY_KEY] ?: "default"
    }

    val readerLineHeight: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[READER_LINE_HEIGHT_KEY] ?: 1.5f
    }

    val readerBrightness: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[READER_BRIGHTNESS_KEY] ?: 1.0f
    }

    val readerScrollMode: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[READER_SCROLL_MODE_KEY] ?: false
    }

    val readerUseSystemBrightness: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[READER_SYSTEM_BRIGHTNESS_KEY] ?: true
    }

    val readerTheme: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[READER_THEME_KEY] ?: "system"
    }

    val readerTapPageTurn: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[READER_TAP_PAGE_TURN_KEY] ?: true
    }

    val readerPageTurnMode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[READER_PAGE_TURN_MODE_KEY] ?: "inverted_l"
    }

    val readerPageMargins: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[READER_PAGE_MARGINS_KEY] ?: 1.0f
    }

    val readerPageMarginHorizontal: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[READER_PAGE_MARGIN_HORIZONTAL_KEY] ?: prefs[READER_PAGE_MARGINS_KEY] ?: 1.0f
    }

    val readerPageMarginVertical: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[READER_PAGE_MARGIN_VERTICAL_KEY] ?: prefs[READER_PAGE_MARGINS_KEY] ?: 1.0f
    }

    val readerParagraphSpacing: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[READER_PARAGRAPH_SPACING_KEY] ?: 1.0f
    }

    val readerLetterSpacing: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[READER_LETTER_SPACING_KEY] ?: 0f
    }

    val readerPublisherStyles: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[READER_PUBLISHER_STYLES_KEY] ?: true
    }

    val readerForcePublisherFonts: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[READER_FORCE_PUBLISHER_FONTS_KEY] ?: false
    }

    val readerKeepScreenOn: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[READER_KEEP_SCREEN_ON_KEY] ?: false
    }

    val readerHideStatusBarInReader: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[READER_HIDE_STATUS_BAR_KEY] ?: false
    }

    val readerHideTimeInReader: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[READER_HIDE_TIME_KEY] ?: false
    }

    val readerHideChapterPathInReader: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[READER_HIDE_CHAPTER_PATH_KEY] ?: false
    }

    val readerAutoRefreshHomeOnEnter: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[READER_AUTO_REFRESH_HOME_ON_ENTER_KEY] ?: false
    }

    val readerPageMarginSeparateMode: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[READER_PAGE_MARGIN_SEPARATE_MODE_KEY] ?: false
    }

    val readerPageAnimation: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[READER_PAGE_ANIMATION_KEY] ?: "smooth"
    }

    val readerForceTapAnimation: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[READER_FORCE_TAP_ANIMATION_KEY] ?: true
    }

    val readerScrollTapPageTurn: Flow<ReaderScrollTapSpeed> = context.dataStore.data.map { prefs ->
        val newValue = prefs[READER_SCROLL_TAP_PAGE_TURN_KEY] as? String
        when (newValue) {
            "off" -> ReaderScrollTapSpeed.OFF
            "fast" -> ReaderScrollTapSpeed.FAST
            "medium" -> ReaderScrollTapSpeed.MEDIUM
            "slow" -> ReaderScrollTapSpeed.SLOW
            else -> {
                val oldValue = prefs[READER_SCROLL_TAP_PAGE_TURN_OLD_KEY] as? Boolean
                when (oldValue) {
                    true -> ReaderScrollTapSpeed.MEDIUM
                    false -> ReaderScrollTapSpeed.OFF
                    null -> ReaderScrollTapSpeed.MEDIUM
                }
            }
        }
    }

    val readerScrollKeepLine: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[READER_SCROLL_KEEP_LINE_KEY] ?: true
    }

    val readerVolumeKeyPageTurn: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[READER_VOLUME_KEY_PAGE_TURN_KEY] ?: false
    }

    val ttsSpeechRate: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[TTS_SPEECH_RATE_KEY] ?: 1.0f
    }

    val ttsPitch: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[TTS_PITCH_KEY] ?: 1.0f
    }

    val ttsVoiceName: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[TTS_VOICE_NAME_KEY] ?: ""
    }

    val ttsSleepEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[TTS_SLEEP_ENABLED_KEY] ?: true
    }

    val ttsSleepMinutes: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[TTS_SLEEP_MINUTES_KEY] ?: 30
    }

    suspend fun saveServerUrl(url: String) {
        context.dataStore.edit { prefs ->
            val normalized = normalizeUrl(url)
            val activeId = prefs[ACTIVE_LIBRARY_SERVER_ID_KEY] ?: DEFAULT_SERVER_ID
            val servers = serversFromPrefs(prefs).map { server ->
                if (server.id == activeId) {
                    server.copy(baseUrl = normalized, updatedAt = System.currentTimeMillis())
                } else {
                    server
                }
            }
            prefs[SERVER_URL_KEY] = normalized
            prefs[LIBRARY_SERVERS_KEY] = gson.toJson(servers)
        }
    }

    suspend fun saveServerName(name: String) {
        context.dataStore.edit { prefs ->
            val activeId = prefs[ACTIVE_LIBRARY_SERVER_ID_KEY] ?: DEFAULT_SERVER_ID
            val servers = serversFromPrefs(prefs).map { server ->
                if (server.id == activeId) {
                    server.copy(name = name, updatedAt = System.currentTimeMillis())
                } else {
                    server
                }
            }
            prefs[SERVER_NAME_KEY] = name
            prefs[LIBRARY_SERVERS_KEY] = gson.toJson(servers)
        }
    }

    suspend fun saveServerPrivacy(isPrivateMode: Boolean, siteAccessCode: String) {
        context.dataStore.edit { prefs ->
            val activeId = prefs[ACTIVE_LIBRARY_SERVER_ID_KEY] ?: DEFAULT_SERVER_ID
            val servers = serversFromPrefs(prefs).map { server ->
                if (server.id == activeId) {
                    server.copy(
                        isPrivateMode = isPrivateMode,
                        siteAccessCode = if (isPrivateMode) siteAccessCode else "",
                        updatedAt = System.currentTimeMillis()
                    )
                } else {
                    server
                }
            }
            prefs[LIBRARY_SERVERS_KEY] = gson.toJson(servers)
        }
    }

    suspend fun saveServerType(serverType: String, basicUser: String, basicPass: String) {
        context.dataStore.edit { prefs ->
            val activeId = prefs[ACTIVE_LIBRARY_SERVER_ID_KEY] ?: DEFAULT_SERVER_ID
            val servers = serversFromPrefs(prefs).map { server ->
                if (server.id == activeId) {
                    server.copy(
                        serverType = serverType,
                        httpBasicUser = if (serverType == "opds") basicUser else "",
                        httpBasicPass = if (serverType == "opds") basicPass else "",
                        updatedAt = System.currentTimeMillis()
                    )
                } else {
                    server
                }
            }
            prefs[LIBRARY_SERVERS_KEY] = gson.toJson(servers)
        }
    }

    val activeServerType: Flow<String> = context.dataStore.data.map { prefs ->
        activeServerFromPrefs(prefs).serverType ?: "talebook"
    }

    val activeHttpBasicUser: Flow<String> = context.dataStore.data.map { prefs ->
        activeServerFromPrefs(prefs).httpBasicUser.orEmpty()
    }

    val activeHttpBasicPass: Flow<String> = context.dataStore.data.map { prefs ->
        activeServerFromPrefs(prefs).httpBasicPass.orEmpty()
    }

    suspend fun saveLoginInfo(mode: String, username: String, nickname: String) {
        context.dataStore.edit { prefs ->
            val activeId = prefs[ACTIVE_LIBRARY_SERVER_ID_KEY] ?: DEFAULT_SERVER_ID
            val servers = serversFromPrefs(prefs).map { server ->
                if (server.id == activeId) {
                    server.copy(
                        loginMode = mode,
                        username = username,
                        nickname = nickname,
                        updatedAt = System.currentTimeMillis()
                    )
                } else {
                    server
                }
            }
            prefs[LOGIN_MODE_KEY] = mode
            prefs[USERNAME_KEY] = username
            prefs[NICKNAME_KEY] = nickname
            prefs[LIBRARY_SERVERS_KEY] = gson.toJson(servers)
        }
    }

    suspend fun saveLoginSecret(mode: String, username: String, password: String, accessCode: String, nickname: String = username) {
        context.dataStore.edit { prefs ->
            val activeId = prefs[ACTIVE_LIBRARY_SERVER_ID_KEY] ?: DEFAULT_SERVER_ID
            val servers = serversFromPrefs(prefs).map { server ->
                if (server.id == activeId) {
                    server.copy(
                        loginMode = mode,
                        username = username,
                        password = if (mode == "password") password else "",
                        accessCode = if (mode == "code") accessCode else "",
                        nickname = nickname,
                        updatedAt = System.currentTimeMillis()
                    )
                } else {
                    server
                }
            }
            prefs[LIBRARY_SERVERS_KEY] = gson.toJson(servers)
        }
    }

    suspend fun clearLogin() {
        context.dataStore.edit { prefs ->
            val activeId = prefs[ACTIVE_LIBRARY_SERVER_ID_KEY] ?: DEFAULT_SERVER_ID
            val servers = serversFromPrefs(prefs).map { server ->
                if (server.id == activeId) {
                    server.copy(loginMode = "", nickname = "", updatedAt = System.currentTimeMillis())
                } else {
                    server
                }
            }
            prefs.remove(LOGIN_MODE_KEY)
            prefs.remove(USERNAME_KEY)
            prefs.remove(NICKNAME_KEY)
            prefs[LIBRARY_SERVERS_KEY] = gson.toJson(servers)
        }
    }

    suspend fun saveStartTab(tab: String) {
        val normalized = when (tab) {
            START_TAB_RECENT, START_TAB_LIBRARY, START_TAB_LOCAL, START_TAB_SETTINGS -> tab
            else -> START_TAB_RECENT
        }
        context.dataStore.edit { prefs -> prefs[START_TAB_KEY] = normalized }
    }

    suspend fun setActiveLibraryServer(serverId: String) {
        context.dataStore.edit { prefs ->
            val server = serversFromPrefs(prefs).firstOrNull { it.id == serverId } ?: return@edit
            prefs[ACTIVE_LIBRARY_SERVER_ID_KEY] = server.id
            prefs[SERVER_URL_KEY] = server.baseUrl
            if (server.loginMode.isBlank()) {
                prefs.remove(LOGIN_MODE_KEY)
                prefs.remove(USERNAME_KEY)
                prefs.remove(NICKNAME_KEY)
            } else {
                prefs[LOGIN_MODE_KEY] = server.loginMode
                prefs[USERNAME_KEY] = server.username
                prefs[NICKNAME_KEY] = server.nickname
            }
            RetrofitClient.updateBaseUrl(server.baseUrl)
        }
    }

    suspend fun upsertLibraryServer(server: LibraryServerConfig): String {
        val id = server.id.ifBlank { UUID.randomUUID().toString() }
        val now = System.currentTimeMillis()
        val normalized = server.copy(
            id = id,
            name = server.name.ifBlank { server.baseUrl }.trim().take(80),
            baseUrl = normalizeUrl(server.baseUrl),
            createdAt = if (server.createdAt > 0) server.createdAt else now,
            updatedAt = now
        )
        context.dataStore.edit { prefs ->
            val existing = serversFromPrefs(prefs).filterNot { it.id == id }
            prefs[LIBRARY_SERVERS_KEY] = gson.toJson(existing + normalized)
            if (prefs[ACTIVE_LIBRARY_SERVER_ID_KEY].isNullOrBlank()) {
                prefs[ACTIVE_LIBRARY_SERVER_ID_KEY] = id
                prefs[SERVER_URL_KEY] = normalized.baseUrl
            }
        }
        return id
    }

    suspend fun deleteLibraryServer(serverId: String) {
        if (serverId == DEFAULT_SERVER_ID) return
        context.dataStore.edit { prefs ->
            val remaining = serversFromPrefs(prefs).filterNot { it.id == serverId }
            val safeRemaining = remaining.ifEmpty { listOf(defaultServer(prefs)) }
            val activeId = prefs[ACTIVE_LIBRARY_SERVER_ID_KEY] ?: DEFAULT_SERVER_ID
            prefs[LIBRARY_SERVERS_KEY] = gson.toJson(safeRemaining)
            if (activeId == serverId) {
                val fallback = safeRemaining.first()
                prefs[ACTIVE_LIBRARY_SERVER_ID_KEY] = fallback.id
                prefs[SERVER_URL_KEY] = fallback.baseUrl
                RetrofitClient.updateBaseUrl(fallback.baseUrl)
            }
        }
    }

    suspend fun saveThemeMode(mode: String) {
        val normalized = when (mode) {
            THEME_LIGHT, THEME_DARK, THEME_AUTO -> mode
            else -> THEME_AUTO
        }
        context.dataStore.edit { prefs ->
            prefs[THEME_MODE_KEY] = normalized
        }
    }

    suspend fun saveDayThemePreset(preset: String) {
        val normalized = preset.takeIf { value -> ThemePresets.day.any { it.id == value } } ?: ThemePresets.DAY_SYSTEM
        val palette = ThemePresets.day.first { it.id == normalized }
        context.dataStore.edit { prefs ->
            prefs[DAY_THEME_PRESET_KEY] = normalized
            prefs[READER_THEME_KEY] = normalized.toLegacyReaderTheme(false)
            prefs[READER_BACKGROUND_COLOR_KEY] = (palette.background and 0xFFFFFF).toString()
            prefs[READER_TEXT_COLOR_KEY] = (palette.text and 0xFFFFFF).toString()
            prefs[READER_CUSTOM_THEME_ENABLED_KEY] = true
        }
    }

    suspend fun saveNightThemePreset(preset: String) {
        val normalized = preset.takeIf { value -> ThemePresets.night.any { it.id == value } } ?: ThemePresets.NIGHT_CHARCOAL
        val palette = ThemePresets.night.first { it.id == normalized }
        context.dataStore.edit { prefs ->
            prefs[NIGHT_THEME_PRESET_KEY] = normalized
            prefs[READER_THEME_KEY] = normalized.toLegacyReaderTheme(true)
            prefs[READER_BACKGROUND_COLOR_KEY] = (palette.background and 0xFFFFFF).toString()
            prefs[READER_TEXT_COLOR_KEY] = (palette.text and 0xFFFFFF).toString()
            prefs[READER_CUSTOM_THEME_ENABLED_KEY] = true
        }
    }

    suspend fun saveAppAccent(accent: String) {
        val normalized = accent.takeIf { value -> ThemePresets.accents.any { it.id == value } } ?: ThemePresets.accents.first().id
        context.dataStore.edit { prefs -> prefs[APP_ACCENT_KEY] = normalized }
    }

    suspend fun saveDayCustomColors(background: Long, text: Long) {
        saveReaderCustomColors(background, text, enabled = true)
        context.dataStore.edit { prefs ->
            prefs[DAY_THEME_PRESET_KEY] = ThemePresets.DAY_CUSTOM
            prefs[READER_THEME_KEY] = "custom"
            prefs[DAY_CUSTOM_BACKGROUND_KEY] = (background and 0xFFFFFFFFL).toString()
            prefs[DAY_CUSTOM_TEXT_KEY] = (text and 0xFFFFFFFFL).toString()
        }
    }

    suspend fun saveReaderCustomColors(background: Long, text: Long, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[READER_BACKGROUND_COLOR_KEY] = (background and 0xFFFFFF).toString()
            prefs[READER_TEXT_COLOR_KEY] = (text and 0xFFFFFF).toString()
            prefs[READER_CUSTOM_THEME_ENABLED_KEY] = enabled
        }
    }

    suspend fun saveSkipAuth(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[SKIP_AUTH_KEY] = enabled
        }
    }

    suspend fun saveHomeTabs(value: String) {
        val normalized = when (value) {
            HOME_TABS_LIBRARY, HOME_TABS_LOCAL, HOME_TABS_BOTH -> value
            else -> HOME_TABS_BOTH
        }
        context.dataStore.edit { prefs ->
            prefs[HOME_TABS_KEY] = normalized
        }
    }

    suspend fun saveReaderHideStatusBarInReader(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[READER_HIDE_STATUS_BAR_KEY] = enabled
        }
    }

    suspend fun saveReaderHideTimeInReader(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[READER_HIDE_TIME_KEY] = enabled
        }
    }

    suspend fun saveReaderHideChapterPathInReader(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[READER_HIDE_CHAPTER_PATH_KEY] = enabled
        }
    }

    suspend fun saveReaderAutoRefreshHomeOnEnter(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[READER_AUTO_REFRESH_HOME_ON_ENTER_KEY] = enabled
        }
    }

    suspend fun saveReaderPageMarginSeparateMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[READER_PAGE_MARGIN_SEPARATE_MODE_KEY] = enabled
        }
    }

    suspend fun saveShowTabLabel(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[SHOW_TAB_LABEL_KEY] = enabled
        }
    }

    suspend fun saveReaderToolbarLabels(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[READER_TOOLBAR_LABELS_KEY] = enabled
        }
    }

    suspend fun saveReaderHideToolbarLabels(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[READER_HIDE_TOOLBAR_LABELS_KEY] = enabled
        }
    }

    suspend fun saveReaderCacheLimitMb(limitMb: Int) {
        context.dataStore.edit { prefs ->
            prefs[READER_CACHE_LIMIT_MB_KEY] = limitMb.coerceIn(128, 10240)
        }
    }

    suspend fun saveReaderAutoCacheOnWifi(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[READER_AUTO_CACHE_ON_WIFI_KEY] = enabled
        }
    }

    suspend fun saveReaderDisplaySettings(
        fontScale: Float,
        fontFamily: String,
        lineHeight: Float,
        brightness: Float,
        scrollMode: Boolean,
        useSystemBrightness: Boolean,
        theme: String,
        tapPageTurn: Boolean,
        pageTurnMode: String,
        pageMargins: Float,
        pageMarginHorizontal: Float = pageMargins,
        pageMarginVertical: Float = pageMargins,
        pageMarginSeparateMode: Boolean = false,
        paragraphSpacing: Float,
        letterSpacing: Float,
        publisherStyles: Boolean,
        forcePublisherFonts: Boolean,
        keepScreenOn: Boolean,
        pageAnimation: String,
        scrollTapPageTurn: ReaderScrollTapSpeed,
        scrollKeepLine: Boolean,
        volumeKeyPageTurn: Boolean,
        forceTapAnimation: Boolean
    ) {
        val horizontal = pageMarginHorizontal.coerceIn(0.5f, 3.0f)
        val vertical = pageMarginVertical.coerceIn(0.5f, 3.0f)
        context.dataStore.edit { prefs ->
            prefs[READER_FONT_SCALE_KEY] = fontScale.coerceIn(0.5f, 3.0f)
            prefs[READER_FONT_FAMILY_KEY] = when (fontFamily) {
                "default", "serif", "sans_serif", "monospace" -> fontFamily
                else -> "default"
            }
            prefs[READER_LINE_HEIGHT_KEY] = lineHeight.coerceIn(0.5f, 3.0f)
            prefs[READER_BRIGHTNESS_KEY] = brightness.coerceIn(0.0f, 1.0f)
            prefs[READER_SCROLL_MODE_KEY] = scrollMode
            prefs[READER_SYSTEM_BRIGHTNESS_KEY] = useSystemBrightness
            prefs[READER_THEME_KEY] = when (theme) {
                "system", "light", "sepia", "dark", "pink", "blue", "green", "custom" -> theme
                else -> "system"
            }
            prefs[READER_TAP_PAGE_TURN_KEY] = tapPageTurn
            prefs[READER_PAGE_TURN_MODE_KEY] = when (pageTurnMode) {
                "inverted_l", "left_right", "right_only", "disabled" -> pageTurnMode
                else -> "inverted_l"
            }
            prefs[READER_PAGE_MARGINS_KEY] = pageMargins.coerceIn(0.5f, 3.0f)
            prefs[READER_PAGE_MARGIN_HORIZONTAL_KEY] = horizontal
            prefs[READER_PAGE_MARGIN_VERTICAL_KEY] = vertical
            prefs[READER_PAGE_MARGIN_SEPARATE_MODE_KEY] = pageMarginSeparateMode
            prefs[READER_PARAGRAPH_SPACING_KEY] = paragraphSpacing.coerceIn(0.0f, 4.0f)
            prefs[READER_LETTER_SPACING_KEY] = letterSpacing.coerceIn(0f, 10f)
            prefs[READER_PUBLISHER_STYLES_KEY] = publisherStyles
            prefs[READER_FORCE_PUBLISHER_FONTS_KEY] = forcePublisherFonts
            prefs[READER_KEEP_SCREEN_ON_KEY] = keepScreenOn
            prefs[READER_PAGE_ANIMATION_KEY] = when (pageAnimation) {
                "smooth", "slide", "cover", "override", "none" -> pageAnimation
                else -> "smooth"
            }
            prefs.remove(READER_SCROLL_TAP_PAGE_TURN_OLD_KEY)
            prefs[READER_SCROLL_TAP_PAGE_TURN_KEY] = when (scrollTapPageTurn) {
                ReaderScrollTapSpeed.OFF -> "off"
                ReaderScrollTapSpeed.FAST -> "fast"
                ReaderScrollTapSpeed.MEDIUM -> "medium"
                ReaderScrollTapSpeed.SLOW -> "slow"
            }
            prefs[READER_SCROLL_KEEP_LINE_KEY] = scrollKeepLine
            prefs[READER_VOLUME_KEY_PAGE_TURN_KEY] = volumeKeyPageTurn
            prefs[READER_FORCE_TAP_ANIMATION_KEY] = forceTapAnimation
        }
    }

    suspend fun migrateLegacyMargins() {
        context.dataStore.edit { prefs ->
            val hasH = prefs[READER_PAGE_MARGIN_HORIZONTAL_KEY] != null
            val hasV = prefs[READER_PAGE_MARGIN_VERTICAL_KEY] != null
            val legacy = prefs[READER_PAGE_MARGINS_KEY]
            if (!hasH && legacy != null) {
                prefs[READER_PAGE_MARGIN_HORIZONTAL_KEY] = legacy
            }
            if (!hasV && legacy != null) {
                prefs[READER_PAGE_MARGIN_VERTICAL_KEY] = legacy
            }
        }
    }

    suspend fun saveTtsSettings(
        speechRate: Float,
        pitch: Float,
        voiceName: String,
        sleepEnabled: Boolean,
        sleepMinutes: Int
    ) {
        context.dataStore.edit { prefs ->
            prefs[TTS_SPEECH_RATE_KEY] = speechRate.coerceIn(0.5f, 2.0f)
            prefs[TTS_PITCH_KEY] = pitch.coerceIn(0.5f, 2.0f)
            prefs[TTS_VOICE_NAME_KEY] = voiceName
            prefs[TTS_SLEEP_ENABLED_KEY] = sleepEnabled
            prefs[TTS_SLEEP_MINUTES_KEY] = sleepMinutes.coerceIn(5, 180)
        }
    }

    private fun normalizeUrl(url: String): String {
        return url.trim().trimEnd('/')
    }

    private fun defaultServer(prefs: Preferences): LibraryServerConfig {
        val url = normalizeUrl(prefs[SERVER_URL_KEY] ?: "")
        return LibraryServerConfig(
            id = DEFAULT_SERVER_ID,
            name = "默认书库",
            baseUrl = url,
            loginMode = prefs[LOGIN_MODE_KEY] ?: "",
            username = prefs[USERNAME_KEY] ?: "",
            nickname = prefs[NICKNAME_KEY] ?: ""
        )
    }

    private fun serversFromPrefs(prefs: Preferences): List<LibraryServerConfig> {
        val raw = prefs[LIBRARY_SERVERS_KEY].orEmpty()
        val parsed = if (raw.isBlank()) {
            emptyList()
        } else {
            runCatching { gson.fromJson<List<LibraryServerConfig>>(raw, serverListType) }.getOrNull().orEmpty()
        }
        val cleaned = parsed
            .filter { it.baseUrl.isNotBlank() }
            .map { it.copy(baseUrl = normalizeUrl(it.baseUrl)) }
        return if (cleaned.any { it.id == DEFAULT_SERVER_ID }) cleaned else listOf(defaultServer(prefs)) + cleaned
    }

    private fun activeServerFromPrefs(prefs: Preferences): LibraryServerConfig {
        val activeId = prefs[ACTIVE_LIBRARY_SERVER_ID_KEY] ?: DEFAULT_SERVER_ID
        return serversFromPrefs(prefs).firstOrNull { it.id == activeId }
            ?: serversFromPrefs(prefs).first()
    }

    private fun String.toLegacyReaderTheme(dark: Boolean): String = when {
        dark -> "dark"
        this == ThemePresets.DAY_EYE -> "sepia"
        this == ThemePresets.DAY_PINK -> "pink"
        this == ThemePresets.DAY_BLUE -> "blue"
        this == ThemePresets.DAY_GREEN -> "green"
        this == ThemePresets.DAY_CUSTOM -> "custom"
        else -> "light"
    }
}
