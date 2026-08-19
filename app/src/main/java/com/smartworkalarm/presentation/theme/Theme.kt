package com.smartworkalarm.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Colors = lightColorScheme(
    primary = Color(0xFF315DA8),
    secondary = Color(0xFF53617A),
    background = Color(0xFFF8F9FF),
)

@Composable
fun WorkAlarmTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
