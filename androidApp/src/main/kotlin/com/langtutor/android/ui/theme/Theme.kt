package com.langtutor.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// WhatsApp-inspired color scheme
private val LangTutorColorScheme = lightColorScheme(
    primary = Color(0xFF075E54),           // dark teal — app bars, buttons
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCF8C6),  // pale green — outgoing message bubbles
    onPrimaryContainer = Color(0xFF0A3130),
    secondary = Color(0xFF25D366),         // bright green — accents
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFECE5DD),        // beige — chat wallpaper
    onBackground = Color(0xFF111B21),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111B21),
    surfaceVariant = Color(0xFFFFFFFF),    // white — incoming message bubbles
    onSurfaceVariant = Color(0xFF111B21),
    error = Color(0xFFB00020),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

@Composable
fun LangTutorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LangTutorColorScheme,
        content = content,
    )
}
