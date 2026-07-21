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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    companion object {
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
        const val THEME_AUTO = "auto"
        const val READER_LOCAL = "local"
        const val READER_ONLINE = "online"

        private val SERVER_URL_KEY = stringPreferencesKey("server_url")
        private val USERNAME_KEY = stringPreferencesKey("username")
        private val NICKNAME_KEY = stringPreferencesKey("nickname")
        private val LOGIN_MODE_KEY = stringPreferencesKey("login_mode")  // "code" / "password" / ""
        private val USER_ID_KEY = intPreferencesKey("user_id")
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")  // "light" / "dark" / "auto"
        private val READER_MODE_KEY = stringPreferencesKey("reader_mode")  // "local" / "online"
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
        private val READER_PARAGRAPH_SPACING_KEY = floatPreferencesKey("reader_paragraph_spacing")
        private val READER_PUBLISHER_STYLES_KEY = booleanPreferencesKey("reader_publisher_styles")
        private val READER_KEEP_SCREEN_ON_KEY = booleanPreferencesKey("reader_keep_screen_on")
        private val READER_PAGE_ANIMATION_KEY = stringPreferencesKey("reader_page_animation")
        private val READER_SCROLL_TAP_PAGE_TURN_KEY = booleanPreferencesKey("reader_scroll_tap_page_turn")
        private val READER_SCROLL_KEEP_LINE_KEY = booleanPreferencesKey("reader_scroll_keep_line")
        private val READER_VOLUME_KEY_PAGE_TURN_KEY = booleanPreferencesKey("reader_volume_key_page_turn")
        private val TTS_SPEECH_RATE_KEY = floatPreferencesKey("tts_speech_rate")
        private val TTS_PITCH_KEY = floatPreferencesKey("tts_pitch")
        private val TTS_VOICE_NAME_KEY = stringPreferencesKey("tts_voice_name")
        private val TTS_SLEEP_ENABLED_KEY = booleanPreferencesKey("tts_sleep_enabled")
        private val TTS_SLEEP_MINUTES_KEY = intPreferencesKey("tts_sleep_minutes")
        private const val DEFAULT_URL = "https://book.liufenyi.xyz:9973"
        const val DEFAULT_CACHE_LIMIT_MB = 1024
    }

    val serverUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[SERVER_URL_KEY] ?: DEFAULT_URL
    }

    val username: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[USERNAME_KEY] ?: ""
    }

    val nickname: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[NICKNAME_KEY] ?: ""
    }

    val loginMode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[LOGIN_MODE_KEY] ?: ""
    }

    val userId: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[USER_ID_KEY] ?: 0
    }

    val isLoggedIn: Flow<Boolean> = context.dataStore.data.map { prefs ->
        val mode = prefs[LOGIN_MODE_KEY] ?: ""
        mode.isNotEmpty()
    }

    val themeMode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[THEME_MODE_KEY] ?: THEME_AUTO
    }

    val readerMode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[READER_MODE_KEY] ?: READER_LOCAL
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

    val readerParagraphSpacing: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[READER_PARAGRAPH_SPACING_KEY] ?: 1.0f
    }

    val readerPublisherStyles: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[READER_PUBLISHER_STYLES_KEY] ?: true
    }

    val readerKeepScreenOn: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[READER_KEEP_SCREEN_ON_KEY] ?: false
    }

    val readerPageAnimation: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[READER_PAGE_ANIMATION_KEY] ?: "smooth"
    }

    val readerScrollTapPageTurn: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[READER_SCROLL_TAP_PAGE_TURN_KEY] ?: true
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
            prefs[SERVER_URL_KEY] = url
        }
    }

    suspend fun saveLoginInfo(mode: String, username: String, nickname: String) {
        context.dataStore.edit { prefs ->
            prefs[LOGIN_MODE_KEY] = mode
            prefs[USERNAME_KEY] = username
            prefs[NICKNAME_KEY] = nickname
        }
    }

    suspend fun clearLogin() {
        context.dataStore.edit { prefs ->
            prefs.remove(LOGIN_MODE_KEY)
            prefs.remove(USERNAME_KEY)
            prefs.remove(NICKNAME_KEY)
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

    suspend fun saveReaderMode(mode: String) {
        val normalized = when (mode) {
            READER_LOCAL, READER_ONLINE -> mode
            else -> READER_LOCAL
        }
        context.dataStore.edit { prefs ->
            prefs[READER_MODE_KEY] = normalized
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
        paragraphSpacing: Float,
        publisherStyles: Boolean,
        keepScreenOn: Boolean,
        pageAnimation: String,
        scrollTapPageTurn: Boolean,
        scrollKeepLine: Boolean,
        volumeKeyPageTurn: Boolean
    ) {
        context.dataStore.edit { prefs ->
            prefs[READER_FONT_SCALE_KEY] = fontScale.coerceIn(0.7f, 1.8f)
            prefs[READER_FONT_FAMILY_KEY] = when (fontFamily) {
                "default", "serif", "sans_serif", "monospace" -> fontFamily
                else -> "default"
            }
            prefs[READER_LINE_HEIGHT_KEY] = lineHeight.coerceIn(1.0f, 2.4f)
            prefs[READER_BRIGHTNESS_KEY] = brightness.coerceIn(0.3f, 1.0f)
            prefs[READER_SCROLL_MODE_KEY] = scrollMode
            prefs[READER_SYSTEM_BRIGHTNESS_KEY] = useSystemBrightness
            prefs[READER_THEME_KEY] = when (theme) {
                "system", "light", "sepia", "dark" -> theme
                else -> "system"
            }
            prefs[READER_TAP_PAGE_TURN_KEY] = tapPageTurn
            prefs[READER_PAGE_TURN_MODE_KEY] = when (pageTurnMode) {
                "inverted_l", "left_right", "right_only", "disabled" -> pageTurnMode
                else -> "inverted_l"
            }
            prefs[READER_PAGE_MARGINS_KEY] = pageMargins.coerceIn(0.5f, 2.0f)
            prefs[READER_PARAGRAPH_SPACING_KEY] = paragraphSpacing.coerceIn(0.0f, 2.0f)
            prefs[READER_PUBLISHER_STYLES_KEY] = publisherStyles
            prefs[READER_KEEP_SCREEN_ON_KEY] = keepScreenOn
            prefs[READER_PAGE_ANIMATION_KEY] = when (pageAnimation) {
                "smooth", "slide", "cover", "none" -> pageAnimation
                else -> "smooth"
            }
            prefs[READER_SCROLL_TAP_PAGE_TURN_KEY] = scrollTapPageTurn
            prefs[READER_SCROLL_KEEP_LINE_KEY] = scrollKeepLine
            prefs[READER_VOLUME_KEY_PAGE_TURN_KEY] = volumeKeyPageTurn
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
}
