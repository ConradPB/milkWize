package com.milkwize.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = ForestGreen,
    onPrimary = PaperWhite,
    secondary = EarthySlate,
    onSecondary = PaperWhite,
    tertiary = SunlitAmber,
    background = MilkWhite,
    surface = PaperWhite,
    onBackground = EarthySlate,
    onSurface = EarthySlate,
    error = TerracottaRed
)

@Composable
fun AndroidTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
