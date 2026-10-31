package com.hostelattendance.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

enum class CalendarSelectionMode {
    START_DATE,
    END_DATE,
}

@Composable
fun ArchedGlassCalendar(
    startDate: LocalDate,
    endDate: LocalDate,
    onDateRangeSelected: (LocalDate, LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    maxRangeDays: Int = 15,
) {
    var activeMode by remember { mutableStateOf(CalendarSelectionMode.START_DATE) }
    val activeDate = if (activeMode == CalendarSelectionMode.START_DATE) startDate else endDate

    // Week offset centered around active date (7 days window)
    var centerDate by remember { mutableStateOf(activeDate) }

    LaunchedEffect(activeDate) {
        if (ChronoUnit.DAYS.between(centerDate, activeDate) !in -3..3) {
            centerDate = activeDate
        }
    }

    val monthName = activeDate.format(DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH))
    val dayNumber = activeDate.dayOfMonth.toString()

    // 7 visible days centered around centerDate
    val visibleDates = remember(centerDate) {
        (-3..3).map { centerDate.plusDays(it.toLong()) }
    }

    val backdrop = LocalGlobalGlassBackdrop.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val context = androidx.compose.ui.platform.LocalContext.current

    val calendarModifier = if (backdrop != null) {
        modifier
            .fillMaxWidth()
            .liquidGlassEffect(
                backdrop = backdrop,
                density = density,
                cornerRadius = 24.dp,
                blurRadius = 14.dp,
                refractionHeightPx = 14f,
                refractionAmountPx = 20f,
                surfaceColor = Color(0x38191624),
            )
            .padding(16.dp)
    } else {
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0x551E1C2B))
            .border(1.dp, Color(0x28FFFFFF), RoundedCornerShape(24.dp))
            .padding(16.dp)
    }

    Column(
        modifier = calendarModifier,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Mode Selector: [ Start Date ] [ End Date ]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0x22000000))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val startSelected = activeMode == CalendarSelectionMode.START_DATE
            val startBg by animateColorAsState(
                targetValue = if (startSelected) Color(0xD024222E) else Color.Transparent,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "startBg",
            )
            val startText by animateColorAsState(
                targetValue = if (startSelected) Color.White else Color(0xFF8E8E93),
                label = "startText",
            )
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = startBg,
                border = if (startSelected) BorderStroke(1.dp, Color(0x35FFFFFF)) else null,
                modifier = Modifier
                    .weight(1f)
                    .clickable { activeMode = CalendarSelectionMode.START_DATE },
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Start: ",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFE91E63),
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = startDate.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)),
                        style = MaterialTheme.typography.labelMedium,
                        color = startText,
                        fontWeight = if (startSelected) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }

            val endSelected = activeMode == CalendarSelectionMode.END_DATE
            val endBg by animateColorAsState(
                targetValue = if (endSelected) Color(0xD024222E) else Color.Transparent,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "endBg",
            )
            val endText by animateColorAsState(
                targetValue = if (endSelected) Color.White else Color(0xFF8E8E93),
                label = "endText",
            )
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = endBg,
                border = if (endSelected) BorderStroke(1.dp, Color(0x35FFFFFF)) else null,
                modifier = Modifier
                    .weight(1f)
                    .clickable { activeMode = CalendarSelectionMode.END_DATE },
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "End: ",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFFF5722),
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = endDate.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)),
                        style = MaterialTheme.typography.labelMedium,
                        color = endText,
                        fontWeight = if (endSelected) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }

        // Prominent Header: Month and Day (e.g. "August 23")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        android.app.DatePickerDialog(
                            context,
                            { _, year, month, day ->
                                val pickedDate = LocalDate.of(year, month + 1, day)
                                if (activeMode == CalendarSelectionMode.START_DATE) {
                                    var newEnd = endDate
                                    if (newEnd.isBefore(pickedDate)) {
                                        newEnd = pickedDate
                                    } else if (ChronoUnit.DAYS.between(pickedDate, newEnd) >= maxRangeDays) {
                                        newEnd = pickedDate.plusDays((maxRangeDays - 1).toLong())
                                    }
                                    onDateRangeSelected(pickedDate, newEnd)
                                } else {
                                    var newStart = startDate
                                    if (pickedDate.isBefore(newStart)) {
                                        newStart = pickedDate
                                    } else if (ChronoUnit.DAYS.between(newStart, pickedDate) >= maxRangeDays) {
                                        newStart = pickedDate.minusDays((maxRangeDays - 1).toLong())
                                    }
                                    onDateRangeSelected(newStart, pickedDate)
                                }
                                centerDate = pickedDate
                            },
                            activeDate.year,
                            activeDate.monthValue - 1,
                            activeDate.dayOfMonth,
                        ).show()
                    }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                AnimatedContent(
                    targetState = monthName,
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(180)) },
                    label = "monthName",
                ) { m ->
                    Text(
                        text = m,
                        style = MaterialTheme.typography.headlineLarge.copy(fontSize = 32.sp),
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
                AnimatedContent(
                    targetState = dayNumber,
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(180)) },
                    label = "dayNumber",
                ) { d ->
                    Text(
                        text = d,
                        style = MaterialTheme.typography.headlineLarge.copy(fontSize = 32.sp),
                        fontWeight = FontWeight.Normal,
                        color = Color.White.copy(alpha = 0.92f),
                    )
                }
            }

            // Week Navigation Arrows
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Surface(
                    shape = CircleShape,
                    color = Color(0x24FFFFFF),
                    modifier = Modifier
                        .size(36.dp)
                        .clickable { centerDate = centerDate.minusWeeks(1) },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("‹", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = Color(0x24FFFFFF),
                    modifier = Modifier
                        .size(36.dp)
                        .clickable { centerDate = centerDate.plusWeeks(1) },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("›", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Arched Glass Horizon Ribbon Track with Weekdays and Dates
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Arched Ribbon Canvas Background
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                val ribbonTop = h * 0.42f
                val ribbonBottom = h * 0.88f
                val archElevation = 14.dp.toPx()

                // Top curved path
                val topPath = Path().apply {
                    moveTo(0f, ribbonTop)
                    quadraticTo(w / 2f, ribbonTop - archElevation, w, ribbonTop)
                }

                // Bottom curved path
                val bottomPath = Path().apply {
                    moveTo(0f, ribbonBottom)
                    quadraticTo(w / 2f, ribbonBottom - archElevation, w, ribbonBottom)
                }

                // Filled arched ribbon band
                val ribbonShape = Path().apply {
                    moveTo(0f, ribbonTop)
                    quadraticTo(w / 2f, ribbonTop - archElevation, w, ribbonTop)
                    lineTo(w, ribbonBottom)
                    quadraticTo(w / 2f, ribbonBottom - archElevation, 0f, ribbonBottom)
                    close()
                }

                // Soft glass fill
                drawPath(
                    path = ribbonShape,
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0x35FFFFFF), Color(0x12FFFFFF)),
                    ),
                )

                // Highlight stroke on top curve
                drawPath(
                    path = topPath,
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color(0x20FFFFFF), Color(0x75FFFFFF), Color(0x20FFFFFF)),
                    ),
                    style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round),
                )

                // Subtle bottom contour line
                drawPath(
                    path = bottomPath,
                    color = Color(0x18FFFFFF),
                    style = Stroke(width = 1.dp.toPx()),
                )
            }

            // 7 Days positioned along the curve
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                visibleDates.forEachIndexed { index, date ->
                    // Curvature vertical offset (arch peak in center: index = 3)
                    val normalizedDist = (index - 3) / 3f
                    val archOffsetDp = (-8f * (1f - normalizedDist * normalizedDist)).dp

                    val isSelected = (date == activeDate)
                    val isInRange = !date.isBefore(startDate) && !date.isAfter(endDate)
                    val isToday = (date == LocalDate.now())

                    val dayOfWeek = date.format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH))
                    val dayNum = date.dayOfMonth.toString()

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .offset(y = archOffsetDp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                if (activeMode == CalendarSelectionMode.START_DATE) {
                                    val newStart = date
                                    var newEnd = endDate
                                    if (newEnd.isBefore(newStart)) {
                                        newEnd = newStart
                                    } else if (ChronoUnit.DAYS.between(newStart, newEnd) >= maxRangeDays) {
                                        newEnd = newStart.plusDays((maxRangeDays - 1).toLong())
                                    }
                                    onDateRangeSelected(newStart, newEnd)
                                } else {
                                    val newEnd = date
                                    var newStart = startDate
                                    if (newEnd.isBefore(newStart)) {
                                        newStart = newEnd
                                    } else if (ChronoUnit.DAYS.between(newStart, newEnd) >= maxRangeDays) {
                                        newStart = newEnd.minusDays((maxRangeDays - 1).toLong())
                                    }
                                    onDateRangeSelected(newStart, newEnd)
                                }
                            },
                    ) {
                        // Weekday label
                        Text(
                            text = dayOfWeek,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else Color(0xFF9E9E9E),
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Date Pill / Glowing Circle
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .shadow(8.dp, CircleShape, spotColor = Color(0xFFFF5722))
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(
                                                Color(0xFFD81B60), // Vibrant Magenta
                                                Color(0xFFFF5722), // Glowing Coral Orange
                                            ),
                                        ),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = dayNum,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isInRange) Color(0x35E91E63) else Color.Transparent,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = dayNum,
                                    fontSize = 14.sp,
                                    fontWeight = if (isInRange) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isInRange) Color(0xFFFF80AB) else Color.White.copy(alpha = 0.85f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
