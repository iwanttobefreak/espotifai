package com.espotifai.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import coil.ImageLoader

val EspotifaiGreen = Color(0xFF1DB954)
val DarkBackground = Color(0xFF121212)
val DarkSurface = Color(0xFF181818)
val DarkSurfaceVariant = Color(0xFF282828)

val DarkColors = darkColorScheme(
    primary = EspotifaiGreen,
    onPrimary = Color(0xFF000000),
    background = DarkBackground,
    onBackground = Color(0xFFFFFFFF),
    surface = DarkSurface,
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFB3B3B3),
    secondary = Color(0xFFB3B3B3),
    onSecondary = Color(0xFF000000)
)

val LocalImageLoader = staticCompositionLocalOf<ImageLoader> {
    error("ImageLoader no proporcionado")
}

@Composable
fun EspotifaiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content
    )
}
