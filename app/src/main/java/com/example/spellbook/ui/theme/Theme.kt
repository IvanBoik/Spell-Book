package com.example.spellbook.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.example.spellbook.data.AppTheme

private val LightColorScheme = lightColorScheme(
    primary = DndRed,
    onPrimary = ParchmentCard,
    primaryContainer = DndRedDark,
    onPrimaryContainer = ParchmentCard,
    secondary = DndGold,
    onSecondary = ParchmentCard,
    secondaryContainer = Parchment,
    onSecondaryContainer = InkBrown,
    tertiary = DndRedLight,
    onTertiary = ParchmentCard,
    tertiaryContainer = Parchment,
    onTertiaryContainer = InkBrown,
    background = Parchment,
    onBackground = InkBrown,
    surface = ParchmentCard,
    onSurface = InkBrown,
    surfaceVariant = Parchment,
    onSurfaceVariant = InkBrown,
    surfaceDim = ParchmentBorder,
    surfaceBright = ParchmentCard,
    surfaceContainerLowest = ParchmentCard,
    surfaceContainerLow = ParchmentCard,
    surfaceContainer = Parchment,
    surfaceContainerHigh = Parchment,
    surfaceContainerHighest = ParchmentBorder,
    surfaceTint = DndRed,
    inverseSurface = InkBrown,
    inverseOnSurface = ParchmentCard,
    inversePrimary = DndRedLight,
    outline = ParchmentBorder,
    outlineVariant = ParchmentBorder,
    scrim = InkBrown,
)

private val DarkColorScheme = darkColorScheme(
    primary = DndRedOnDark,
    onPrimary = DndRedDark,
    primaryContainer = DndRedDark,
    onPrimaryContainer = DarkParchmentText,
    secondary = DndGold,
    onSecondary = DarkBackground,
    secondaryContainer = DarkSurface,
    onSecondaryContainer = DarkParchmentText,
    tertiary = DndRedLight,
    onTertiary = DarkParchmentText,
    tertiaryContainer = DarkSurface,
    onTertiaryContainer = DarkParchmentText,
    background = DarkBackground,
    onBackground = DarkParchmentText,
    surface = DarkSurface,
    onSurface = DarkParchmentText,
    surfaceVariant = DarkSurface,
    onSurfaceVariant = DarkParchmentText,
    surfaceDim = DarkBackground,
    surfaceBright = DarkSurface,
    surfaceContainerLowest = DarkBackground,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurface,
    surfaceContainerHighest = DarkSurface,
    surfaceTint = DndRedOnDark,
    inverseSurface = DarkParchmentText,
    inverseOnSurface = DarkBackground,
    inversePrimary = DndRedDark,
    outline = DndGold,
    outlineVariant = DndGold,
    scrim = DarkBackground,
)

/**
 * Тема приложения.
 *
 * @param appTheme выбранное пользователем оформление; по умолчанию следует системному.
 */
@Composable
fun SpellBookTheme(
    appTheme: AppTheme = AppTheme.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = appTheme.isDark(isSystemInDarkTheme())
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
