package com.smartworkalarm.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Typography
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp

private val Colors = darkColorScheme(
    primary = Color(0xFFC9F078), onPrimary = Color(0xFF18220D),
    primaryContainer = Color(0xFF303E21), onPrimaryContainer = Color(0xFFE3F4C9),
    secondary = Color(0xFFBDD3B1), onSecondary = Color(0xFF192A17),
    secondaryContainer = Color(0xFF29372A), onSecondaryContainer = Color(0xFFDCE8D7),
    tertiary = Color(0xFFE8C591), onTertiary = Color(0xFF362915),
    background = Color(0xFF101410), onBackground = Color(0xFFF3F3E8),
    surface = Color(0xFF1B211C), onSurface = Color(0xFFF3F3E8),
    surfaceVariant = Color(0xFF252D26), onSurfaceVariant = Color(0xFFACB8AC),
    surfaceContainerLowest = Color(0xFF0C100D), surfaceContainerLow = Color(0xFF171D18),
    surfaceContainer = Color(0xFF1B211C), surfaceContainerHigh = Color(0xFF252D26),
    surfaceContainerHighest = Color(0xFF303A31),
    outline = Color(0xFF71816E), outlineVariant = Color(0xFF354036),
    error = Color(0xFFFFB4A2), onError = Color(0xFF561D12),
    errorContainer = Color(0xFF3C2722), onErrorContainer = Color(0xFFFFD9CF),
    inverseSurface = Color(0xFFF0F1DF), inverseOnSurface = Color(0xFF202A20),
    inversePrimary = Color(0xFF486425), surfaceTint = Color.Transparent,
)

private val CalarmType = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 76.sp, lineHeight = 82.sp, letterSpacing = (-3).sp, fontFeatureSettings = "tnum"),
    displayMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 60.sp, lineHeight = 66.sp, letterSpacing = (-2).sp, fontFeatureSettings = "tnum"),
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 44.sp, lineHeight = 50.sp, letterSpacing = (-1).sp, fontFeatureSettings = "tnum"),
    headlineLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 38.sp, letterSpacing = (-.7).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 34.sp, letterSpacing = (-.5).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp),
)

@Composable
fun WorkAlarmTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = CalarmType,
        shapes = Shapes(RoundedCornerShape(8.dp), RoundedCornerShape(12.dp), RoundedCornerShape(18.dp), RoundedCornerShape(24.dp), RoundedCornerShape(28.dp)),
        content = content)
}
