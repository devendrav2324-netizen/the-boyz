package com.hostelattendance.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.max

/**
 * Interactive photo cropping and frame adjustment dialog.
 * Allows user to pan, zoom (pinch or slider), rotate 90 degrees,
 * and crops the output into a high-resolution portrait passport image.
 */
@Composable
fun ImageCropDialog(
    sourceBitmap: Bitmap,
    onDismiss: () -> Unit,
    onCropConfirmed: (Bitmap) -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var rotationDegrees by remember { mutableFloatStateOf(0f) }

    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            color = Color(0xF2161420),
            tonalElevation = 16.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x35FFFFFF)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "Adjust & Crop Photo",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Text(
                            text = "Drag to reposition, pinch or use slider to zoom",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f),
                        )
                    }

                    // Rotate 90 degrees button
                    IconButton(
                        onClick = {
                            rotationDegrees = (rotationDegrees + 90f) % 360f
                        },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0x25FFFFFF)),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Rotate 90 degrees",
                            tint = Color.White,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Interactive Crop Viewport
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xFF0B0912))
                        .onSizeChanged { viewportSize = it }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(0.8f, 5.0f)
                                offsetX += pan.x
                                offsetY += pan.y
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    val composeBitmap = remember(sourceBitmap) { sourceBitmap.asImageBitmap() }

                    // Scaled and transformed image
                    androidx.compose.foundation.Image(
                        bitmap = composeBitmap,
                        contentDescription = "Source image to crop",
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offsetX
                                translationY = offsetY
                                rotationZ = rotationDegrees
                            },
                    )

                    // Overlay Mask with square viewfinder frame
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val canvasW = size.width
                        val canvasH = size.height
                        val boxDimension = minOf(canvasW, canvasH) * 0.78f
                        val left = (canvasW - boxDimension) / 2f
                        val top = (canvasH - boxDimension) / 2f

                        // Outer darkened scrim
                        drawRect(
                            color = Color.Black.copy(alpha = 0.58f),
                            size = size,
                        )

                        // Clear viewport hole
                        drawRoundRect(
                            color = Color.Transparent,
                            topLeft = Offset(left, top),
                            size = Size(boxDimension, boxDimension),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx()),
                            blendMode = androidx.compose.ui.graphics.BlendMode.Clear,
                        )

                        // Viewport border guide
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.85f),
                            topLeft = Offset(left, top),
                            size = Size(boxDimension, boxDimension),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx()),
                            style = Stroke(width = 2.5.dp.toPx()),
                        )

                        // Rule of thirds grid lines
                        val step = boxDimension / 3f
                        val gridColor = Color.White.copy(alpha = 0.35f)
                        val gridStroke = 1.dp.toPx()

                        // Vertical lines
                        drawLine(
                            color = gridColor,
                            start = Offset(left + step, top),
                            end = Offset(left + step, top + boxDimension),
                            strokeWidth = gridStroke,
                        )
                        drawLine(
                            color = gridColor,
                            start = Offset(left + step * 2, top),
                            end = Offset(left + step * 2, top + boxDimension),
                            strokeWidth = gridStroke,
                        )

                        // Horizontal lines
                        drawLine(
                            color = gridColor,
                            start = Offset(left, top + step),
                            end = Offset(left + boxDimension, top + step),
                            strokeWidth = gridStroke,
                        )
                        drawLine(
                            color = gridColor,
                            start = Offset(left, top + step * 2),
                            end = Offset(left + boxDimension, top + step * 2),
                            strokeWidth = gridStroke,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Zoom Slider & Reset row
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Zoom: ${String.format("%.1fx", scale)}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                        Text(
                            text = "Reset position",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    scale = 1f
                                    offsetX = 0f
                                    offsetY = 0f
                                    rotationDegrees = 0f
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }

                    Slider(
                        value = scale,
                        onValueChange = { scale = it },
                        valueRange = 0.8f..4.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color(0x35FFFFFF),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x40FFFFFF)),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                    ) {
                        Text("Cancel", color = Color.White)
                    }

                    Button(
                        onClick = {
                            val cropped = cropBitmap(
                                source = sourceBitmap,
                                scale = scale,
                                offsetX = offsetX,
                                offsetY = offsetY,
                                rotationDegrees = rotationDegrees,
                                viewportWidth = viewportSize.width,
                                viewportHeight = viewportSize.height,
                            )
                            onCropConfirmed(cropped)
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White,
                        ),
                        modifier = Modifier
                            .weight(1.3f)
                            .height(50.dp),
                    ) {
                        Text("Save & Apply", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

/**
 * Performs actual pixel-accurate bitmap cropping and transformation.
 */
private fun cropBitmap(
    source: Bitmap,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    rotationDegrees: Float,
    viewportWidth: Int,
    viewportHeight: Int,
): Bitmap {
    val targetDimension = 640
    val result = Bitmap.createBitmap(targetDimension, targetDimension, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)

    val vw = viewportWidth.toFloat().takeIf { it > 0 } ?: 1000f
    val vh = viewportHeight.toFloat().takeIf { it > 0 } ?: 1000f
    val boxDimension = minOf(vw, vh) * 0.78f
    val boxLeft = (vw - boxDimension) / 2f
    val boxTop = (vh - boxDimension) / 2f

    val ratio = targetDimension.toFloat() / boxDimension

    canvas.save()
    // Transform to center of target
    canvas.translate(targetDimension / 2f, targetDimension / 2f)

    // Apply viewport scale factor
    canvas.scale(ratio, ratio)

    // Apply user translation offset
    canvas.translate(offsetX, offsetY)

    // Apply user rotation
    canvas.rotate(rotationDegrees)

    // Apply user scale
    canvas.scale(scale, scale)

    // Draw source centered
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    canvas.drawBitmap(source, -source.width / 2f, -source.height / 2f, paint)

    canvas.restore()
    return result
}
