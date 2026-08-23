package com.smartworkalarm.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Colors = darkColorScheme(
    primary = Color(0xFFA98CFF),
    secondary = Color(0xFF54D8E8),
    background = Color(0xFF090D1B),
    surface = Color(0xFF141A2E),
    surfaceVariant = Color(0xFF202943),
    onBackground = Color(0xFFF4F1FF),
)

@Composable
fun WorkAlarmTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
