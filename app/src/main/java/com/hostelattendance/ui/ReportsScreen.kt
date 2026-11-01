package com.hostelattendance.ui

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.hostelattendance.HostelAttendanceApplication
import com.hostelattendance.data.export.AttendanceReportPdfGenerator
import com.hostelattendance.data.local.AttendanceRecordEntity
import com.hostelattendance.data.local.StudentEntity
import com.hostelattendance.data.repository.AttendanceRepository
import com.hostelattendance.domain.AttendanceRules
import com.hostelattendance.domain.AttendanceStatus
import kotlinx.coroutines.launch
import android.widget.Toast
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val dateButtonFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

private enum class ReportScope(val label: String) {
    HOSTEL("Whole hostel"),
    WING("Individual wing"),
    STUDENT("Individual student"),
}

@Composable
fun ReportsScreen(
    hostelId: String = "local-installation",
    isAdmin: Boolean = true,
) {
    val context = LocalContext.current
    val application = context.applicationContext as HostelAttendanceApplication
    ReportsScreen(
        hostelId = hostelId,
        isAdmin = isAdmin,
        repository = application.attendanceRepository,
    )
}

@Composable
fun ReportsScreen(
    hostelId: String,
    isAdmin: Boolean = true,
    repository: AttendanceRepository,
) {
    val context = LocalContext.current
    val application = context.applicationContext as HostelAttendanceApplication
    val today = remember { AttendanceRules.today() }
    var startDateString by rememberSaveable {
        mutableStateOf(today.minusDays(6).toString())
    }
    var endDateString by rememberSaveable {
        mutableStateOf(today.toString())
    }
    val startDate = remember(startDateString) {
        try { LocalDate.parse(startDateString) } catch (_: Exception) { today.minusDays(6) }
    }
    val endDate = remember(endDateString) {
        try { LocalDate.parse(endDateString) } catch (_: Exception) { today }
    }

    var selectedScopeName by rememberSaveable { mutableStateOf(ReportScope.HOSTEL.name) }
    val selectedScope = remember(selectedScopeName) {
        ReportScope.entries.find { it.name == selectedScopeName } ?: ReportScope.HOSTEL
    }
    val wings by repository.observeWings(hostelId).collectAsState(initial = emptyList())
    var selectedWingId by rememberSaveable { mutableStateOf("") }
    var wingDropdownExpanded by remember { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSuggestionTrayVisible by remember { mutableStateOf(false) }
    var selectedRecordForSideOption by remember { mutableStateOf<AttendanceRecordEntity?>(null) }
    val scope = rememberCoroutineScope()
    var studentToEdit by remember { mutableStateOf<StudentEntity?>(null) }

    val allStudents by application.studentRepository.observeAllStudents(hostelId)
        .collectAsState(initial = emptyList())

    val studentSuggestions = remember(searchQuery, allStudents) {
        val q = searchQuery.trim()
        if (q.isBlank()) {
            emptyList()
        } else {
            allStudents
                .filter { student ->
                    student.studentName.contains(q, ignoreCase = true) ||
                        student.currentRoomNumber.contains(q, ignoreCase = true) ||
                        student.rollNumber.contains(q, ignoreCase = true)
                }
                .sortedWith(
                    compareBy(
                        { !it.studentName.startsWith(q, ignoreCase = true) && !it.currentRoomNumber.startsWith(q, ignoreCase = true) },
                        { it.currentRoomNumber },
                        { it.studentName },
                    ),
                )
                .take(6)
        }
    }

    LaunchedEffect(wings.isEmpty()) {
        if (wings.isEmpty()) {
            com.hostelattendance.data.sync.FirestoreSyncService.restoreWingsAndStudentsFromFirestore(
                hostelId,
                application.database.wingDao(),
                application.database.studentDao(),
            )
            val currentStudents = application.database.studentDao().getAllStudents(hostelId)
            if (currentStudents.isEmpty()) {
                com.hostelattendance.data.seed.DatabaseSeeder.seedIfNeeded(context, application.database, hostelId)
            }
        }
    }

    val rawRecords by repository.observeRecordsForDateRange(hostelId, startDate.toString(), endDate.toString())
        .collectAsState(initial = emptyList())

    LaunchedEffect(hostelId, startDateString, endDateString) {
        com.hostelattendance.data.sync.FirestoreSyncService.restoreAttendanceForDateRange(
            hostelId = hostelId,
            startDate = startDate.toString(),
            endDate = endDate.toString(),
            sessionDao = application.database.attendanceSessionDao(),
        )
    }

    val filteredRecords = remember(rawRecords, selectedScope, selectedWingId, searchQuery) {
        rawRecords.filter { record ->
            val matchesScope = when (selectedScope) {
                ReportScope.HOSTEL -> true
                ReportScope.WING -> selectedWingId.isBlank() || record.wingIdSnapshot == selectedWingId
                ReportScope.STUDENT -> true
            }
            val matchesSearch = searchQuery.isBlank() ||
                record.rollNumberSnapshot.contains(searchQuery.trim(), ignoreCase = true) ||
                record.studentNameSnapshot.contains(searchQuery.trim(), ignoreCase = true) ||
                record.roomNumberSnapshot.contains(searchQuery.trim(), ignoreCase = true)

            matchesScope && matchesSearch
        }
    }

    val studentsForPdf = remember(allStudents, filteredRecords, selectedScope, selectedWingId, searchQuery) {
        val baseList = if (allStudents.isNotEmpty()) {
            allStudents
        } else {
            filteredRecords.distinctBy { it.studentId }.map { rec ->
                com.hostelattendance.data.local.StudentEntity(
                    studentId = rec.studentId,
                    hostelId = rec.hostelId,
                    rollNumber = rec.rollNumberSnapshot,
                    normalizedRollNumber = rec.rollNumberSnapshot,
                    currentWingId = rec.wingIdSnapshot,
                    currentRoomNumber = rec.roomNumberSnapshot,
                    studentName = rec.studentNameSnapshot,
                    branch = null,
                    year = null,
                    isActive = true,
                    createdAtMillis = 0L,
                    updatedAtMillis = 0L,
                )
            }
        }
        baseList.filter { student ->
            val matchesScope = when (selectedScope) {
                ReportScope.HOSTEL -> true
                ReportScope.WING -> selectedWingId.isBlank() || student.currentWingId == selectedWingId
                ReportScope.STUDENT -> true
            }
            val matchesSearch = searchQuery.isBlank() ||
                student.rollNumber.contains(searchQuery.trim(), ignoreCase = true) ||
                student.studentName.contains(searchQuery.trim(), ignoreCase = true) ||
                student.currentRoomNumber.contains(searchQuery.trim(), ignoreCase = true)

            matchesScope && matchesSearch
        }
    }

    val daysInRange = (ChronoUnit.DAYS.between(startDate, endDate) + 1).toInt().coerceAtLeast(1)
    val isSingleDay = startDate == endDate || daysInRange == 1
    val studentCount = studentsForPdf.size.coerceAtLeast(1)
    val totalStudentDays = studentCount * daysInRange

    val presentCount = filteredRecords.count { it.status == AttendanceStatus.PRESENT.name }
    val leaveCount = filteredRecords.count { it.status == AttendanceStatus.LEAVE.name }
    val effectiveAbsentCount = (totalStudentDays - presentCount - leaveCount).coerceAtLeast(0)
    val notMarkedCount = filteredRecords.count { it.status == AttendanceStatus.NOT_MARKED.name }
    val totalRecords = filteredRecords.size
    val singleDayRate = if (studentCount > 0) ((presentCount * 100) / studentCount).coerceIn(0, 100) else 0
    val dailyAvgRate = if (totalStudentDays > 0) ((presentCount * 100) / totalStudentDays).coerceIn(0, 100) else 0

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        item(key = "reports_header") {
            Column {
                Text(
                    text = "Attendance Reports",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${startDate.format(dateButtonFormatter)} – ${endDate.format(dateButtonFormatter)} · ${selectedScope.label}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        // Filters Panel
        item(key = "reports_filters") {
            GlassPanel(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Scope selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ReportScope.entries.forEach { scope ->
                        val isSelected = selectedScope == scope
                        val tabInteraction = rememberMotionInteractionSource()
                        val containerColor by animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color(0x33FFFFFF),
                            animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
                            label = "scopeTabColor_${scope.name}",
                        )
                        val textColor by animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
                            label = "scopeTextColor_${scope.name}",
                        )
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = containerColor,
                            modifier = Modifier
                                .weight(1f)
                                .pressFeedback(tabInteraction, pressedScale = 0.94f)
                                .clickable(
                                    interactionSource = tabInteraction,
                                    indication = null,
                                ) {
                                    selectedScopeName = scope.name
                                    if (scope != ReportScope.STUDENT) {
                                        searchQuery = ""
                                        isSuggestionTrayVisible = false
                                    }
                                },
                        ) {
                            Text(
                                text = when (scope) {
                                    ReportScope.HOSTEL -> "Hostel"
                                    ReportScope.WING -> "Wing"
                                    ReportScope.STUDENT -> "Student"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = textColor,
                                modifier = Modifier.padding(vertical = 9.dp, horizontal = 4.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                }

                // Wing selector if Scope == WING
                AnimatedVisibility(
                    visible = selectedScope == ReportScope.WING,
                    enter = expandVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)) + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        val wingSelectInteraction = rememberMotionInteractionSource()
                        OutlinedButton(
                            onClick = { wingDropdownExpanded = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .pressFeedback(wingSelectInteraction),
                            interactionSource = wingSelectInteraction,
                            shape = RoundedCornerShape(18.dp),
                        ) {
                            val activeWingName = wings.firstOrNull { it.wingId == selectedWingId }?.name ?: "All Wings"
                            Text("Wing: $activeWingName")
                        }
                        DropdownMenu(
                            expanded = wingDropdownExpanded,
                            onDismissRequest = { wingDropdownExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("All Wings") },
                                onClick = {
                                    selectedWingId = ""
                                    wingDropdownExpanded = false
                                },
                            )
                            wings.forEach { wing ->
                                DropdownMenuItem(
                                    text = { Text(wing.name) },
                                    onClick = {
                                        selectedWingId = wing.wingId
                                        wingDropdownExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }

                // Search field + Autocomplete Suggestion Tray - ONLY visible when Scope == STUDENT!
                AnimatedVisibility(
                    visible = selectedScope == ReportScope.STUDENT,
                    enter = expandVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)) + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                isSuggestionTrayVisible = it.isNotBlank()
                            },
                            placeholder = { Text("Search by student name or room number") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0x22FFFFFF),
                                        modifier = Modifier
                                            .padding(end = 8.dp)
                                            .clickable {
                                                searchQuery = ""
                                                isSuggestionTrayVisible = false
                                            },
                                    ) {
                                        Text(
                                            text = "✕",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        )

                        // Option Tray (Suggestions matching name or room number)
                        AnimatedVisibility(
                            visible = isSuggestionTrayVisible && studentSuggestions.isNotEmpty(),
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xF21F1C28),
                                border = BorderStroke(1.dp, Color(0x40FFFFFF)),
                                shadowElevation = 10.dp,
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                ) {
                                    studentSuggestions.forEach { student ->
                                        val wingName = wings.firstOrNull { it.wingId == student.currentWingId }?.name ?: "Wing"
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    searchQuery = student.studentName
                                                    isSuggestionTrayVisible = false
                                                }
                                                .padding(horizontal = 14.dp, vertical = 9.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = student.studentName,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                )
                                                Text(
                                                    text = "Roll: ${student.rollNumber} · $wingName",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                                            ) {
                                                Text(
                                                    text = "Room ${student.currentRoomNumber}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Summary Stats Grid
        item(key = "reports_stats") {
            GlassPanel(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    ReportStatItem(label = "Present", count = presentCount, color = Color(0xFF4CAF50))
                    ReportStatItem(label = "Absent", count = effectiveAbsentCount, color = Color(0xFFE53935))
                    ReportStatItem(label = "Leave", count = leaveCount, color = Color(0xFFFFA000))
                    if (isSingleDay) {
                        ReportStatItem(label = "Attendance", count = "$singleDayRate%", color = MaterialTheme.colorScheme.primary)
                    } else if (selectedScope == ReportScope.STUDENT || studentsForPdf.size == 1) {
                        ReportStatItem(label = "Days Present", count = "$presentCount / $daysInRange", color = MaterialTheme.colorScheme.primary)
                    } else {
                        ReportStatItem(label = "Daily Avg", count = "$dailyAvgRate%", color = MaterialTheme.colorScheme.primary)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val subtitleStats = if (isSingleDay) {
                        "$studentCount residents · $presentCount present · $effectiveAbsentCount absent"
                    } else if (selectedScope == ReportScope.STUDENT || studentsForPdf.size == 1) {
                        val sName = studentsForPdf.firstOrNull()?.studentName ?: "Student"
                        "$sName: $presentCount days present out of $daysInRange days"
                    } else {
                        "$totalStudentDays student-days ($daysInRange days · $effectiveAbsentCount absent)"
                    }
                    Text(
                        text = subtitleStats,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val exportPdfInteraction = rememberMotionInteractionSource()
                    Button(
                        onClick = {
                            AttendanceReportPdfGenerator().generateAndSharePdf(
                                context = context,
                                hostelId = hostelId,
                                startDate = startDate,
                                endDate = endDate,
                                students = studentsForPdf,
                                records = filteredRecords,
                                wingName = wings.firstOrNull { it.wingId == selectedWingId }?.name,
                                filterDescription = if (searchQuery.isNotBlank()) "Search: $searchQuery" else null,
                            )
                        },
                        enabled = studentsForPdf.isNotEmpty(),
                        interactionSource = exportPdfInteraction,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.pressFeedback(exportPdfInteraction),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary,
                        ),
                    ) {
                        Text("Export PDF", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // SECTION: Select Report Date Range (Arched Liquid Glass Calendar)
        item(key = "reports_date_range") {
            ArchedGlassCalendar(
                startDate = startDate,
                endDate = endDate,
                onDateRangeSelected = { newStart, newEnd ->
                    startDateString = newStart.toString()
                    endDateString = newEnd.toString()
                },
                maxRangeDays = 15,
            )
        }

        // Attendance Register List Header
        item(key = "reports_list_header") {
            Text(
                text = "Records (${filteredRecords.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        // Attendance Register List Items
        if (filteredRecords.isEmpty()) {
            item(key = "reports_empty") {
                GlassPanel(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(24.dp),
                ) {
                    Text(
                        text = "No saved attendance records found for this selection.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
            }
        } else {
            items(
                filteredRecords,
                key = { it.recordId },
                contentType = { "report_row" },
            ) { record ->
                AttendanceReportRow(
                    record = record,
                    modifier = Modifier.animateItem(),
                    onLongClick = {
                        selectedRecordForSideOption = record
                    },
                )
            }
        }

        item(key = "reports_bottom_spacer") {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    AnimatedVisibility(
        visible = selectedRecordForSideOption != null,
        enter = fadeIn(tween(140)),
        exit = fadeOut(tween(120)),
    ) {
        val targetRecord = selectedRecordForSideOption ?: return@AnimatedVisibility
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.58f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { selectedRecordForSideOption = null },
                ),
            contentAlignment = Alignment.CenterEnd,
        ) {
            val backdrop = LocalGlobalGlassBackdrop.current
            val density = androidx.compose.ui.platform.LocalDensity.current
            val sidePanelShape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp)

            val panelModifier = if (backdrop != null) {
                Modifier
                    .fillMaxHeight()
                    .widthIn(min = 280.dp, max = 340.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .liquidGlassEffect(
                        backdrop = backdrop,
                        density = density,
                        cornerRadius = 28.dp,
                        blurRadius = 18.dp,
                        refractionHeightPx = 18f,
                        refractionAmountPx = 24f,
                        surfaceColor = Color(0x401E1A29),
                        specular = com.styropyr0.prismal.specular.PrismalSpecular(
                            width = 1.dp,
                            alpha = 0.85f,
                            style = com.styropyr0.prismal.specular.PrismalSpecularStyle.Ambient,
                        ),
                        depthShadow = com.styropyr0.prismal.depth.PrismalDepthShadow(
                            radius = 28.dp,
                            color = Color.Black.copy(alpha = 0.50f),
                        ),
                    )
                    .padding(20.dp)
                    .statusBarsPadding()
                    .navigationBarsPadding()
            } else {
                Modifier
                    .fillMaxHeight()
                    .widthIn(min = 280.dp, max = 340.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .shadow(
                        elevation = 28.dp,
                        shape = sidePanelShape,
                        ambientColor = Color.Black.copy(alpha = 0.65f),
                        spotColor = Color.Black.copy(alpha = 0.85f),
                    )
                    .clip(sidePanelShape)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xF02B2636),
                                Color(0xF51E1A27),
                                Color(0xF815121D),
                            ),
                        ),
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0x99FFFFFF),
                                Color(0x30FFFFFF),
                                Color(0x0CFFFFFF),
                            ),
                        ),
                        shape = sidePanelShape,
                    )
                    .padding(20.dp)
                    .statusBarsPadding()
                    .navigationBarsPadding()
            }

            Box(
                modifier = panelModifier,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Student Options",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0x25FFFFFF),
                                modifier = Modifier.clickable { selectedRecordForSideOption = null },
                            ) {
                                Text(
                                    text = "✕",
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0x20FFFFFF),
                            border = BorderStroke(1.dp, Color(0x28FFFFFF)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = targetRecord.studentNameSnapshot,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                                Text(
                                    text = "#" + targetRecord.rollNumberSnapshot + " · Room " + targetRecord.roomNumberSnapshot,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "Wing: " + targetRecord.wingNameSnapshot,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        val studentOptInteraction = rememberMotionInteractionSource()
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .pressFeedback(studentOptInteraction)
                                .clickable(
                                    interactionSource = studentOptInteraction,
                                    indication = null,
                                ) {
                                    selectedScopeName = ReportScope.STUDENT.name
                                    searchQuery = targetRecord.rollNumberSnapshot
                                    selectedRecordForSideOption = null
                                },
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "check this student",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                                Text(
                                    text = "Filter report for this student only",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        val wingOptInteraction = rememberMotionInteractionSource()
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.20f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .pressFeedback(wingOptInteraction)
                                .clickable(
                                    interactionSource = wingOptInteraction,
                                    indication = null,
                                ) {
                                    selectedScopeName = ReportScope.WING.name
                                    selectedWingId = targetRecord.wingIdSnapshot
                                    searchQuery = ""
                                    selectedRecordForSideOption = null
                                },
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "check his wing",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                                Text(
                                    text = "Filter report to " + targetRecord.wingNameSnapshot,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        if (isAdmin) {
                            val editOptInteraction = rememberMotionInteractionSource()
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = Color(0x334CAF50),
                                border = BorderStroke(1.dp, Color(0x664CAF50)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pressFeedback(editOptInteraction)
                                    .clickable(
                                        interactionSource = editOptInteraction,
                                        indication = null,
                                    ) {
                                        scope.launch {
                                            val student = application.database.studentDao().findById(targetRecord.studentId)
                                            studentToEdit = student
                                            selectedRecordForSideOption = null
                                        }
                                    },
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = "Edit student details",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF81C784),
                                    )
                                    Text(
                                        text = "Correct name, room, wing, branch, year or status",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = { selectedRecordForSideOption = null },
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color(0x35FFFFFF)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    ) {
                        Text("Close", color = Color.White)
                    }
                }
            }
        }
    }

    studentToEdit?.let { student ->
        var editName by remember(student) { mutableStateOf(student.studentName) }
        var editRoom by remember(student) { mutableStateOf(student.currentRoomNumber) }
        var editWingId by remember(student) { mutableStateOf(student.currentWingId) }
        var editBranch by remember(student) { mutableStateOf(student.branch.orEmpty()) }
        var editYear by remember(student) { mutableStateOf(student.year.orEmpty()) }
        var editIsActive by remember(student) { mutableStateOf(student.isActive) }
        var editWingMenuExpanded by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { studentToEdit = null },
            title = { Text("Edit Student Details") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Roll Number: #${student.rollNumber} (Permanent ID)",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Full Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = editRoom,
                        onValueChange = { editRoom = it },
                        label = { Text("Room Number") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { editWingMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            val wingName = wings.firstOrNull { it.wingId == editWingId }?.name ?: "Select Wing"
                            Text("Wing: $wingName")
                        }
                        DropdownMenu(
                            expanded = editWingMenuExpanded,
                            onDismissRequest = { editWingMenuExpanded = false },
                        ) {
                            wings.forEach { wing ->
                                DropdownMenuItem(
                                    text = { Text(wing.name) },
                                    onClick = {
                                        editWingId = wing.wingId
                                        editWingMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = editBranch,
                        onValueChange = { editBranch = it },
                        label = { Text("Branch") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = editYear,
                        onValueChange = { editYear = it },
                        label = { Text("Year") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (editIsActive) "Status: Active" else "Status: Inactive")
                        Switch(
                            checked = editIsActive,
                            onCheckedChange = { editIsActive = it },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            try {
                                application.studentRepository.updateStudent(
                                    studentId = student.studentId,
                                    studentName = editName,
                                    roomNumber = editRoom,
                                    wingId = editWingId,
                                    branch = editBranch,
                                    year = editYear,
                                )
                                if (editIsActive != student.isActive) {
                                    application.studentRepository.setStudentActive(student.studentId, editIsActive)
                                }
                                Toast.makeText(
                                    context,
                                    "Updated resident $editName and synced to cloud!",
                                    Toast.LENGTH_SHORT,
                                ).show()
                                studentToEdit = null
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    e.message ?: "Could not update student.",
                                    Toast.LENGTH_SHORT,
                                ).show()
                                studentToEdit = null
                            }
                        }
                    },
                ) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { studentToEdit = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}
}

@Composable
private fun ReportStatItem(label: String, count: Any, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (count is Int) {
            AnimatedRollingCounter(
                count = count,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color,
            )
        } else {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun AttendanceReportRow(
    record: AttendanceRecordEntity,
    modifier: Modifier = Modifier,
    onLongClick: () -> Unit,
) {
    val backdrop = LocalGlobalGlassBackdrop.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val rowInteraction = rememberMotionInteractionSource()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlassEffect(
                backdrop = backdrop,
                density = density,
                cornerRadius = 18.dp,
                blurRadius = 12.dp,
                refractionHeightPx = 12f,
                refractionAmountPx = 18f,
                surfaceColor = Color(0x351F1C2B),
            )
            .pressFeedback(rowInteraction, pressedScale = 0.98f)
            .combinedClickable(
                interactionSource = rowInteraction,
                indication = null,
                onClick = {},
                onLongClick = onLongClick,
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "#${record.rollNumberSnapshot}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = record.studentNameSnapshot,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${record.localDate} · ${record.wingNameSnapshot} (Room ${record.roomNumberSnapshot})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val statusColor = when (record.status) {
                AttendanceStatus.PRESENT.name -> Color(0xFF4CAF50)
                AttendanceStatus.ABSENT.name -> Color(0xFFE53935)
                AttendanceStatus.LEAVE.name -> Color(0xFFFFA000)
                else -> Color(0xFF9E9E9E)
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = statusColor.copy(alpha = 0.2f),
            ) {
                Text(
                    text = record.status,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = statusColor,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}
