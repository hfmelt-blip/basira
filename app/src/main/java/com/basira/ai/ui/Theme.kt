package com.basira.ai.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object C {
    val bg = Color(0xFF191A1F)
    val panel = Color(0xFF202128)
    val panel2 = Color(0xFF2A2B35)
    val line = Color(0xFF33343F)
    val text = Color(0xFFECEBE6)
    val muted = Color(0xFF9A998F)
    val accent = Color(0xFFE0A458)
    val accentInk = Color(0xFF1B1408)
    val ok = Color(0xFF6CC08B)
    val bad = Color(0xFFE2685F)
    val code = Color(0xFF121317)
}

@Composable
fun BasiraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.accent,
            onPrimary = C.accentInk,
            background = C.bg,
            onBackground = C.text,
            surface = C.panel,
            onSurface = C.text,
            surfaceVariant = C.panel2,
            onSurfaceVariant = C.muted,
            outline = C.line,
            error = C.bad,
            secondaryContainer = C.panel2,
            onSecondaryContainer = C.text,
        ),
        content = content
    )
}
