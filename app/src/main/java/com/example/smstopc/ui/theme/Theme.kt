package com.example.smstopc.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = Blue500,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = Blue500,
    secondary = Green500,
    background = Gray50,
    surface = androidx.compose.ui.graphics.Color.White,
    onBackground = Gray900,
    onSurface = Gray900,
    outline = Gray200,
)

@Composable
fun SmsToEmailTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}
