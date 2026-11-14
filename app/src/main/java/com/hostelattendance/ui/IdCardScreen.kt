package com.hostelattendance.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hostelattendance.data.local.UserSessionManager
import kotlin.math.cos
import kotlin.math.sin

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

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            val saved = UserSessionManager.saveIdCardPhotoFromUri(context, uid, uri)
            if (saved != null) {
                photoPath = saved
                try {
                    photoBitmap = BitmapFactory.decodeFile(saved)?.asImageBitmap()
                } catch (_: Exception) {}
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
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

        // The ID Card View matching the reference design
        IdCardView(
            studentName = studentName,
            roomNumber = roomNumber,
            photoBitmap = photoBitmap,
            onPhotoClick = { photoPickerLauncher.launch("image/*") },
        )

        // Actions: Upload photo & Remove photo options
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
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
                        UserSessionManager.clearIdCardPhoto(context, uid)
                        photoPath = null
                        photoBitmap = null
                    },
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.2.dp, Color(0xFFF43F5E).copy(alpha = 0.8f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFFDA4AF),
                    ),
                    modifier = Modifier.height(50.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reset", fontWeight = FontWeight.SemiBold)
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
                    text = "💡 Tip: Click near or directly on the photo container to upload your portrait from your gallery.",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.75f),
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun IdCardView(
    studentName: String?,
    roomNumber: String?,
    photoBitmap: ImageBitmap?,
    onPhotoClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Dynamic Name Extraction
    val rawName = studentName?.trim()?.takeIf { it.isNotEmpty() } ?: "Hosteller"
    val parts = rawName.split("\\s+".toRegex())
    val firstName = parts.firstOrNull()?.uppercase() ?: "HOSTELLER"
    val lastName = if (parts.size > 1) {
        parts.drop(1).joinToString(" ").uppercase()
    } else {
        "RESIDENT"
    }

    val displayRoom = roomNumber?.trim()?.takeIf { it.isNotEmpty() } ?: "N/A"

    Card(
        modifier = modifier
            .width(320.dp)
            .height(540.dp)
            .shadow(elevation = 18.dp, shape = RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFD6D9DE)),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background geometric polygon facets on the right side
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Draw subtle isometric hexagon facet accents
                val facetPaint = Color(0xFFC7CBD1).copy(alpha = 0.45f)
                val lightFacetPaint = Color(0xFFE5E8EC).copy(alpha = 0.55f)

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
                    .width(92.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 28.dp,
                            bottomStart = 28.dp,
                            topEnd = 0.dp,
                            bottomEnd = 40.dp,
                        )
                    )
                    .background(Color(0xFF37383B)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 24.dp),
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
                            style = Stroke(width = 3.dp.toPx()),
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

                    // Vertical Rotated Text "HOSTELLER" reading upwards from H to R
                    Box(
                        modifier = Modifier
                            .weight(8f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "HOSTELLER",
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.SansSerif,
                            letterSpacing = 7.sp,
                            modifier = Modifier.graphicsLayer {
                                rotationZ = -90f
                            },
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))
                }
            }

            // Right Main Body Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 92.dp),
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
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.SansSerif,
                        color = Color(0xFF101010),
                        lineHeight = 28.sp,
                        letterSpacing = (-0.5).sp,
                    )
                    Text(
                        text = "HOSTEL",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.SansSerif,
                        color = Color(0xFF101010),
                        letterSpacing = 0.5.sp,
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Photo Area: Centered in the right pane with clickable interaction
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(175.dp)
                            .shadow(elevation = 8.dp, shape = RoundedCornerShape(26.dp))
                            .clip(RoundedCornerShape(26.dp))
                            .background(Color(0xFFFBFBFB))
                            .border(BorderStroke(5.dp, Color.White), RoundedCornerShape(26.dp))
                            .clickable { onPhotoClick() },
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

                    // Camera / Upload badge button near the image
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 6.dp, y = 6.dp)
                            .size(38.dp)
                            .shadow(elevation = 4.dp, shape = CircleShape)
                            .clip(CircleShape)
                            .background(Color(0xFF37383B))
                            .border(BorderStroke(2.dp, Color.White), CircleShape)
                            .clickable { onPhotoClick() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Create,
                            contentDescription = "Upload Photo",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Student Details (Name and Room Number)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 22.dp, end = 16.dp, bottom = 26.dp),
                    horizontalAlignment = Alignment.Start,
                ) {
                    Text(
                        text = firstName,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF404040),
                        fontFamily = FontFamily.SansSerif,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 36.sp,
                    )
                    Text(
                        text = lastName,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        fontStyle = FontStyle.Italic,
                        color = Color(0xFF101010),
                        fontFamily = FontFamily.SansSerif,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 32.sp,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "ROOM NUMBER- $displayRoom",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF202020),
                        letterSpacing = 1.2.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
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
        val mountainLeft = Path().apply {
            moveTo(0f, h * 0.85f)
            lineTo(w * 0.38f, h * 0.42f)
            lineTo(w * 0.76f, h * 0.85f)
            lineTo(w * 0.76f, h)
            lineTo(0f, h)
            close()
        }
        drawPath(
            path = mountainLeft,
            color = Color(0xFF64B5F6),
        )

        // Right smaller mountain
        val mountainRight = Path().apply {
            moveTo(w * 0.35f, h * 0.85f)
            lineTo(w * 0.65f, h * 0.50f)
            lineTo(w, h * 0.88f)
            lineTo(w, h)
            lineTo(w * 0.35f, h)
            close()
        }
        drawPath(
            path = mountainRight,
            color = Color(0xFF42A5F5),
        )
    }
}
