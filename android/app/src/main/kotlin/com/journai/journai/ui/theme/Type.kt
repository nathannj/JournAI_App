package com.journai.journai.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.journai.journai.R

// Merriweather font family (bundled in res/font)
val Merriweather = FontFamily(
    Font(R.font.merriweather_regular, FontWeight.Normal),
    Font(R.font.merriweather_italic, FontWeight.Normal, style = FontStyle.Italic),
    Font(R.font.merriweather_bold, FontWeight.Bold),
    Font(R.font.merriweather_bold_italic, FontWeight.Bold, style = FontStyle.Italic),
)

// Keep default sizes/spacing; just swap the font family everywhere
private val Default = Typography()

val Typography = Default.copy(
    displayLarge = Default.displayLarge.copy(fontFamily = Merriweather),
    displayMedium = Default.displayMedium.copy(fontFamily = Merriweather),
    displaySmall = Default.displaySmall.copy(fontFamily = Merriweather),

    headlineLarge = Default.headlineLarge.copy(fontFamily = Merriweather),
    headlineMedium = Default.headlineMedium.copy(fontFamily = Merriweather),
    headlineSmall = Default.headlineSmall.copy(fontFamily = Merriweather),

    titleLarge = Default.titleLarge.copy(fontFamily = Merriweather),
    titleMedium = Default.titleMedium.copy(fontFamily = Merriweather),
    titleSmall = Default.titleSmall.copy(fontFamily = Merriweather),

    bodyLarge = Default.bodyLarge.copy(fontFamily = Merriweather),
    bodyMedium = Default.bodyMedium.copy(fontFamily = Merriweather),
    bodySmall = Default.bodySmall.copy(fontFamily = Merriweather),

    labelLarge = Default.labelLarge.copy(fontFamily = Merriweather),
    labelMedium = Default.labelMedium.copy(fontFamily = Merriweather),
    labelSmall = Default.labelSmall.copy(fontFamily = Merriweather),
)
