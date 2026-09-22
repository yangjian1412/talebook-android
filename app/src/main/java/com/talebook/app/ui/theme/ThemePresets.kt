package com.talebook.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.talebook.app.data.repository.SettingsRepository

data class ReaderThemePalette(
    val id: String,
    val label: String,
    val background: Long,
    val text: Long,
    val surface: Long,
    val primary: Long,
    val textureResName: String? = null
)

data class AppAccentPalette(
    val id: String,
    val label: String,
    val lightPrimary: Long,
    val darkPrimary: Long
)

object ThemePresets {
    const val DAY_SYSTEM = "day_system"
    const val DAY_EYE = "eye"
    const val DAY_PINK = "pink"
    const val DAY_BLUE = "blue"
    const val DAY_GREEN = "green"
    const val DAY_CUSTOM = "custom"

    const val NIGHT_CHARCOAL = "charcoal"
    const val NIGHT_SYSTEM = "night_system"
    const val NIGHT_WARM = "warm"
    const val NIGHT_BLUE = "midnight"
    const val NIGHT_GREEN = "forest"

    val accents = listOf(
        AppAccentPalette("blue", "深蓝", 0xFF2D5F9AL, 0xFFAFC7E8L),
        AppAccentPalette("red", "绯红", 0xFFC24747L, 0xFFF0B8B8L),
        AppAccentPalette("orange", "橙色", 0xFFC56A22L, 0xFFF2C29AL),
        AppAccentPalette("amber", "琥珀", 0xFF9E7B16L, 0xFFE5CF82L),
        AppAccentPalette("green", "松绿", 0xFF3E7C59L, 0xFFA9D4B9L),
        AppAccentPalette("teal", "青蓝", 0xFF3D7C7AL, 0xFFA9D8D4L),
        AppAccentPalette("magenta", "品红", 0xFFA4378AL, 0xFFE8B2D6L),
        AppAccentPalette("rose", "玫瑰", 0xFFC25A78L, 0xFFF0B8C8L)
    )

    fun accentPrimary(accent: String, dark: Boolean): Long {
        val palette = accents.firstOrNull { it.id == accent } ?: accents.first()
        return if (dark) palette.darkPrimary else palette.lightPrimary
    }

    val day = listOf(
        ReaderThemePalette(DAY_SYSTEM, "系统默认", 0xFFFFFFFFL, 0xFF000000L, 0xFFFFFFFFL, 0xFF1A73E8L),
        ReaderThemePalette(DAY_EYE, "米黄", 0xFFF3F0D7L, 0xFF2F3424L, 0xFFFCF8E3L, 0xFF687A2FL),
        ReaderThemePalette(DAY_PINK, "浅粉", 0xFFFFF0F3L, 0xFF4A2C35L, 0xFFFFF8F9L, 0xFFC65A78L),
        ReaderThemePalette(DAY_BLUE, "浅蓝", 0xFFF0F5FFL, 0xFF25364AL, 0xFFF8FBFFL, 0xFF4F75B8L),
        ReaderThemePalette(DAY_GREEN, "浅绿", 0xFFF0FAF0L, 0xFF263D2AL, 0xFFF8FFF8L, 0xFF4F8A59L),
        ReaderThemePalette(DAY_CUSTOM, "自定义", 0xFFF8F5EEL, 0xFF2B2B2BL, 0xFFFFFCF5L, 0xFF8A6B3EL)
    )

    val night = listOf(
        ReaderThemePalette(NIGHT_SYSTEM, "系统默认", 0xFF000000L, 0xFFFFFFFFL, 0xFF000000L, 0xFF9DB7F5L),
        ReaderThemePalette(NIGHT_CHARCOAL, "炭黑", 0xFF181A1BL, 0xFF969088L, 0xFF242729L, 0xFF9DB7F5L),
        ReaderThemePalette(NIGHT_WARM, "暖夜", 0xFF201B17L, 0xFFA89A8EL, 0xFF2B241EL, 0xFFD0A56AL),
        ReaderThemePalette(NIGHT_BLUE, "深蓝", 0xFF111A24L, 0xFFA1ADBAL, 0xFF1B2633L, 0xFF8AB4F8L),
        ReaderThemePalette(NIGHT_GREEN, "墨绿", 0xFF111D18L, 0xFF9BAEA3L, 0xFF1A2921L, 0xFF8FC9A8L)
    )

    fun isDark(mode: String, systemDark: Boolean): Boolean = when (mode) {
        SettingsRepository.THEME_LIGHT -> false
        SettingsRepository.THEME_DARK -> true
        else -> systemDark
    }

    fun palette(
        mode: String,
        systemDark: Boolean,
        dayPreset: String,
        nightPreset: String,
        customBackground: Long,
        customText: Long,
        accent: String = accents.first().id
    ): ReaderThemePalette {
        val primary = accentPrimary(accent, isDark(mode, systemDark))
        val dark = isDark(mode, systemDark)
        val palette = if (dark) {
            night.firstOrNull { it.id == nightPreset } ?: night.first()
        } else {
            val preset = day.firstOrNull { it.id == dayPreset } ?: day.first()
            if (preset.id == DAY_CUSTOM) {
                preset.copy(
                    background = customBackground.takeIf { it != 0L } ?: preset.background,
                    text = customText.takeIf { it != 0L } ?: preset.text,
                    surface = lighten(customBackground.takeIf { it != 0L } ?: preset.background)
                )
            } else {
                preset
            }
        }
        return palette.copy(primary = primary)
    }

    private fun lighten(rgb: Long): Long {
        val red = ((rgb shr 16) and 0xFF).coerceAtMost(255)
        val green = ((rgb shr 8) and 0xFF).coerceAtMost(255)
        val blue = (rgb and 0xFF).coerceAtMost(255)
        fun channel(value: Long): Long = (value + ((255 - value) * 0.45)).toLong().coerceIn(0, 255)
        return (channel(red) shl 16) or (channel(green) shl 8) or channel(blue)
    }
}

fun Color.toRgbLong(): Long = ((red * 255).toLong() shl 16) or ((green * 255).toLong() shl 8) or (blue * 255).toLong()

fun Long.toColor(): Color = Color(0xFF000000 or (this and 0xFFFFFF))
