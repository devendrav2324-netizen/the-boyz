package com.hostelattendance.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import com.hostelattendance.R

private val DeepCharcoal = Color(0xFF15131B)
private val ElevatedSurface = Color(0xFF25212D)
private val Violet = Color(0xFF9B7BFF)
private val PrimaryText = Color(0xFFF4F0F7)
private val SecondaryText = Color(0xFFB8B1C3)
private val Dropline = FontFamily(
    Font(R.font.dropline_regular, FontWeight.Normal),
)

private fun TextStyle.withDropline(): TextStyle = copy(fontFamily = Dropline)

private val DroplineTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.withDropline(),
        displayMedium = base.displayMedium.withDropline(),
        displaySmall = base.displaySmall.withDropline(),
        headlineLarge = base.headlineLarge.withDropline(),
        headlineMedium = base.headlineMedium.withDropline(),
        headlineSmall = base.headlineSmall.withDropline(),
        titleLarge = base.titleLarge.withDropline(),
        titleMedium = base.titleMedium.withDropline(),
        titleSmall = base.titleSmall.withDropline(),
        bodyLarge = base.bodyLarge.withDropline(),
        bodyMedium = base.bodyMedium.withDropline(),
        bodySmall = base.bodySmall.withDropline(),
        labelLarge = base.labelLarge.withDropline(),
        labelMedium = base.labelMedium.withDropline(),
        labelSmall = base.labelSmall.withDropline(),
    )
}

private val HostelColors = darkColorScheme(
    primary = Violet,
    onPrimary = Color.White,
    secondary = Color(0xFF80DCD5),
    background = DeepCharcoal,
    onBackground = PrimaryText,
    surface = ElevatedSurface,
    onSurface = PrimaryText,
    onSurfaceVariant = SecondaryText,
    error = Color(0xFFFF3158),
)

@Composable
fun HostelAttendanceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HostelColors,
        typography = DroplineTypography,
        content = content,
    )
}
