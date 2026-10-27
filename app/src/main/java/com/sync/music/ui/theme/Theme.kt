package com.sync.music.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = YtPrimary,
    onPrimary = YtOnSurface,
    background = YtBackground,
    onBackground = YtOnSurface,
    surface = YtSurface,
    onSurface = YtOnSurface,
    surfaceVariant = YtSurfaceVariant,
    onSurfaceVariant = YtOnSurfaceVariant
)

@Composable
fun SyncTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
