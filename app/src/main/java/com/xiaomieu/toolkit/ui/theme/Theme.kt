package com.xiaomieu.toolkit.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val XiaomiOrange = Color(0xFFFF6B00)
private val XiaomiOrangeDark = Color(0xFFFF8A3D)
private val XiaomiTeal = Color(0xFF03A9A5)

private val LightColors = lightColorScheme(
    primary = XiaomiOrange,
    secondary = XiaomiTeal,
)

private val DarkColors = darkColorScheme(
    primary = XiaomiOrangeDark,
    secondary = XiaomiTeal,
)

@Composable
fun XiaomiEuToolKitTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
