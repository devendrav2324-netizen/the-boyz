package com.hostelattendance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.styropyr0.prismal.depth.PrismalDepthInset
import com.styropyr0.prismal.depth.PrismalDepthShadow
import com.styropyr0.prismal.drawPrismalGlass
import com.styropyr0.prismal.effects.applyPrismalGlassEffects
import com.styropyr0.prismal.shapes.PrismalCapsule
import com.styropyr0.prismal.shapes.PrismalRoundedRectangle
import com.styropyr0.prismal.sources.PrismalGlassLayer
import com.styropyr0.prismal.specular.PrismalSpecular
import com.styropyr0.prismal.specular.PrismalSpecularStyle

/**
 * CompositionLocal providing the root screen-level [PrismalGlassLayer] backdrop,
 * sampled by all liquid glass surfaces across the app for real-time refraction and blur.
 */
val LocalGlobalGlassBackdrop = staticCompositionLocalOf<PrismalGlassLayer?> { null }

/**
 * Applies the Prismal Liquid Glass effect to any component:
 * - AGSL Edge Lens Refraction & Dispersion
 * - Directional Specular Highlights
 * - Soft ambient depth shadows
 * - Translucent liquid glass tint overlay
 * - Graceful fallback on devices below Android 13
 */
fun Modifier.liquidGlassEffect(
    backdrop: PrismalGlassLayer?,
    density: Density,
    cornerRadius: Dp = 24.dp,
    blurRadius: Dp = 16.dp,
    refractionHeightPx: Float = 18f,
    refractionAmountPx: Float = 26f,
    surfaceColor: Color = Color(0x351F1C28),
    specular: PrismalSpecular? = PrismalSpecular.Default,
    depthShadow: PrismalDepthShadow? = PrismalDepthShadow(
        radius = 18.dp,
        color = Color.Black.copy(alpha = 0.38f),
    ),
    depthInset: PrismalDepthInset? = null,
): Modifier {
    val shape = PrismalRoundedRectangle(cornerRadius)
    val fallbackShape = RoundedCornerShape(cornerRadius)

    return if (backdrop != null) {
        this.drawPrismalGlass(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                applyPrismalGlassEffects(
                    density = density,
                    adaptiveLuminance = false,
                    luminance = 0.5f,
                    blurRadiusPx = with(density) { blurRadius.toPx() },
                    refractionHeightPx = refractionHeightPx,
                    refractionAmountPx = refractionAmountPx,
                    saturation = 1.35f,
                    depthEffect = true,
                    chromaticAberration = 0.12f,
                    useVibrancy = true,
                )
            },
            specular = if (specular != null) { { specular } } else null,
            depthShadow = if (depthShadow != null) { { depthShadow } } else null,
            depthInset = if (depthInset != null) { { depthInset } } else null,
            onDrawSurface = {
                drawRect(surfaceColor)
            },
        )
    } else {
        this
            .shadow(
                elevation = 16.dp,
                shape = fallbackShape,
                ambientColor = Color.Black.copy(alpha = 0.35f),
                spotColor = Color.Black.copy(alpha = 0.50f),
            )
            .clip(fallbackShape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0x992B2836),
                        Color(0xCC1F1C28),
                        Color(0xE615131C),
                    ),
                ),
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color(0x80FFFFFF),
                        Color(0x20FFFFFF),
                        Color(0x08FFFFFF),
                    ),
                ),
                shape = fallbackShape,
            )
    }
}

/**
 * Capsule-shaped variant of the Prismal Liquid Glass modifier (for buttons, pills, chips).
 */
fun Modifier.liquidGlassCapsuleEffect(
    backdrop: PrismalGlassLayer?,
    density: Density,
    blurRadius: Dp = 14.dp,
    refractionHeightPx: Float = 14f,
    refractionAmountPx: Float = 20f,
    surfaceColor: Color = Color(0x332B2836),
    specular: PrismalSpecular? = PrismalSpecular(style = PrismalSpecularStyle.Ambient),
    depthShadow: PrismalDepthShadow? = PrismalDepthShadow(
        radius = 12.dp,
        color = Color.Black.copy(alpha = 0.28f),
    ),
): Modifier {
    val shape = PrismalCapsule()
    val fallbackShape = RoundedCornerShape(percent = 50)

    return if (backdrop != null) {
        this.drawPrismalGlass(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                applyPrismalGlassEffects(
                    density = density,
                    adaptiveLuminance = false,
                    luminance = 0.5f,
                    blurRadiusPx = with(density) { blurRadius.toPx() },
                    refractionHeightPx = refractionHeightPx,
                    refractionAmountPx = refractionAmountPx,
                    saturation = 1.30f,
                    depthEffect = true,
                    chromaticAberration = 0.08f,
                    useVibrancy = true,
                )
            },
            specular = if (specular != null) { { specular } } else null,
            depthShadow = if (depthShadow != null) { { depthShadow } } else null,
            onDrawSurface = {
                drawRect(surfaceColor)
            },
        )
    } else {
        this
            .shadow(
                elevation = 8.dp,
                shape = fallbackShape,
                ambientColor = Color.Black.copy(alpha = 0.30f),
                spotColor = Color.Black.copy(alpha = 0.45f),
            )
            .clip(fallbackShape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0x802B2836),
                        Color(0xBF1F1C28),
                    ),
                ),
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color(0x60FFFFFF),
                        Color(0x18FFFFFF),
                    ),
                ),
                shape = fallbackShape,
            )
    }
}
