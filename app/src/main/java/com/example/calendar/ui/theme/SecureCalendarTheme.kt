package com.example.calendar.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun SecureCalendarTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) darkColorScheme(
        primary = Color(0xFFFFB1CD), onPrimary = Color(0xFF541D35),
        primaryContainer = Color(0xFF71334D), onPrimaryContainer = Color(0xFFFFD9E5),
        secondary = Color(0xFFE8B6C8), secondaryContainer = Color(0xFF563C47),
        background = Color(0xFF20171B), surface = Color(0xFF281C22),
        surfaceVariant = Color(0xFF503D46),
        surfaceContainerLowest = Color(0xFF1B1418), surfaceContainerLow = Color(0xFF281F24),
        surfaceContainer = Color(0xFF30262C), surfaceContainerHigh = Color(0xFF3A2E35),
        surfaceContainerHighest = Color(0xFF453840), outlineVariant = Color(0xFF55454E),
        onSecondary = Color(0xFF432535), onSecondaryContainer = Color(0xFFFFD9E5),
        tertiary = Color(0xFFE8B6C8), onTertiary = Color(0xFF432535), onSurface = Color(0xFFF8E5EC),
        onBackground = Color(0xFFF8E5EC), onSurfaceVariant = Color(0xFFDCC0CC)
    ) else lightColorScheme(
        primary = Color(0xFFA7446B), onPrimary = Color.White,
        primaryContainer = Color(0xFFFFD9E6), onPrimaryContainer = Color(0xFF63213D),
        secondary = Color(0xFF88576B), secondaryContainer = Color(0xFFFBE1EB),
        onSecondaryContainer = Color(0xFF5A3042), tertiary = Color(0xFF9A5875),
        background = Color(0xFFFCF7F9), surface = Color(0xFFFFFBFC),
        surfaceVariant = Color(0xFFF7E2EB), onSurface = Color(0xFF39252F),
        onBackground = Color(0xFF39252F), onSurfaceVariant = Color(0xFF775A68),
        outline = Color(0xFF9C7E8B), outlineVariant = Color(0xFFE8DCE1),
        surfaceContainerLowest = Color.White, surfaceContainer = Color(0xFFF7EDF1),
        surfaceContainerLow = Color(0xFFFAF1F5), surfaceContainerHigh = Color(0xFFF2E5EB),
        surfaceContainerHighest = Color(0xFFEBDDE4)
    )
    MaterialTheme(colorScheme = colors, typography = Typography, content = content)
}
