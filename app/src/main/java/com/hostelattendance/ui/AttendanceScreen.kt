package com.hostelattendance.ui

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.graphics.Path
import com.hostelattendance.domain.UserSessionPrivacyRules
import com.hostelattendance.domain.AttendanceDisputeRequest
import com.google.firebase.firestore.FirebaseFirestore
import java.time.LocalTime


import android.app.DatePickerDialog
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import com.hostelattendance.domain.NaturalRollNumberComparator
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hostelattendance.HostelAttendanceApplication
import com.hostelattendance.data.local.AttendanceRecordEntity
import com.hostelattendance.data.local.AttendanceSessionWithRecords
import com.hostelattendance.data.local.WingEntity
import com.hostelattendance.data.repository.AttendanceRepository
import com.hostelattendance.domain.AttendanceRules
import com.hostelattendance.domain.AttendanceStatus
import com.hostelattendance.domain.AttendanceTotals
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val DEFAULT_HOSTEL_ID = "cgitr-boys-hostel"
private val attendanceDateFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)

private val markableStatuses = listOf(
    AttendanceStatus.PRESENT,
    AttendanceStatus.ABSENT,
    AttendanceStatus.LEAVE,
)

@Composable
fun AttendanceScreen(
    hostelId: String = DEFAULT_HOSTEL_ID,
    allowedWingIds: Set<String> = emptySet(),
    isAdmin: Boolean = true,
    userDisplayName: String? = null,
    userStudentId: String? = null,
    userRollNumber: String? = null,
    onNavigateToRegistration: () -> Unit = {},
    onSwitchHosteller: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val application = context.applicationContext as HostelAttendanceApplication
    AttendanceScreen(
        hostelId = hostelId,
        allowedWingIds = allowedWingIds,
        isAdmin = isAdmin,
        userDisplayName = userDisplayName,
        userStudentId = userStudentId,
        userRollNumber = userRollNumber,
        onNavigateToRegistration = onNavigateToRegistration,
        onSwitchHosteller = onSwitchHosteller,
        repository = application.attendanceRepository,
    )
}

