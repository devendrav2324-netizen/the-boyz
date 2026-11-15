package com.hostelattendance.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hostelattendance.data.local.UserSessionManager
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IdCardScreen(
    studentName: String?,
    roomNumber: String?,
    studentId: String? = null,
    rollNumber: String? = null,
    uid: String,
) {
    val context = LocalContext.current

    var photoPath by remember(uid) {
        mutableStateOf(UserSessionManager.getIdCardPhotoPath(context, uid))
    }
    var photoBitmap by remember(photoPath) {
        mutableStateOf<ImageBitmap?>(
            photoPath?.let { path ->
                try {
                    BitmapFactory.decodeFile(path)?.asImageBitmap()
                } catch (_: Exception) {
                    null
                }
            }
        )
    }

    // Photo cropping dialog state
    var bitmapToCrop by remember { mutableStateOf<Bitmap?>(null) }
    var showPhotoOptionsDialog by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val decoded = BitmapFactory.decodeStream(stream)
                    if (decoded != null) {
                        bitmapToCrop = decoded
                    }
                }
            } catch (_: Exception) {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // Top Page Header
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Hosteller ID Card",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                ),
            )
            Text(
                text = "Your official digital hostel identity card.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.72f),
            )
        }

        // The ID Card View matching professional template
        IdCardView(
            studentName = studentName,
            roomNumber = roomNumber,
            rollNumber = rollNumber,
            photoBitmap = photoBitmap,
            onPhotoClick = {
                if (photoBitmap == null) {
                    photoPickerLauncher.launch("image/*")
                } else {
                    showPhotoOptionsDialog = true
                }
            },
            onPhotoLongClick = {
                showPhotoOptionsDialog = true
            },
        )

        // Actions: Upload / Adjust & Reset options
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = { photoPickerLauncher.launch("image/*") },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6366F1),
                    contentColor = Color.White,
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Create,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (photoBitmap == null) "Upload Photo" else "Change Photo",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
            }

            if (photoBitmap != null) {
                OutlinedButton(
                    onClick = {
                        try {
                            photoPath?.let { path ->
                                val currentBmp = BitmapFactory.decodeFile(path)
                                if (currentBmp != null) {
                                    bitmapToCrop = currentBmp
                                }
                            }
                        } catch (_: Exception) {}
                    },
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0x60818CF8)),
                    modifier = Modifier.height(50.dp),
                ) {
                    Text("Crop & Adjust", color = Color(0xFFC7D2FE), fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = {
                        UserSessionManager.clearIdCardPhoto(context, uid)
                        photoPath = null
                        photoBitmap = null
                    },
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0x40FF5252)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF8A80)),
                    modifier = Modifier.height(50.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        // Information notice
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = Color(0x221E1B4B),
            border = BorderStroke(1.dp, Color(0x35818CF8)),
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "💡 Tip: Tap or hold over the photo to change, crop, or align your portrait in the frame.",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.78f),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    // Photo options dialog (shown on long-press or tap)
    if (showPhotoOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showPhotoOptionsDialog = false },
            title = {
                Text("ID Card Photo Options", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            showPhotoOptionsDialog = false
                            photoPickerLauncher.launch("image/*")
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("📷 Choose New Photo from Gallery", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                    }

                    if (photoBitmap != null) {
                        TextButton(
                            onClick = {
                                showPhotoOptionsDialog = false
                                try {
                                    photoPath?.let { path ->
                                        val currentBmp = BitmapFactory.decodeFile(path)
                                        if (currentBmp != null) {
                                            bitmapToCrop = currentBmp
                                        }
                                    }
                                } catch (_: Exception) {}
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("✂ Crop & Adjust Frame Position", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                        }

                        TextButton(
                            onClick = {
                                showPhotoOptionsDialog = false
                                UserSessionManager.clearIdCardPhoto(context, uid)
                                photoPath = null
                                photoBitmap = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("🗑 Remove Photo", color = Color(0xFFFF5252), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPhotoOptionsDialog = false }) {
                    Text("Close")
                }
            },
        )
    }

    // Interactive Image Crop and Adjustment Dialog
    bitmapToCrop?.let { srcBitmap ->
        ImageCropDialog(
            sourceBitmap = srcBitmap,
            onDismiss = { bitmapToCrop = null },
            onCropConfirmed = { cropped ->
                val saved = UserSessionManager.saveIdCardPhotoBitmap(context, uid, cropped)
                if (saved != null) {
                    photoPath = saved
                    photoBitmap = cropped.asImageBitmap()
                }
                bitmapToCrop = null
            },
        )
    }
}

/**
 * Modern, professional ID Card view with accurate vertical ribbon,
 * auto-fitting typography, and barcode security finishing.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IdCardView(
    studentName: String?,
    roomNumber: String?,
    rollNumber: String? = null,
    photoBitmap: ImageBitmap?,
    onPhotoClick: () -> Unit,
    onPhotoLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Dynamic Name Extraction
    val rawName = studentName?.trim()?.takeIf { it.isNotEmpty() } ?: "Hosteller"
    val parts = rawName.split("\\s+".toRegex()).filter { it.isNotBlank() }
    val firstName = parts.firstOrNull()?.uppercase() ?: "HOSTELLER"
    val lastName = if (parts.size > 1) {
        parts.drop(1).joinToString(" ").uppercase()
    } else {
        "RESIDENT"
    }

    val displayRoom = roomNumber?.trim()?.takeIf { it.isNotEmpty() } ?: "N/A"
    val displayRoll = rollNumber?.trim()?.takeIf { it.isNotEmpty() } ?: "—"

    Card(
        modifier = modifier
            .width(320.dp)
            .height(540.dp)
            .shadow(elevation = 20.dp, shape = RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFD8DBE0)),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background isometric polygon facet accents
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                val facetPaint = Color(0xFFC7CBD1).copy(alpha = 0.40f)
                val lightFacetPaint = Color(0xFFE5E8EC).copy(alpha = 0.50f)

                // Top right facet
                drawPath(
                    path = Path().apply {
                        moveTo(w * 0.45f, 0f)
                        lineTo(w, 0f)
                        lineTo(w, h * 0.22f)
                        lineTo(w * 0.72f, h * 0.35f)
                        close()
                    },
                    color = lightFacetPaint,
                )

                // Middle right polygon facet
                drawPath(
                    path = Path().apply {
                        moveTo(w * 0.72f, h * 0.35f)
                        lineTo(w, h * 0.22f)
                        lineTo(w, h * 0.65f)
                        lineTo(w * 0.55f, h * 0.72f)
                        close()
                    },
                    color = facetPaint,
                )

                // Bottom facet
                drawPath(
                    path = Path().apply {
                        moveTo(0f, h * 0.75f)
                        lineTo(w * 0.65f, h * 0.85f)
                        lineTo(w * 0.65f, h)
                        lineTo(0f, h)
                        close()
                    },
                    color = lightFacetPaint,
                )
            }

            // Left vertical column (Dark Charcoal #37383B)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(88.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 28.dp,
                            bottomStart = 28.dp,
                            topEnd = 0.dp,
                            bottomEnd = 40.dp,
                        )
                    )
                    .background(Color(0xFF323336)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Modern Hexagonal Logo Badge at Top
                    Canvas(modifier = Modifier.size(36.dp)) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val r = size.minDimension * 0.46f

                        val hexPath = Path()
                        for (i in 0 until 6) {
                            val angleRad = Math.toRadians((60 * i - 30).toDouble())
                            val x = (cx + r * cos(angleRad)).toFloat()
                            val y = (cy + r * sin(angleRad)).toFloat()
                            if (i == 0) hexPath.moveTo(x, y) else hexPath.lineTo(x, y)
                        }
                        hexPath.close()

                        // Outer hexagon stroke
                        drawPath(
                            path = hexPath,
                            color = Color.White,
                            style = Stroke(width = 2.8.dp.toPx()),
                        )

                        // Inner geometric diamond
                        val innerR = r * 0.45f
                        val innerPath = Path()
                        for (i in 0 until 6) {
                            val angleRad = Math.toRadians((60 * i - 30).toDouble())
                            val x = (cx + innerR * cos(angleRad)).toFloat()
                            val y = (cy + innerR * sin(angleRad)).toFloat()
                            if (i == 0) innerPath.moveTo(x, y) else innerPath.lineTo(x, y)
                        }
                        innerPath.close()
                        drawPath(
                            path = innerPath,
                            color = Color.White,
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Continuous Vertical Text "HOSTELLER" (Fixes word-wrap bug)
                    Box(
                        modifier = Modifier
                            .weight(8f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "HOSTELLER",
                            color = Color.White,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.SansSerif,
                            letterSpacing = 7.sp,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier
                                .wrapContentSize(unbounded = true)
                                .graphicsLayer {
                                    rotationZ = -90f
                                },
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Subtle microchip security accent
                    Canvas(modifier = Modifier.size(22.dp)) {
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.25f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
                            style = Stroke(width = 1.dp.toPx()),
                        )
                    }
                }
            }

            // Right Main Body Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 88.dp),
                horizontalAlignment = Alignment.End,
            ) {
                // Top Header: CGIT BOYS HOSTEL
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 22.dp, end = 20.dp),
                ) {
                    Text(
                        text = "CGIT BOYS",
                        fontSize = 27.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.SansSerif,
                        color = Color(0xFF101010),
                        lineHeight = 27.sp,
                        letterSpacing = (-0.5).sp,
                    )
                    Text(
                        text = "HOSTEL",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.SansSerif,
                        color = Color(0xFF101010),
                        letterSpacing = 0.5.sp,
                    )
                    Text(
                        text = "RESIDENT IDENTITY CARD",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF555555),
                        letterSpacing = 1.sp,
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Photo Area: Centered in the right pane with hold-to-edit interaction
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(175.dp)
                            .shadow(elevation = 10.dp, shape = RoundedCornerShape(26.dp))
                            .clip(RoundedCornerShape(26.dp))
                            .background(Color(0xFFFBFBFB))
                            .border(BorderStroke(4.dp, Color.White), RoundedCornerShape(26.dp))
                            .combinedClickable(
                                onClick = onPhotoClick,
                                onLongClick = onPhotoLongClick,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (photoBitmap != null) {
                            Image(
                                bitmap = photoBitmap,
                                contentDescription = "Hosteller Photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            // Mountain & Sun Graphic Placeholder matching reference
                            DefaultIdPhotoGraphic(modifier = Modifier.fillMaxSize())
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Student Details (Auto-fitted Name, Room Number, Roll Number)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 16.dp, bottom = 14.dp),
                    horizontalAlignment = Alignment.Start,
                ) {
                    // First Name with auto font size
                    val firstNameSize = if (firstName.length > 10) 24.sp else 30.sp
                    Text(
                        text = firstName,
                        fontSize = firstNameSize,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF383838),
                        fontFamily = FontFamily.SansSerif,
                        lineHeight = firstNameSize,
                    )

                    // Last Name with auto font size
                    val lastNameSize = if (lastName.length > 11) 22.sp else 28.sp
                    Text(
                        text = lastName,
                        fontSize = lastNameSize,
                        fontWeight = FontWeight.Black,
                        fontStyle = FontStyle.Italic,
                        color = Color(0xFF101010),
                        fontFamily = FontFamily.SansSerif,
                        lineHeight = lastNameSize,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Room & Roll Number
                    Text(
                        text = "ROOM NO - $displayRoom",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF222222),
                        letterSpacing = 1.1.sp,
                    )
                    if (displayRoll != "—") {
                        Text(
                            text = "ROLL NO - #$displayRoll",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF454545),
                            letterSpacing = 0.8.sp,
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Decorative ID card Barcode strip at bottom
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .height(16.dp),
                    ) {
                        val barColor = Color(0xFF202020)
                        val totalW = size.width
                        var curX = 0f
                        val pattern = listOf(2, 4, 1, 3, 2, 5, 1, 4, 2, 1, 3, 5, 2, 4, 1, 3, 2, 4, 1, 5, 3, 2, 4, 1, 3, 2)
                        for (w in pattern) {
                            val barW = w.dp.toPx()
                            if (curX + barW > totalW) break
                            drawRect(
                                color = barColor,
                                topLeft = Offset(curX, 0f),
                                size = Size(barW * 0.65f, size.height),
                            )
                            curX += barW + 1.5.dp.toPx()
                        }
                    }
                }
            }
        }
    }
}

/**
 * Renders the mountain and sun placeholder graphic matching the reference card.
 */
@Composable
private fun DefaultIdPhotoGraphic(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Sun
        drawCircle(
            color = Color(0xFFFFCC33),
            radius = w * 0.16f,
            center = Offset(w * 0.72f, h * 0.32f),
        )

        // Left big mountain
        val leftMountain = Path().apply {
            moveTo(w * 0.05f, h * 0.95f)
            lineTo(w * 0.42f, h * 0.40f)
            lineTo(w * 0.78f, h * 0.95f)
            close()
        }
        drawPath(leftMountain, color = Color(0xFF6BA4F8))

        // Right smaller mountain
        val rightMountain = Path().apply {
            moveTo(w * 0.45f, h * 0.95f)
            lineTo(w * 0.75f, h * 0.48f)
            lineTo(w * 0.98f, h * 0.95f)
            close()
        }
        drawPath(rightMountain, color = Color(0xFF8BBDFB))
    }
}
