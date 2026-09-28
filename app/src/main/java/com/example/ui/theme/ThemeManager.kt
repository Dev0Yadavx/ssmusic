package com.example.ui.theme

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

enum class AccentPalette(
    val id: String,
    val displayName: String,
    val primaryDark: Color,
    val primaryLight: Color,
    val secondary: Color,
    val tertiary: Color
) {
    EMERALD(
        id = "emerald",
        displayName = "Emerald Mint",
        primaryDark = Color(0xFF6EE7B7),
        primaryLight = Color(0xFF059669),
        secondary = Color(0xFF93C5FD),
        tertiary = Color(0xFFF472B6)
    ),
    VIOLET(
        id = "violet",
        displayName = "Violet Pulse",
        primaryDark = Color(0xFFA78BFA),
        primaryLight = Color(0xFF7C3AED),
        secondary = Color(0xFFF472B6),
        tertiary = Color(0xFF38BDF8)
    ),
    OCEAN(
        id = "ocean",
        displayName = "Pixel Ocean",
        primaryDark = Color(0xFF38BDF8),
        primaryLight = Color(0xFF0284C7),
        secondary = Color(0xFF818CF8),
        tertiary = Color(0xFFF472B6)
    ),
    SUNSET(
        id = "sunset",
        displayName = "Sunset Amber",
        primaryDark = Color(0xFFFBBF24),
        primaryLight = Color(0xFFD97706),
        secondary = Color(0xFFFB923C),
        tertiary = Color(0xFFF43F5E)
    ),
    ROSE(
        id = "rose",
        displayName = "Electric Rose",
        primaryDark = Color(0xFFFB7185),
        primaryLight = Color(0xFFE11D48),
        secondary = Color(0xFFA855F7),
        tertiary = Color(0xFF38BDF8)
    );

    companion object {
        fun fromId(id: String): AccentPalette {
            return entries.firstOrNull { it.id == id } ?: EMERALD
        }
    }
}

enum class NowPlayingStyle(
    val id: String,
    val displayName: String,
    val description: String
) {
    IMMERSIVE_POSTER(
        id = "immersive_poster",
        displayName = "Immersive Poster",
        description = "Full canvas artwork with blur gradient scrim & glass controls"
    ),
    VINYL_DISC(
        id = "vinyl_disc",
        displayName = "Vinyl Disc",
        description = "Circular disc artwork with quick action tool row & squircle play"
    ),
    MODERN_CARD(
        id = "modern_card",
        displayName = "Modern Card",
        description = "Classic elevated album card with fluid timeline & studio controls"
    );

    companion object {
        fun fromId(id: String): NowPlayingStyle {
            return entries.firstOrNull { it.id == id } ?: IMMERSIVE_POSTER
        }
    }
}

class ThemeManager private constructor(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        "smusic_theme_preferences",
        Context.MODE_PRIVATE
    )

    private val _themeMode = MutableStateFlow(
        try {
            ThemeMode.valueOf(prefs.getString(KEY_THEME_MODE, ThemeMode.DARK.name) ?: ThemeMode.DARK.name)
        } catch (e: Exception) {
            ThemeMode.DARK
        }
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _useDynamicColor = MutableStateFlow(
        prefs.getBoolean(KEY_DYNAMIC_COLOR, Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
    )
    val useDynamicColor: StateFlow<Boolean> = _useDynamicColor.asStateFlow()

    private val _isAmoledBlack = MutableStateFlow(
        prefs.getBoolean(KEY_AMOLED_BLACK, false)
    )
    val isAmoledBlack: StateFlow<Boolean> = _isAmoledBlack.asStateFlow()

    private val _accentPalette = MutableStateFlow(
        AccentPalette.fromId(prefs.getString(KEY_ACCENT_PALETTE, AccentPalette.EMERALD.id) ?: AccentPalette.EMERALD.id)
    )
    val accentPalette: StateFlow<AccentPalette> = _accentPalette.asStateFlow()

    private val _fontOption = MutableStateFlow(
        FontOption.fromId(prefs.getString(KEY_FONT_OPTION, FontOption.SF_PRO_DISPLAY.id) ?: FontOption.SF_PRO_DISPLAY.id)
    )
    val fontOption: StateFlow<FontOption> = _fontOption.asStateFlow()

    private val _nowPlayingStyle = MutableStateFlow(
        NowPlayingStyle.fromId(prefs.getString(KEY_NOW_PLAYING_STYLE, NowPlayingStyle.IMMERSIVE_POSTER.id) ?: NowPlayingStyle.IMMERSIVE_POSTER.id)
    )
    val nowPlayingStyle: StateFlow<NowPlayingStyle> = _nowPlayingStyle.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun setDynamicColor(enabled: Boolean) {
        _useDynamicColor.value = enabled
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
    }

    fun setAmoledBlack(enabled: Boolean) {
        _isAmoledBlack.value = enabled
        prefs.edit().putBoolean(KEY_AMOLED_BLACK, enabled).apply()
    }

    fun setAccentPalette(palette: AccentPalette) {
        _accentPalette.value = palette
        prefs.edit().putString(KEY_ACCENT_PALETTE, palette.id).apply()
    }

    fun setFontOption(font: FontOption) {
        _fontOption.value = font
        prefs.edit().putString(KEY_FONT_OPTION, font.id).apply()
    }

    fun setNowPlayingStyle(style: NowPlayingStyle) {
        _nowPlayingStyle.value = style
        prefs.edit().putString(KEY_NOW_PLAYING_STYLE, style.id).apply()
    }

    companion object {
        private const val KEY_THEME_MODE = "key_theme_mode"
        private const val KEY_DYNAMIC_COLOR = "key_dynamic_color"
        private const val KEY_AMOLED_BLACK = "key_amoled_black"
        private const val KEY_ACCENT_PALETTE = "key_accent_palette"
        private const val KEY_FONT_OPTION = "key_font_option"
        private const val KEY_NOW_PLAYING_STYLE = "key_now_playing_style"

        @Volatile
        private var instance: ThemeManager? = null

        fun getInstance(context: Context): ThemeManager {
            return instance ?: synchronized(this) {
                instance ?: ThemeManager(context).also { instance = it }
            }
        }
    }
}
