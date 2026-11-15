package com.hostelattendance.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hostelattendance.data.update.AppUpdateInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Narrow display box in transparent blur glass effect showing update is available to download.
 * - Shows when user or admin enters the app.
 * - Remains for 5 seconds and automatically slides up and disappears.
 * - Interactive: User can tap to open update download or throw/flick upwards with fluid physics animation to dismiss.
 */
@Composable
fun FloatingUpdateBanner(
    updateInfo: AppUpdateInfo,
    onUpdateClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val offsetYAnim = remember { Animatable(-150f) }
    var isDismissed by remember { mutableStateOf(false) }

    // Animate entry from top immediately upon appearance
    LaunchedEffect(Unit) {
        offsetYAnim.animateTo(
            targetValue = 0f,
            animationSpec = spring(
                dampingRatio = 0.75f,
                stiffness = Spring.StiffnessMediumLow,
            ),
        )
        // Remains visible for 5 seconds then slides up and disappears
        delay(5000L)
        if (!isDismissed) {
            offsetYAnim.animateTo(
                targetValue = -300f,
                animationSpec = tween(durationMillis = 380, easing = FastOutLinearInEasing),
            )
            isDismissed = true
            onDismiss()
        }
    }

    if (!isDismissed) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 8.dp, start = 16.dp, end = 16.dp)
                .offset { IntOffset(0, offsetYAnim.value.roundToInt()) }
                .pointerInput(Unit) {
                    var accumulatedDrag = 0f
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (accumulatedDrag < -25f) {
                                // User threw/flicked upward -> fling up and dismiss
                                scope.launch {
                                    isDismissed = true
                                    offsetYAnim.animateTo(
                                        targetValue = -350f,
                                        animationSpec = spring(
                                            dampingRatio = 0.8f,
                                            stiffness = 650f,
                                        ),
                                    )
                                    onDismiss()
                                }
                            } else {
                                // Snap back to natural position
                                scope.launch {
                                    offsetYAnim.animateTo(0f, spring(dampingRatio = 0.7f))
                                }
                            }
                            accumulatedDrag = 0f
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            accumulatedDrag += dragAmount
                            scope.launch {
                                // Allow dragging up with instant tracking, resist dragging down
                                val newOffset = if (accumulatedDrag < 0f) accumulatedDrag else accumulatedDrag * 0.25f
                                offsetYAnim.snapTo(newOffset)
                            }
                        },
                    )
                },
            contentAlignment = Alignment.TopCenter,
        ) {
            val backdrop = LocalGlobalGlassBackdrop.current
            val density = LocalDensity.current

            val bannerShape = RoundedCornerShape(26.dp)

            val glassModifier = if (backdrop != null) {
                Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .height(44.dp)
                    .liquidGlassCapsuleEffect(
                        backdrop = backdrop,
                        density = density,
                        blurRadius = 16.dp,
                        surfaceColor = Color(0x351F1B2E),
                    )
            } else {
                Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .height(44.dp)
                    .shadow(elevation = 14.dp, shape = bannerShape, spotColor = Color.Black.copy(alpha = 0.6f))
                    .clip(bannerShape)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xE81F1A2D),
                                Color(0xDA2A223B),
                                Color(0xE81F1A2D),
                            ),
                        ),
                    )
                    .border(
                        BorderStroke(
                            1.dp,
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0x80818CF8),
                                    Color(0x30FFFFFF),
                                    Color(0x80818CF8),
                                ),
                            ),
                        ),
                        bannerShape,
                    )
            }

            Row(
                modifier = glassModifier
                    .clickable { onUpdateClick() }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    // Glowing download badge
                    Surface(
                        shape = CircleShape,
                        color = Color(0x336366F1),
                        border = BorderStroke(0.8.dp, Color(0xFF818CF8)),
                        modifier = Modifier.size(24.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "🚀",
                                fontSize = 12.sp,
                            )
                        }
                    }

                    Text(
                        text = "Update is available to download",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                    )
                }

                // Interactive throw-up hint
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0x25FFFFFF),
                        border = BorderStroke(0.6.dp, Color(0x35FFFFFF)),
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(
                                text = "▲",
                                fontSize = 8.sp,
                                color = Color.White.copy(alpha = 0.8f),
                            )
                            Text(
                                text = "Swipe up",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.8f),
                            )
                        }
                    }
                }
            }
        }
    }
}
