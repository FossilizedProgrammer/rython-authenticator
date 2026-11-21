package com.rython.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Accent = Color(0xFF12B886)
private val AccentDark = Color(0xFF0E9C74)

private val LightScheme = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3F9E9),
    onPrimaryContainer = Color(0xFF05372B),
    secondary = Color(0xFF3B5BDB),
    background = Color(0xFFF6F8F9),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEDF1F3),
    onSurface = Color(0xFF101617),
    outline = Color(0xFFB9C4C8)
)

private val DarkScheme = darkColorScheme(
    primary = AccentDark,
    onPrimary = Color(0xFF00281E),
    primaryContainer = Color(0xFF0C4A3A),
    onPrimaryContainer = Color(0xFFB6F2DE),
    secondary = Color(0xFF8FA4FF),
    background = Color(0xFF0D1112),
    surface = Color(0xFF151B1D),
    surfaceVariant = Color(0xFF222B2D),
    onSurface = Color(0xFFE6EDEE),
    outline = Color(0xFF44514F)
)

@Composable
fun RythonTheme(dark: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        content = content
    )
}

private val BadgePalette = listOf(
    Color(0xFF12B886), Color(0xFF3B5BDB), Color(0xFFE8590C), Color(0xFF9C36B5),
    Color(0xFF1098AD), Color(0xFFD6336C), Color(0xFF2F9E44), Color(0xFF845EF7),
    Color(0xFFC2255C), Color(0xFF0C8599)
)

fun badgeColor(seed: String): Color =
    BadgePalette[((seed.hashCode() % BadgePalette.size) + BadgePalette.size) % BadgePalette.size]

/** Offline service badge: colored circle with the service initials. */
@Composable
fun ServiceBadge(initials: String, seed: String, size: Dp = 44.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(badgeColor(seed)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = (size.value * 0.34f).sp,
            style = MaterialTheme.typography.labelLarge
        )
    }
}
