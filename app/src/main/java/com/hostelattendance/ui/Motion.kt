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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

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
 * Subtle breathing pulse modifier inspired by compose-animations AnimationExample7.
 */
@Composable
fun Modifier.pulsingGlow(
    enabled: Boolean = true,
    minScale: Float = 0.96f,
    maxScale: Float = 1.04f,
    durationMillis: Int = 1200,
): Modifier {
    if (!enabled) return this
    val infiniteTransition = rememberInfiniteTransition(label = "pulsingGlow")
    val scale by infiniteTransition.animateFloat(
        initialValue = minScale,
        targetValue = maxScale,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
        this.alpha = alpha
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
                    animationSpec = tween(180, easing = FastOutSlowInEasing),
                    initialOffsetY = { it / 2 },
                ) + fadeIn(animationSpec = tween(140))) togetherWith
                    (slideOutVertically(
                        animationSpec = tween(180, easing = FastOutSlowInEasing),
                        targetOffsetY = { -it / 2 },
                    ) + fadeOut(animationSpec = tween(140)))
            } else {
                (slideInVertically(
                    animationSpec = tween(180, easing = FastOutSlowInEasing),
                    initialOffsetY = { -it / 2 },
                ) + fadeIn(animationSpec = tween(140))) togetherWith
                    (slideOutVertically(
                        animationSpec = tween(180, easing = FastOutSlowInEasing),
                        targetOffsetY = { it / 2 },
                    ) + fadeOut(animationSpec = tween(140)))
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
 * Sleek dynamic sweep & rotate spinner inspired by compose-animations AnimationExample8.
 */
@Composable
fun AnimatedSweepSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    strokeWidth: Dp = 3.5.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    accentColor: Color = color,
) {
    val transition = rememberInfiniteTransition(label = "sweepSpinner")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "spinnerRotation",
    )

    val sweep by transition.animateFloat(
        initialValue = 30f,
        targetValue = 280f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "spinnerSweep",
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val strokePx = strokeWidth.toPx()
            val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)
            val inset = strokePx / 2f

            drawCircle(
                color = accentColor.copy(alpha = 0.16f),
                radius = (this.size.width - strokePx) / 2f,
                style = Stroke(width = strokePx),
            )

            rotate(degrees = rotation) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.05f),
                            accentColor.copy(alpha = 0.60f),
                            accentColor,
                        ),
                    ),
                    startAngle = 0f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                )
            }
        }
    }
}

/**
 * High-performance physics-based confetti particle simulation
 * inspired by compose-animations AnimationExample16.
 */
private class ConfettiParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var rotation: Float,
    val rotSpeed: Float,
    var ageMs: Float,
    val lifetimeMs: Float,
    val color: Color,
    val w: Float,
    val h: Float,
    val wobblePhase: Float,
)

private val CONFETTI_PALETTE = listOf(
    Color(0xFF4CAF50), // Emerald
    Color(0xFF00E5FF), // Cyan
    Color(0xFFFFD54F), // Gold
    Color(0xFFBA68C8), // Purple
    Color(0xFFFF5252), // Coral Red
    Color(0xFFFFFFFF), // White
    Color(0xFFFF8A80), // Rose
)

@Composable
fun ConfettiBurstView(
    trigger: Boolean,
    modifier: Modifier = Modifier,
) {
    val particles = remember { mutableStateListOf<ConfettiParticle>() }
    var frameNanos by remember { mutableLongStateOf(0L) }
    val density = LocalDensity.current

    val particleW = with(density) { 7.dp.toPx() }
    val particleH = with(density) { 13.dp.toPx() }

    LaunchedEffect(trigger) {
        if (trigger) {
            // Spawn initial celebratory burst from upper screen
            val burstCount = 65
            repeat(burstCount) {
                val angle = Math.toRadians((-90.0 + (Random.nextFloat() * 120.0 - 60.0))).toFloat()
                val speed = 650f + Random.nextFloat() * 850f
                val sizeJitter = 0.75f + Random.nextFloat() * 0.55f
                particles.add(
                    ConfettiParticle(
                        x = 300f + (Random.nextFloat() * 400f - 200f),
                        y = 180f + (Random.nextFloat() * 80f),
                        vx = cos(angle) * speed,
                        vy = sin(angle) * speed,
                        rotation = Random.nextFloat() * 360f,
                        rotSpeed = (Random.nextFloat() * 2f - 1f) * 650f,
                        ageMs = 0f,
                        lifetimeMs = 1500f + Random.nextFloat() * 1000f,
                        color = CONFETTI_PALETTE[Random.nextInt(CONFETTI_PALETTE.size)],
                        w = particleW * sizeJitter,
                        h = particleH * sizeJitter,
                        wobblePhase = Random.nextFloat() * (2f * Math.PI.toFloat()),
                    )
                )
            }
        }
    }

    // Physics step loop: only executes when particles are active on screen
    LaunchedEffect(particles.size > 0) {
        if (particles.isEmpty()) return@LaunchedEffect
        var lastFrame = withFrameNanos { it }
        while (particles.isNotEmpty()) {
            val now = withFrameNanos { it }
            val dtMs = min((now - lastFrame) / 1_000_000f, 40f)
            val dtSec = dtMs / 1000f
            lastFrame = now

            val damping = exp(-1.3f * dtSec)
            var i = 0
            while (i < particles.size) {
                val p = particles[i]
                p.ageMs += dtMs
                if (p.ageMs >= p.lifetimeMs) {
                    particles.removeAt(i)
                    continue
                }
                p.vy += 980f * dtSec // gravity
                p.vx *= damping
                p.vy *= damping
                val wobble = sin(p.ageMs * 0.006f + p.wobblePhase) * 75f
                p.x += (p.vx + wobble) * dtSec
                p.y += p.vy * dtSec
                p.rotation += p.rotSpeed * dtSec
                i++
            }
            frameNanos = now
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    // Interactive tap burst from tap location
                    repeat(35) {
                        val angle = Math.toRadians((-90.0 + (Random.nextFloat() * 140.0 - 70.0))).toFloat()
                        val speed = 500f + Random.nextFloat() * 700f
                        val sizeJitter = 0.7f + Random.nextFloat() * 0.5f
                        particles.add(
                            ConfettiParticle(
                                x = offset.x,
                                y = offset.y,
                                vx = cos(angle) * speed,
                                vy = sin(angle) * speed,
                                rotation = Random.nextFloat() * 360f,
                                rotSpeed = (Random.nextFloat() * 2f - 1f) * 600f,
                                ageMs = 0f,
                                lifetimeMs = 1300f + Random.nextFloat() * 900f,
                                color = CONFETTI_PALETTE[Random.nextInt(CONFETTI_PALETTE.size)],
                                w = particleW * sizeJitter,
                                h = particleH * sizeJitter,
                                wobblePhase = Random.nextFloat() * (2f * Math.PI.toFloat()),
                            )
                        )
                    }
                }
            }
    ) {
        @Suppress("UNUSED_EXPRESSION")
        frameNanos
        for (p in particles) {
            val lifeFraction = (1f - p.ageMs / p.lifetimeMs).coerceIn(0f, 1f)
            val alpha = if (lifeFraction < 0.3f) lifeFraction / 0.3f else 1f
            rotate(degrees = p.rotation, pivot = Offset(p.x, p.y)) {
                drawRect(
                    color = p.color.copy(alpha = alpha),
                    topLeft = Offset(p.x - p.w / 2f, p.y - p.h / 2f),
                    size = Size(p.w, p.h),
                )
            }
        }
    }
}
