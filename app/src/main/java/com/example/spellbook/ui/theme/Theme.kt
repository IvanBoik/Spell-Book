package com.example.spellbook.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = DndRed,
    onPrimary = ParchmentCard,
    primaryContainer = DndRedDark,
    onPrimaryContainer = ParchmentCard,
    secondary = DndGold,
    onSecondary = ParchmentCard,
    tertiary = DndRedLight,
    onTertiary = ParchmentCard,
    background = Parchment,
    onBackground = InkBrown,
    surface = ParchmentCard,
    onSurface = InkBrown,
    surfaceVariant = Parchment,
    onSurfaceVariant = InkBrown,
    outline = ParchmentBorder,
)

private val DarkColorScheme = darkColorScheme(
    primary = DndRedOnDark,
    onPrimary = DndRedDark,
    primaryContainer = DndRedDark,
    onPrimaryContainer = DarkParchmentText,
    secondary = DndGold,
    onSecondary = DarkBackground,
    tertiary = DndRedLight,
    onTertiary = DarkParchmentText,
    background = DarkBackground,
    onBackground = DarkParchmentText,
    surface = DarkSurface,
    onSurface = DarkParchmentText,
    surfaceVariant = DarkSurface,
    onSurfaceVariant = DarkParchmentText,
    outline = DndGold,
)

@Composable
fun SpellBookTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