@Composable
private fun AttendanceScreen(
    hostelId: String,
    allowedWingIds: Set<String>,
    isAdmin: Boolean,
    userDisplayName: String?,
    userStudentId: String?,
    userRollNumber: String?,
    onNavigateToRegistration: () -> Unit,
    onSwitchHosteller: (() -> Unit)?,
    repository: AttendanceRepository,
) {
    if (!isAdmin) {
        UserIndividualAttendanceView(
            hostelId = hostelId,
            userDisplayName = userDisplayName,
            userStudentId = userStudentId,
            userRollNumber = userRollNumber,
            onSwitchHosteller = onSwitchHosteller,
            repository = repository,
        )
        return
    }
    val context = LocalContext.current
    val application = context.applicationContext as HostelAttendanceApplication
    val allWings by repository.observeWings(hostelId).collectAsState(initial = emptyList())
    val wings = remember(allWings, allowedWingIds) {
        if (allowedWingIds.isEmpty()) allWings else allWings.filter { it.wingId in allowedWingIds }
    }
    var selectedWingId by rememberSaveable { mutableStateOf("") }
    var selectedDateString by rememberSaveable { mutableStateOf(AttendanceRules.today().toString()) }
    val selectedDate = remember(selectedDateString) {
        try { LocalDate.parse(selectedDateString) } catch (_: Exception) { AttendanceRules.today() }
    }
    val isPastDate = remember(selectedDate) { AttendanceRules.isPastDate(selectedDate) }
    var wingMenuExpanded by remember { mutableStateOf(false) }
    var showSaveConfirmation by remember { mutableStateOf(false) }
    var showClearConfirmation by remember { mutableStateOf(false) }
    var showAttendanceMarkedSuccessDialog by rememberSaveable { mutableStateOf(false) }
    var showReopenConfirmation by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var isClearing by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val activeStudentCountFlow = remember(repository, hostelId, selectedWingId) {
        repository.observeActiveStudentCount(hostelId, selectedWingId)
    }
    val activeStudentCount by activeStudentCountFlow.collectAsState(initial = 0)

    LaunchedEffect(allWings.isEmpty()) {
        if (allWings.isEmpty()) {
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

    LaunchedEffect(wings) {
        if (wings.isNotEmpty()) {
            if (selectedWingId.isBlank() || wings.none { it.wingId == selectedWingId }) {
                selectedWingId = wings.first().wingId
            }
        }
    }

    val sessionFlow = remember(repository, hostelId, selectedDateString, selectedWingId) {
        if (selectedWingId.isBlank()) {
            kotlinx.coroutines.flow.flowOf(null)
        } else {
            repository.observeSession(
                hostelId = hostelId,
                localDate = selectedDateString,
                wingId = selectedWingId,
            )
        }
    }
    val session by sessionFlow.collectAsState(initial = null)

    DisposableEffect(hostelId, selectedDateString, selectedWingId) {
        if (selectedWingId.isBlank()) return@DisposableEffect onDispose {}

        val listenerReg = com.hostelattendance.data.sync.FirestoreSyncService.listenToAttendanceRealtime(
            hostelId = hostelId,
            localDate = selectedDateString,
            wingId = selectedWingId,
            sessionDao = application.database.attendanceSessionDao(),
        )

        onDispose {
            listenerReg?.remove()
        }
    }

    LaunchedEffect(hostelId, selectedDateString, selectedWingId) {
        if (selectedWingId.isBlank()) return@LaunchedEffect
        errorMessage = null
        infoMessage = null
        if (session == null || session?.records.isNullOrEmpty()) {
            isLoading = true
        }
        try {
            val draft = repository.ensureDraftSession(
                hostelId = hostelId,
                localDate = selectedDateString,
                wingId = selectedWingId,
                isAdmin = isAdmin,
            )
            if (draft == null) {
                // If not found locally, attempt to pull directly from Firestore
                val pulled = com.hostelattendance.data.sync.FirestoreSyncService.pullAttendanceSession(
                    hostelId = hostelId,
                    localDate = selectedDateString,
                    wingId = selectedWingId,
                    sessionDao = application.database.attendanceSessionDao(),
                )
                if (pulled == null) {
                    if (!isAdmin) {
                        if (AttendanceRules.isPastDate(selectedDate)) {
                            infoMessage = "No attendance was recorded on ${selectedDate.format(attendanceDateFormatter)}. Past attendance records are read-only."
                        } else {
                            infoMessage = "Attendance for today has not been marked yet by the administrator."
                        }
                    } else if (AttendanceRules.isPastDate(selectedDate)) {
                        infoMessage = null
                    } else if (activeStudentCount == 0) {
                        infoMessage = "No active students are registered in this wing. Attendance cannot be started until a roster is available."
                    }
                }
            }
        } catch (exception: Exception) {
            errorMessage = exception.message ?: "Could not load the attendance record."
        } finally {
            isLoading = false
        }
    }

    val allStudents by application.studentRepository.observeAllStudents(hostelId).collectAsState(initial = emptyList())
    val activeStudentIds = remember(allStudents) {
        allStudents.filter { it.isActive }.map { it.studentId }.toSet()
    }

    val records = remember(session?.records, session?.session?.state, activeStudentIds) {
        val raw = session?.records.orEmpty()
        val filtered = if (session?.session?.state == "DRAFT" && activeStudentIds.isNotEmpty()) {
            raw.filter { it.studentId in activeStudentIds }
        } else {
            raw
        }
        if (session?.session?.state == "DRAFT") {
            // Unmarked students stay at the top arranged in natural roll number order
            val unmarked = filtered.filter { it.status == AttendanceStatus.NOT_MARKED.name }
                .sortedWith { r1, r2 ->
                    NaturalRollNumberComparator.compare(r1.rollNumberSnapshot, r2.rollNumberSnapshot)
                }
            // Marked students move to the end of the page in order of when they were marked (updatedAtMillis ascending)
            // so the next selected box gets placed below the first one, and so on.
            val marked = filtered.filter { it.status != AttendanceStatus.NOT_MARKED.name }
                .sortedWith { r1, r2 ->
                    val timeComp = r1.updatedAtMillis.compareTo(r2.updatedAtMillis)
                    if (timeComp != 0) timeComp else NaturalRollNumberComparator.compare(r1.rollNumberSnapshot, r2.rollNumberSnapshot)
                }
            unmarked + marked
        } else {
            filtered.sortedWith { r1, r2 ->
                NaturalRollNumberComparator.compare(r1.rollNumberSnapshot, r2.rollNumberSnapshot)
            }
        }
    }
    val totals = remember(records) { AttendanceRules.totals(records.map { it.status }) }
    val selectedWing = wings.firstOrNull { it.wingId == selectedWingId }
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "attendance_header") {
            Text(
                text = "Attendance",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
            )
        }

        item(key = "attendance_date_and_wing") {
            GlassPanel(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Date and wing",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (isLoading) {
                        Text(
                            text = "Auto-syncing...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val dateInteractionSource = rememberMotionInteractionSource()
                    OutlinedButton(
                        onClick = {
                            val current = selectedDate
                            DatePickerDialog(
                                context,
                                { _, year, month, day ->
                                    selectedDateString = LocalDate.of(year, month + 1, day).toString()
                                },
                                current.year,
                                current.monthValue - 1,
                                current.dayOfMonth,
                            ).show()
                        },
                        interactionSource = dateInteractionSource,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .pressFeedback(dateInteractionSource),
                    ) {
                        Text(
                            text = selectedDate.format(attendanceDateFormatter),
                            maxLines = 1,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        val wingInteractionSource = rememberMotionInteractionSource()
                        OutlinedButton(
                            onClick = { wingMenuExpanded = true },
                            enabled = wings.isNotEmpty(),
                            interactionSource = wingInteractionSource,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .pressFeedback(wingInteractionSource),
                        ) {
                            Text(
                                text = selectedWing?.name ?: "Select wing",
                                maxLines = 1,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        DropdownMenu(
                            expanded = wingMenuExpanded,
                            onDismissRequest = { wingMenuExpanded = false },
                        ) {
                            wings.forEach { wing ->
                                DropdownMenuItem(
                                    text = { Text(wing.name) },
                                    onClick = {
                                        selectedWingId = wing.wingId
                                        wingMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (isLoading && session == null && records.isEmpty()) {
            item(key = "attendance_loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AnimatedSweepSpinner()
                }
            }
        }

        if (errorMessage != null && session == null) {
            item(key = "attendance_error") {
                MessageCard(message = errorMessage!!, isError = true)
            }
        }

        if (infoMessage != null && session == null) {
            item(key = "attendance_info") {
                MessageCard(
                    message = infoMessage!!,
                    actionLabel = if (isAdmin) "Add Students in Registration →" else null,
                    onAction = if (isAdmin) onNavigateToRegistration else null,
                )
            }
        }

        if (isPastDate && session == null && !isLoading) {
            item(key = "past_attendance_empty_card") {
                GlassPanel(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Previous Date · No Attendance Recorded",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = if (!isAdmin) {
                                "No attendance record exists for ${selectedDate.format(attendanceDateFormatter)}. Past attendance records are read-only."
                            } else {
                                "No attendance record was found for ${selectedDate.format(attendanceDateFormatter)} in this wing. As an administrator, you may initialize and record attendance for this date."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (isAdmin) {
                            val createInteraction = rememberMotionInteractionSource()
                            val backdrop = LocalGlobalGlassBackdrop.current
                            val density = LocalDensity.current
                            val createBtnModifier = if (backdrop != null) {
                                Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .liquidGlassCapsuleEffect(
                                        backdrop = backdrop,
                                        density = density,
                                        blurRadius = 12.dp,
                                        surfaceColor = Color(0x356750A4),
                                    )
                                    .pressFeedback(createInteraction)
                            } else {
                                Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .pressFeedback(createInteraction)
                            }
                            Button(
                                onClick = {
                                    scope.launch {
                                        isLoading = true
                                        try {
                                            val created = repository.ensureDraftSession(
                                                hostelId = hostelId,
                                                localDate = selectedDateString,
                                                wingId = selectedWingId,
                                                isAdmin = true,
                                                allowPastCreation = true,
                                            )
                                            if (created != null) {
                                                infoMessage = "Past attendance draft created. You can now mark attendance."
                                                errorMessage = null
                                            }
                                        } catch (e: Exception) {
                                            errorMessage = e.message ?: "Could not create past attendance session."
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                },
                                interactionSource = createInteraction,
                                shape = RoundedCornerShape(24.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (backdrop != null) Color.Transparent else MaterialTheme.colorScheme.primary,
                                    contentColor = Color.White,
                                ),
                                modifier = createBtnModifier,
                            ) {
                                Text("Create Past Attendance Draft", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        if (session != null) {
            item(key = "attendance_summary") {
                AttendanceSummary(session = session!!, totals = totals)
            }

            if (errorMessage != null) {
                item(key = "session_error_message") {
                    MessageCard(message = errorMessage!!, isError = true)
                }
            }

            if (infoMessage != null && session!!.session.state == "DRAFT") {
                item(key = "session_info_message") {
                    MessageCard(message = infoMessage!!)
                }
            }

            val canEdit = isAdmin && session!!.session.state == "DRAFT"

            items(
                records,
                key = { it.recordId },
                contentType = { "attendance_record_card" },
            ) { record ->
                AttendanceRecordCard(
                    record = record,
                    saved = !canEdit,
                    onStatusSelected = { status ->
                        if (canEdit) {
                            scope.launch {
                                try {
                                    repository.mark(session!!.session.sessionId, record.recordId, status, isAdmin)
                                    errorMessage = null
                                } catch (exception: Exception) {
                                    errorMessage = exception.message ?: "Could not save the attendance change locally."
                                }
                            }
                        }
                    },
                )
            }

            if (canEdit && records.isNotEmpty()) {
                item(key = "attendance_clear_selections") {
                    val backdrop = LocalGlobalGlassBackdrop.current
                    val density = androidx.compose.ui.platform.LocalDensity.current
                    val clearInteractionSource = rememberMotionInteractionSource()
                    val hasAnyMarked = totals.marked > 0

                    val clearBtnModifier = if (backdrop != null) {
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .liquidGlassCapsuleEffect(
                                backdrop = backdrop,
                                density = density,
                                blurRadius = 14.dp,
                                surfaceColor = if (hasAnyMarked) Color(0x384A1C24) else Color(0x202B2836),
                                borderWidth = 1.5.dp,
                                borderBrush = Brush.verticalGradient(
                                    if (hasAnyMarked) {
                                        listOf(
                                            Color(0xFFFF8A80).copy(alpha = 0.85f),
                                            Color(0xFFEF5350).copy(alpha = 0.40f),
                                            Color(0xFFE53935).copy(alpha = 0.15f),
                                        )
                                    } else {
                                        listOf(
                                            Color.White.copy(alpha = 0.35f),
                                            Color.White.copy(alpha = 0.12f),
                                            Color.Transparent,
                                        )
                                    }
                                ),
                            )
                            .pressFeedback(clearInteractionSource)
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .pressFeedback(clearInteractionSource)
                    }

                    Button(
                        onClick = {
                            if (hasAnyMarked) {
                                showClearConfirmation = true
                            } else {
                                android.widget.Toast.makeText(context, "No markings to clear", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = hasAnyMarked && !isSaving && !isClearing,
                        interactionSource = clearInteractionSource,
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (backdrop != null) Color.Transparent else Color(0x26EF5350),
                            contentColor = if (hasAnyMarked) Color(0xFFFF8A80) else Color.White.copy(alpha = 0.40f),
                            disabledContainerColor = if (backdrop != null) Color.Transparent else Color(0x15FFFFFF),
                            disabledContentColor = Color.White.copy(alpha = 0.35f),
                        ),
                        modifier = clearBtnModifier,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "↺",
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = "Clear Selections",
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            item(key = "attendance_action_footer") {
                if (!isAdmin) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = if (session!!.session.state == "SAVED") "Attendance Record · View Only" else "Attendance in Progress · View Only",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Viewing attendance as resident/viewer. Marking and modifying records is restricted to administrators.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                } else if (session!!.session.state == "SAVED") {
                    val backdrop = LocalGlobalGlassBackdrop.current
                    val density = androidx.compose.ui.platform.LocalDensity.current
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Attendance is saved and locked.",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Marked attendance cannot be changed directly. To make corrections, reopen this attendance session below.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                            )
                            val reopenInteraction = rememberMotionInteractionSource()
                            val reopenModifier = if (backdrop != null) {
                                Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .liquidGlassCapsuleEffect(
                                        backdrop = backdrop,
                                        density = density,
                                        blurRadius = 12.dp,
                                        surfaceColor = Color(0x352E2B38),
                                    )
                                    .pressFeedback(reopenInteraction)
                            } else {
                                Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .pressFeedback(reopenInteraction)
                            }
                            Button(
                                onClick = { showReopenConfirmation = true },
                                interactionSource = reopenInteraction,
                                shape = RoundedCornerShape(24.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (backdrop != null) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                ),
                                modifier = reopenModifier,
                            ) {
                                Text("Reopen for Administrator Correction", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                } else {
                    val saveInteractionSource = rememberMotionInteractionSource()
                    val hasUnmarked = totals.notMarked > 0
                    val backdrop = LocalGlobalGlassBackdrop.current
                    val density = androidx.compose.ui.platform.LocalDensity.current

                    val buttonModifier = if (backdrop != null) {
                        Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .liquidGlassCapsuleEffect(
                                backdrop = backdrop,
                                density = density,
                                blurRadius = 14.dp,
                                refractionHeightPx = 14f,
                                refractionAmountPx = 20f,
                                surfaceColor = if (hasUnmarked) Color(0x352A2633) else Color(0x606750A4),
                                specular = com.styropyr0.prismal.specular.PrismalSpecular(
                                    width = 1.dp,
                                    alpha = 0.85f,
                                    style = com.styropyr0.prismal.specular.PrismalSpecularStyle.Ambient,
                                ),
                                depthShadow = com.styropyr0.prismal.depth.PrismalDepthShadow(
                                    radius = 14.dp,
                                    color = Color.Black.copy(alpha = 0.35f),
                                ),
                            )
                            .pressFeedback(saveInteractionSource)
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .pressFeedback(saveInteractionSource)
                    }

                    Button(
                        onClick = {
                            if (hasUnmarked) {
                                errorMessage = "Cannot save: ${totals.notMarked} student(s) are not marked yet. It is mandatory to mark every student as Present, Absent, or Leave."
                            } else {
                                showSaveConfirmation = true
                            }
                        },
                        enabled = !isSaving && records.isNotEmpty(),
                        interactionSource = saveInteractionSource,
                        shape = RoundedCornerShape(27.dp),
                        colors = if (hasUnmarked) {
                            ButtonDefaults.buttonColors(
                                containerColor = if (backdrop != null) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            ButtonDefaults.buttonColors(
                                containerColor = if (backdrop != null) Color.Transparent else MaterialTheme.colorScheme.primary,
                                contentColor = Color.White,
                            )
                        },
                        modifier = buttonModifier,
                    ) {
                        Text(
                            text = if (hasUnmarked) "Mark all students to save (${totals.notMarked} left)" else "Review and save attendance",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        } else if (wings.isEmpty()) {
            item(key = "attendance_no_wings") {
                MessageCard(
                    message = "No active wings are configured yet. Add a wing and students before taking attendance.",
                    actionLabel = if (isAdmin) "Create Wing in Registration →" else null,
                    onAction = if (isAdmin) onNavigateToRegistration else null,
                )
            }
        }
    }

    if (showSaveConfirmation && session != null) {
        SaveAttendanceDialog(
            session = session!!,
            records = records,
            totals = totals,
            selectedDate = selectedDate,
            onDismiss = { if (!isSaving) showSaveConfirmation = false },
            onConfirm = {
                scope.launch {
                    isSaving = true
                    errorMessage = null
                    try {
                        repository.save(
                            sessionId = session!!.session.sessionId,
                            savedBy = null,
                            confirmIncomplete = false,
                        )
                        showSaveConfirmation = false
                        showAttendanceMarkedSuccessDialog = true
                        infoMessage = "Attendance saved successfully."
                    } catch (exception: Exception) {
                        showSaveConfirmation = false
                        errorMessage = exception.message ?: "Attendance could not be saved."
                    } finally {
                        isSaving = false
                    }
                }
            },
        )
    }

    if (showAttendanceMarkedSuccessDialog) {
        AppleGlassDialog(
            title = "Attendance Marked Successfully !",
            message = "Attendance records for ${selectedDate.format(attendanceDateFormatter)} have been verified and saved.",
            confirmText = "OK",
            dismissText = null,
            showConfetti = true,
            onConfirm = { showAttendanceMarkedSuccessDialog = false },
            onDismiss = { showAttendanceMarkedSuccessDialog = false },
        )
    }

    if (showClearConfirmation && session != null) {
        AppleGlassDialog(
            title = "Clear All Selections?",
            message = "This will reset all ${totals.marked} marked attendance selections for ${selectedWing?.name ?: "this wing"} back to unselected.",
            confirmText = "Clear Selections",
            dismissText = "Cancel",
            isDestructive = true,
            onConfirm = {
                showClearConfirmation = false
                scope.launch {
                    isClearing = true
                    errorMessage = null
                    try {
                        val sessionId = session?.session?.sessionId
                        if (sessionId != null) {
                            repository.clearAllSelections(sessionId, isAdmin)
                            infoMessage = "All attendance markings cleared."
                            android.widget.Toast.makeText(context, "All selections cleared", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        errorMessage = e.message ?: "Could not clear selections."
                    } finally {
                        isClearing = false
                    }
                }
            },
            onDismiss = { showClearConfirmation = false },
        )
    }

    if (showReopenConfirmation && session != null) {
        AlertDialog(
            onDismissRequest = { showReopenConfirmation = false },
            title = { Text("Reopen Attendance Session?") },
            text = {
                Text("Reopening will switch this saved attendance session back to editable draft status. Any modifications will be recorded.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            try {
                                repository.reopenSessionAsAdmin(session!!.session.sessionId)
                                showReopenConfirmation = false
                                infoMessage = "Session reopened. Attendance can now be corrected."
                            } catch (e: Exception) {
                                errorMessage = e.message ?: "Could not reopen session."
                                showReopenConfirmation = false
                            }
                        }
                    },
                ) {
                    Text("Reopen Session")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReopenConfirmation = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun AttendanceSummary(
    session: AttendanceSessionWithRecords,
    totals: AttendanceTotals,
) {
    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedRollingCounter(
                count = totals.marked,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = " of ${totals.total} marked",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val backdrop = LocalGlobalGlassBackdrop.current
            val density = androidx.compose.ui.platform.LocalDensity.current

            listOf(
                Triple("Present", totals.present, Color(0xFF4CAF50)),
                Triple("Absent", totals.absent, Color(0xFFEF5350)),
                Triple("Leave", totals.leave, Color(0xFFFFB74D)),
                Triple("Not marked", totals.notMarked, MaterialTheme.colorScheme.onSurfaceVariant),
            ).forEach { (label, count, countColor) ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .liquidGlassEffect(
                            backdrop = backdrop,
                            density = density,
                            cornerRadius = 14.dp,
                            blurRadius = 12.dp,
                            refractionHeightPx = 10f,
                            refractionAmountPx = 14f,
                            surfaceColor = Color(0x4C1C182B),
                        )
                        .padding(8.dp),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(RoundedCornerShape(percent = 50))
                                    .background(countColor),
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        AnimatedRollingCounter(
                            count = count,
                            style = MaterialTheme.typography.titleMedium,
                            color = countColor,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }
            }
        }
        if (session.session.state == "SAVED") {
            Text(
                text = "Saved locally · Locked",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
private fun LazyItemScope.AttendanceRecordCard(
    record: AttendanceRecordEntity,
    saved: Boolean,
    onStatusSelected: (AttendanceStatus) -> Unit,
) {
    val backdrop = LocalGlobalGlassBackdrop.current
    val density = androidx.compose.ui.platform.LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .animateItem(
                fadeInSpec = tween(durationMillis = 110, easing = LinearOutSlowInEasing),
                fadeOutSpec = tween(durationMillis = 90, easing = FastOutLinearInEasing),
                placementSpec = spring(dampingRatio = 0.78f, stiffness = 550f)
            )
            .optimizedGlassCard(
                cornerRadius = 20.dp,
                surfaceColor = Color(0x4C161324),
            )
            .padding(14.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Student Initials Avatar Pill
                val initials = remember(record.studentNameSnapshot) {
                    record.studentNameSnapshot.split(" ")
                        .mapNotNull { it.firstOrNull()?.toString() }
                        .take(2)
                        .joinToString("")
                        .uppercase()
                        .ifBlank { "ST" }
                }
                val activeStatusAccent = when (record.status) {
                    AttendanceStatus.PRESENT.name -> Color(0xFF4CAF50)
                    AttendanceStatus.ABSENT.name -> Color(0xFFEF5350)
                    AttendanceStatus.LEAVE.name -> Color(0xFFFFB74D)
                    else -> Color(0x66FFFFFF)
                }
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(activeStatusAccent.copy(alpha = 0.20f))
                        .border(1.dp, activeStatusAccent.copy(alpha = 0.55f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = record.studentNameSnapshot,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0x22FFFFFF),
                            border = BorderStroke(1.dp, Color(0x18FFFFFF)),
                        ) {
                            Text(
                                text = "Rm ${record.roomNumberSnapshot}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Text(
                        text = "Roll #${record.rollNumberSnapshot}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                markableStatuses.forEach { status ->
                    val selected = record.status == status.name
                    val statusInteractionSource = rememberMotionInteractionSource()
                    val statusAccent = when (status) {
                        AttendanceStatus.PRESENT -> Color(0xFF4CAF50)
                        AttendanceStatus.ABSENT -> Color(0xFFEF5350)
                        AttendanceStatus.LEAVE -> Color(0xFFFFB74D)
                        AttendanceStatus.NOT_MARKED -> MaterialTheme.colorScheme.primary
                    }
                    val backgroundColor by animateColorAsState(
                        targetValue = if (selected) {
                            statusAccent.copy(alpha = 0.35f)
                        } else {
                            Color(0x14FFFFFF)
                        },
                        animationSpec = tween(durationMillis = 65, easing = FastOutSlowInEasing),
                        label = "${status.name}StatusColor",
                    )
                    val borderColor by animateColorAsState(
                        targetValue = if (selected) statusAccent.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.32f),
                        animationSpec = tween(durationMillis = 65, easing = FastOutSlowInEasing),
                        label = "${status.name}BorderColor",
                    )
                    val scale by animateFloatAsState(
                        targetValue = if (selected) 1.05f else 1f,
                        animationSpec = spring(
                            dampingRatio = 0.70f,
                            stiffness = Spring.StiffnessHigh,
                        ),
                        label = "${status.name}Scale",
                    )
                    OutlinedButton(
                        onClick = { onStatusSelected(status) },
                        enabled = !saved,
                        interactionSource = statusInteractionSource,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(if (selected) 2.dp else 1.25.dp, borderColor),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .pressFeedback(statusInteractionSource, pressedScale = 0.94f)
                            .semantics {
                                this.selected = selected
                                this.contentDescription = status.accessibilityLabel
                            },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = backgroundColor,
                        ),
                    ) {
                        Text(
                            text = status.shortLabel,
                            maxLines = 1,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) statusAccent else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SaveAttendanceDialog(
    session: AttendanceSessionWithRecords,
    records: List<AttendanceRecordEntity>,
    totals: AttendanceTotals,
    selectedDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val today = remember { AttendanceRules.today() }
    val incomplete = remember(records) {
        records.filter { it.status == AttendanceStatus.NOT_MARKED.name }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm attendance") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Save attendance for ${selectedDate.format(attendanceDateFormatter)}?")
                if (selectedDate != today) {
                    Text(
                        text = "The selected date is not today. Check it carefully before saving.",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (incomplete.isNotEmpty()) {
                    Text(
                        text = "Cannot save: ${incomplete.size} student(s) are not marked yet. All students must be marked Present, Absent, or Leave.",
                        color = MaterialTheme.colorScheme.error,
                    )
                    LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                        items(incomplete, key = { it.recordId }) {
                            Text("${it.rollNumberSnapshot} · ${it.studentNameSnapshot}")
                        }
                    }
                } else {
                    Text("All ${totals.total} students have been marked.")
                }
            }
        },
        confirmButton = {
            if (incomplete.isEmpty()) {
                TextButton(onClick = onConfirm) {
                    Text("Confirm save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (incomplete.isEmpty()) "Cancel" else "Go back to mark")
            }
        },
    )
}

@Composable
private fun MessageCard(
    message: String,
    isError: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            if (actionLabel != null && onAction != null) {
                OutlinedButton(
                    onClick = onAction,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

private val AttendanceStatus.shortLabel: String
    get() = when (this) {
        AttendanceStatus.PRESENT -> "P"
        AttendanceStatus.ABSENT -> "A"
        AttendanceStatus.LEAVE -> "L"
        AttendanceStatus.NOT_MARKED -> "—"
    }

private val AttendanceStatus.accessibilityLabel: String
    get() = when (this) {
        AttendanceStatus.PRESENT -> "Present"
        AttendanceStatus.ABSENT -> "Absent"
        AttendanceStatus.LEAVE -> "Leave"
        AttendanceStatus.NOT_MARKED -> "Not marked"
    }


// =========================================================================
// USER SESSION - INDIVIDUAL ATTENDANCE VIEW (Matches Reference Design)
// =========================================================================

@Composable
private fun UserIndividualAttendanceView(
    hostelId: String,
    userDisplayName: String?,
    userStudentId: String?,
    userRollNumber: String?,
    onSwitchHosteller: (() -> Unit)? = null,
    repository: AttendanceRepository,
) {
    val context = LocalContext.current
    val application = context.applicationContext as HostelAttendanceApplication
    val scope = rememberCoroutineScope()

    var selectedDateString by rememberSaveable { mutableStateOf(AttendanceRules.today().toString()) }
    val selectedDate = remember(selectedDateString) {
        try { LocalDate.parse(selectedDateString) } catch (_: Exception) { AttendanceRules.today() }
    }
    val today = remember { AttendanceRules.today() }
    val isToday = remember(selectedDate, today) { selectedDate == today }

    var showDisputeDialog by rememberSaveable { mutableStateOf(false) }
    var showDisputeSuccessDialog by rememberSaveable { mutableStateOf(false) }

    // Resolve student record from local database to get full name and details
    val allStudents by application.studentRepository.observeAllStudents(hostelId).collectAsState(initial = emptyList())
    val matchedStudent = remember(allStudents, userStudentId, userRollNumber, userDisplayName) {
        allStudents.firstOrNull { student ->
            (userStudentId?.isNotBlank() == true && student.studentId.equals(userStudentId, ignoreCase = true)) ||
            (userRollNumber?.isNotBlank() == true && student.rollNumber.equals(userRollNumber, ignoreCase = true)) ||
            (userDisplayName?.isNotBlank() == true && (
                student.studentName.equals(userDisplayName.trim(), ignoreCase = true) ||
                student.studentName.contains(userDisplayName.trim(), ignoreCase = true) ||
                userDisplayName.trim().contains(student.studentName, ignoreCase = true)
            ))
        }
    }

    // Restore / sync attendance for the selected date from Firestore
    LaunchedEffect(hostelId, selectedDateString) {
        try {
            com.hostelattendance.data.sync.FirestoreSyncService.restoreAttendanceForDateRange(
                hostelId = hostelId,
                startDate = selectedDateString,
                endDate = selectedDateString,
                sessionDao = application.database.attendanceSessionDao(),
            )
        } catch (_: Exception) {}
    }

    // Observe records for this date and strictly filter to ONLY the logged-in student
    val rawRecords by repository.observeRecordsForDateRange(hostelId, selectedDateString, selectedDateString)
        .collectAsState(initial = emptyList())

    val userRecord = remember(rawRecords, matchedStudent, userDisplayName, userStudentId) {
        val sId = matchedStudent?.studentId ?: userStudentId
        val sName = matchedStudent?.studentName ?: userDisplayName
        UserSessionPrivacyRules.filterRecordsForUser(
            records = rawRecords,
            userStudentId = sId,
            userStudentName = sName,
            getStudentId = { it.studentId },
            getStudentName = { it.studentNameSnapshot },
        ).firstOrNull()
    }

    val currentStatus = userRecord?.status ?: AttendanceStatus.NOT_MARKED.name
    val greeting = remember { UserSessionPrivacyRules.getGreetingForTime(LocalTime.now()) }
    val studentName = userDisplayName?.takeIf { it.isNotBlank() } ?: matchedStudent?.studentName ?: "Devendra"
    val displayFirstName = remember(studentName) { UserSessionPrivacyRules.extractFirstName(studentName) }
    val formattedDate = remember(selectedDate) { UserSessionPrivacyRules.formatDisplayDate(selectedDate) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Top,
    ) {
        Spacer(modifier = Modifier.height(36.dp))

        // Cursive script greeting (matches reference: "Good morning")
        Text(
            text = greeting,
            style = TextStyle(
                fontSize = 32.sp,
                fontWeight = FontWeight.Normal,
                fontStyle = FontStyle.Italic,
                fontFamily = FontFamily.Serif,
                color = Color(0xFFE9D5FF),
            ),
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Bold Title: First Name (matches reference: "Devendra") with Switch Profile option
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = displayFirstName,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                ),
                modifier = Modifier.weight(1f, fill = false),
            )
            if (onSwitchHosteller != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x356366F1))
                        .border(BorderStroke(1.dp, Color(0x60818CF8)), RoundedCornerShape(12.dp))
                        .clickable { onSwitchHosteller() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Switch Profile",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFC084FC),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Date row with calendar icon (clickable to view other dates)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                    DatePickerDialog(
                        context,
                        { _, y, m, d ->
                            selectedDateString = LocalDate.of(y, m + 1, d).toString()
                        },
                        selectedDate.year,
                        selectedDate.monthValue - 1,
                        selectedDate.dayOfMonth,
                    ).show()
                }
                .padding(vertical = 4.dp, horizontal = 2.dp)
        ) {
            CalendarVectorIcon(tint = Color(0xFF94A3B8))
            Text(
                text = formattedDate,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF94A3B8),
            )
            if (!isToday) {
                Text(
                    text = "• Today",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFA855F7),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { selectedDateString = today.toString() }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(56.dp))

        // Main Attendance Status Glass Card (matches reference)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0x3B1E1B4B),
                            Color(0x22111827),
                        )
                    )
                )
                .border(
                    BorderStroke(
                        1.2.dp,
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0x60818CF8),
                                Color(0x223B82F6),
                            )
                        )
                    ),
                    RoundedCornerShape(26.dp)
                )
                .padding(horizontal = 20.dp, vertical = 22.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Circular Glowing Badge with checkmark/cross/clock
                StatusGlowingBadge(status = currentStatus)

                // Thin vertical separator line
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(48.dp)
                        .background(Color.White.copy(alpha = 0.15f))
                )

                // Label and large status indicator
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Your Attendance is marked as",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.72f),
                        fontWeight = FontWeight.Normal,
                    )
                    Text(
                        text = when (currentStatus) {
                            AttendanceStatus.PRESENT.name -> "Present"
                            AttendanceStatus.ABSENT.name -> "Absent"
                            AttendanceStatus.LEAVE.name -> "Leave"
                            else -> "Not Marked Yet"
                        },
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (currentStatus) {
                            AttendanceStatus.PRESENT.name -> Color(0xFFC084FC) // Lilac/violet as in reference
                            AttendanceStatus.ABSENT.name -> Color(0xFFF87171)
                            AttendanceStatus.LEAVE.name -> Color(0xFFFBBF24)
                            else -> Color(0xFF94A3B8)
                        },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // "Request for Wrong Attendance" Action Card (matches reference)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0x331E1B4B),
                            Color(0x200F172A),
                        )
                    )
                )
                .border(
                    BorderStroke(
                        1.dp,
                        Color(0x35818CF8)
                    ),
                    RoundedCornerShape(22.dp)
                )
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Request for Wrong Attendance",
                    fontSize = 15.sp,
                    color = Color.White.copy(alpha = 0.85f),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { showDisputeDialog = true },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0x356366F1),
                        contentColor = Color.White,
                    ),
                    border = BorderStroke(1.dp, Color(0x60818CF8)),
                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Request",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }

    if (showDisputeDialog) {
        AttendanceDisputeDialog(
            dateDisplay = formattedDate,
            currentStatus = currentStatus,
            onDismiss = { showDisputeDialog = false },
            onSubmit = { claimedStatus, reason ->
                showDisputeDialog = false
                val effectiveStudentId = matchedStudent?.studentId ?: userStudentId ?: "student_${System.currentTimeMillis()}"
                val effectiveStudentName = matchedStudent?.studentName ?: userDisplayName ?: "Student"

                scope.launch {
                    try {
                        FirebaseFirestore.getInstance()
                            .collection("attendance_disputes")
                            .add(
                                mapOf(
                                    "hostelId" to hostelId,
                                    "studentId" to effectiveStudentId,
                                    "studentName" to effectiveStudentName,
                                    "localDate" to selectedDateString,
                                    "markedStatus" to currentStatus,
                                    "claimedStatus" to claimedStatus,
                                    "reason" to reason,
                                    "createdAtMillis" to System.currentTimeMillis(),
                                    "status" to "PENDING",
                                )
                            )
                    } catch (_: Exception) {}
                    showDisputeSuccessDialog = true
                }
            }
        )
    }

    if (showDisputeSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showDisputeSuccessDialog = false },
            title = {
                Text(
                    text = "Request Submitted",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            },
            text = {
                Text(
                    text = "Your attendance correction request for $formattedDate has been sent to the hostel administration for review.",
                    color = Color.White.copy(alpha = 0.85f),
                )
            },
            confirmButton = {
                Button(
                    onClick = { showDisputeSuccessDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                ) {
                    Text("OK", color = Color.White)
                }
            },
            containerColor = Color(0xFF1E1B4B),
            shape = RoundedCornerShape(20.dp),
        )
    }
}

@Composable
private fun StatusGlowingBadge(status: String) {
    val glowColor = when (status) {
        AttendanceStatus.PRESENT.name -> Color(0xFF22C55E) // Vibrant emerald
        AttendanceStatus.ABSENT.name -> Color(0xFFEF4444)  // Vibrant red
        AttendanceStatus.LEAVE.name -> Color(0xFFF59E0B)   // Vibrant amber
        else -> Color(0xFF64748B)                          // Muted slate
    }

    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        glowColor.copy(alpha = 0.95f),
                        glowColor.copy(alpha = 0.60f),
                        glowColor.copy(alpha = 0.20f),
                    )
                )
            )
            .border(
                BorderStroke(2.dp, glowColor.copy(alpha = 0.85f)),
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(24.dp)) {
            val strokeWidth = 3.2.dp.toPx()
            when (status) {
                AttendanceStatus.PRESENT.name -> {
                    val path = Path().apply {
                        moveTo(size.width * 0.20f, size.height * 0.52f)
                        lineTo(size.width * 0.44f, size.height * 0.74f)
                        lineTo(size.width * 0.82f, size.height * 0.28f)
                    }
                    drawPath(
                        path = path,
                        color = Color.White,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
                AttendanceStatus.ABSENT.name -> {
                    drawLine(
                        color = Color.White,
                        start = Offset(size.width * 0.26f, size.height * 0.26f),
                        end = Offset(size.width * 0.74f, size.height * 0.74f),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = Color.White,
                        start = Offset(size.width * 0.74f, size.height * 0.26f),
                        end = Offset(size.width * 0.26f, size.height * 0.74f),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }
                AttendanceStatus.LEAVE.name -> {
                    drawCircle(
                        color = Color.White,
                        radius = size.width * 0.42f,
                        style = Stroke(width = strokeWidth)
                    )
                    drawLine(
                        color = Color.White,
                        start = Offset(size.width * 0.5f, size.height * 0.5f),
                        end = Offset(size.width * 0.5f, size.height * 0.26f),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = Color.White,
                        start = Offset(size.width * 0.5f, size.height * 0.5f),
                        end = Offset(size.width * 0.70f, size.height * 0.5f),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }
                else -> {
                    drawLine(
                        color = Color.White,
                        start = Offset(size.width * 0.28f, size.height * 0.5f),
                        end = Offset(size.width * 0.72f, size.height * 0.5f),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarVectorIcon(
    tint: Color,
    modifier: Modifier = Modifier.size(18.dp)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.6.dp.toPx()

        drawRoundRect(
            color = tint,
            topLeft = Offset(0f, h * 0.18f),
            size = Size(w, h * 0.82f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
            style = Stroke(width = stroke)
        )
        drawLine(
            color = tint,
            start = Offset(0f, h * 0.42f),
            end = Offset(w, h * 0.42f),
            strokeWidth = stroke
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.28f, 0f),
            end = Offset(w * 0.28f, h * 0.24f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.72f, 0f),
            end = Offset(w * 0.72f, h * 0.24f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        val dotRadius = 1.2.dp.toPx()
        drawCircle(color = tint, radius = dotRadius, center = Offset(w * 0.32f, h * 0.62f))
        drawCircle(color = tint, radius = dotRadius, center = Offset(w * 0.50f, h * 0.62f))
        drawCircle(color = tint, radius = dotRadius, center = Offset(w * 0.68f, h * 0.62f))
        drawCircle(color = tint, radius = dotRadius, center = Offset(w * 0.32f, h * 0.80f))
        drawCircle(color = tint, radius = dotRadius, center = Offset(w * 0.50f, h * 0.80f))
        drawCircle(color = tint, radius = dotRadius, center = Offset(w * 0.68f, h * 0.80f))
    }
}

@Composable
private fun AttendanceDisputeDialog(
    dateDisplay: String,
    currentStatus: String,
    onDismiss: () -> Unit,
    onSubmit: (claimedStatus: String, reason: String) -> Unit,
) {
    var selectedClaim by remember { mutableStateOf("PRESENT") }
    var reasonText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Request Wrong Attendance",
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Date: $dateDisplay\nCurrent recorded status: $currentStatus",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.75f),
                )

                Text(
                    text = "Select your correct status:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf("PRESENT", "LEAVE", "ABSENT").forEach { option ->
                        val isSelected = selectedClaim == option
                        OutlinedButton(
                            onClick = { selectedClaim = option },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isSelected) Color(0x406366F1) else Color.Transparent,
                                contentColor = if (isSelected) Color(0xFFC084FC) else Color.White.copy(alpha = 0.7f),
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) Color(0xFF818CF8) else Color.White.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = option.lowercase().replaceFirstChar { it.uppercase() },
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = reasonText,
                    onValueChange = { reasonText = it },
                    label = { Text("Reason / Explanation", color = Color.White.copy(alpha = 0.7f)) },
                    placeholder = { Text("e.g. Present in room 102 during roll call", color = Color.White.copy(alpha = 0.4f)) },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF818CF8),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 4,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(selectedClaim, reasonText.trim()) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Submit Request", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Cancel", color = Color.White.copy(alpha = 0.7f))
            }
        },
        containerColor = Color(0xFF1E1B4B),
        shape = RoundedCornerShape(24.dp),
    )
}
