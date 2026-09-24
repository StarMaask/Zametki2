package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

enum class AppThemePreset(val title: String) {
    PURITY("Чистота (Светлая минималистичная)"),
    DEPTH("Глубина (Тёмная контрастная)"),
    WARMTH("Тепло (Уютная янтарная)"),
    MATERIAL_YOU("Material You (Динамическая)"),
    DARK_NIGHT("Глубокая ночь (Тёмная)"),
    OCEAN("Океан (Морская)"),
    FOREST("Изумруд (Лесная)")
}

// 1. Палитра «Чистота»
private val PurityColorScheme = lightColorScheme(
    primary = PurityPrimary,
    onPrimary = PurityOnPrimary,
    primaryContainer = PurityPrimaryContainer,
    onPrimaryContainer = PurityOnPrimaryContainer,
    secondary = PuritySecondary,
    onSecondary = PurityOnSecondary,
    secondaryContainer = PuritySecondaryContainer,
    onSecondaryContainer = PurityOnSecondaryContainer,
    background = PurityBackground,
    onBackground = PurityOnBackground,
    surface = PuritySurface,
    onSurface = PurityOnSurface,
    surfaceVariant = PuritySurfaceVariant,
    onSurfaceVariant = PurityOnSurfaceVariant,
    outline = PurityOutline,
    outlineVariant = PurityOutlineVariant
)

// 2. Палитра «Глубина»
private val DepthColorScheme = darkColorScheme(
    primary = DepthPrimary,
    onPrimary = DepthOnPrimary,
    primaryContainer = DepthPrimaryContainer,
    onPrimaryContainer = DepthOnPrimaryContainer,
    secondary = DepthSecondary,
    onSecondary = DepthOnSecondary,
    secondaryContainer = DepthSecondaryContainer,
    onSecondaryContainer = DepthOnSecondaryContainer,
    background = DepthBackground,
    onBackground = DepthOnBackground,
    surface = DepthSurface,
    onSurface = DepthOnSurface,
    surfaceVariant = DepthSurfaceVariant,
    onSurfaceVariant = DepthOnSurfaceVariant,
    outline = DepthOutline,
    outlineVariant = DepthOutlineVariant
)

// 3. Палитра «Тепло»
private val WarmthColorScheme = lightColorScheme(
    primary = WarmthPrimary,
    onPrimary = WarmthOnPrimary,
    primaryContainer = WarmthPrimaryContainer,
    onPrimaryContainer = WarmthOnPrimaryContainer,
    secondary = WarmthSecondary,
    onSecondary = WarmthOnSecondary,
    secondaryContainer = WarmthSecondaryContainer,
    onSecondaryContainer = WarmthOnSecondaryContainer,
    background = WarmthBackground,
    onBackground = WarmthOnBackground,
    surface = WarmthSurface,
    onSurface = WarmthOnSurface,
    surfaceVariant = WarmthSurfaceVariant,
    onSurfaceVariant = WarmthOnSurfaceVariant,
    outline = WarmthOutline,
    outlineVariant = WarmthOutlineVariant
)

private val OceanColorScheme = lightColorScheme(
    primary = Color(0xFF0284C7),
    onPrimary = Color.White,
    background = Color(0xFFF0F9FF),
    surface = Color.White
)

private val ForestColorScheme = lightColorScheme(
    primary = Color(0xFF059669),
    onPrimary = Color.White,
    background = Color(0xFFF0FDF4),
    surface = Color.White
)

@Composable
fun NotesTheme(
    preset: AppThemePreset = AppThemePreset.PURITY,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when (preset) {
        AppThemePreset.PURITY -> if (darkTheme) DepthColorScheme else PurityColorScheme
        AppThemePreset.DEPTH -> DepthColorScheme
        AppThemePreset.WARMTH -> WarmthColorScheme
        AppThemePreset.MATERIAL_YOU -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (darkTheme) DepthColorScheme else PurityColorScheme
            }
        }
        AppThemePreset.DARK_NIGHT -> DepthColorScheme
        AppThemePreset.OCEAN -> OceanColorScheme
        AppThemePreset.FOREST -> ForestColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
