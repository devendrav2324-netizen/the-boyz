package com.hostelattendance.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * High-performance fluid spring animation specs inspired by skydoves/compose-animations
 */
object MotionSpecs {
    // Apple-style fluid glide (fast and responsive)
    val AppleGlideSpring = spring<Dp>(
        dampingRatio = 0.82f,
        stiffness = 550f,
    )

    // Apple-style button tactile bounce
    val AppleButtonSpring = spring<Float>(
        dampingRatio = 0.76f,
        stiffness = 450f,
    )

    // Apple-style pop-up overshoot
    val ApplePopSpring = spring<Float>(
        dampingRatio = 0.76f,
        stiffness = 420f,
    )

    val BouncySpring = spring<Float>(
        dampingRatio = 0.74f,
        stiffness = Spring.StiffnessMedium,
    )

    val SnappySpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    val PillSpring = spring<Dp>(
        dampingRatio = 0.80f,
        stiffness = 650f,
    )

    val FastTween = tween<Float>(
        durationMillis = 200,
        easing = FastOutSlowInEasing,
    )

    val CardExpand = spring<androidx.compose.ui.unit.IntSize>(
        dampingRatio = 0.82f,
        stiffness = Spring.StiffnessMedium,
    )
}

@Composable
internal fun rememberMotionInteractionSource(): MutableInteractionSource =
    remember { MutableInteractionSource() }

/**
 * Instantaneous touch-down response with tactile spring bounce on release.
 * Inspired by compose-animations AnimationExample4 and AnimationExample14.
 */
@Composable
internal fun Modifier.pressFeedback(
    interactionSource: MutableInteractionSource = rememberMotionInteractionSource(),
    pressedScale: Float = 0.95f,
): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = if (isPressed) {
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)
        } else {
            // Apple-style tactile click bounce - instant, crisp recovery
            spring(dampingRatio = 0.78f, stiffness = 520f)
        },
        label = "pressScaleFeedback",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Snappy animated rolling counter inspired by compose-animations AnimationExample5.
 */
@Composable
fun AnimatedRollingCounter(
    count: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = MaterialTheme.colorScheme.onSurface,
    fontWeight: FontWeight = FontWeight.Bold,
) {
    AnimatedContent(
        targetState = count,
        modifier = modifier,
        transitionSpec = {
            val goingUp = targetState > initialState
            if (goingUp) {
                (slideInVertically(
                    animationSpec = tween(190, easing = FastOutSlowInEasing),
                    initialOffsetY = { it },
                ) + fadeIn(animationSpec = tween(150))) togetherWith
                    (slideOutVertically(
                        animationSpec = tween(190, easing = FastOutSlowInEasing),
                        targetOffsetY = { -it },
                    ) + fadeOut(animationSpec = tween(150)))
            } else {
                (slideInVertically(
                    animationSpec = tween(190, easing = FastOutSlowInEasing),
                    initialOffsetY = { -it },
                ) + fadeIn(animationSpec = tween(150))) togetherWith
                    (slideOutVertically(
                        animationSpec = tween(190, easing = FastOutSlowInEasing),
                        targetOffsetY = { it },
                    ) + fadeOut(animationSpec = tween(150)))
            }.using(SizeTransform(clip = false))
        },
        label = "rollingCounter_$count",
    ) { targetCount ->
        Text(
            text = targetCount.toString(),
            style = style,
            color = color,
            fontWeight = fontWeight,
        )
    }
}

/**
 * Sleek sweep & rotate spinner inspired by compose-animations AnimationExample8.
 */
@Composable
fun AnimatedSweepSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    strokeWidth: Dp = 3.5.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    accentColor: Color = color,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sweepSpinner")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "spinnerRotation",
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val diameter = size.toPx()
            val strokePx = strokeWidth.toPx()
            val arcSize = diameter - strokePx

            drawCircle(
                color = accentColor.copy(alpha = 0.16f),
                radius = arcSize / 2f,
                style = Stroke(width = strokePx),
            )

            rotate(degrees = rotation) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.05f),
                            accentColor.copy(alpha = 0.5f),
                            accentColor,
                        ),
                    ),
                    startAngle = 0f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = Offset(strokePx / 2f, strokePx / 2f),
                    size = Size(arcSize, arcSize),
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                )
            }
        }
    }
}
