package com.hostelattendance.ui

import android.app.DatePickerDialog
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
    onNavigateToRegistration: () -> Unit = {},
) {
    val context = LocalContext.current
    val application = context.applicationContext as HostelAttendanceApplication
    AttendanceScreen(
        hostelId = hostelId,
        allowedWingIds = allowedWingIds,
        isAdmin = isAdmin,
        onNavigateToRegistration = onNavigateToRegistration,
        repository = application.attendanceRepository,
    )
}

@Composable
private fun AttendanceScreen(
    hostelId: String,
    allowedWingIds: Set<String>,
    isAdmin: Boolean,
    onNavigateToRegistration: () -> Unit,
    repository: AttendanceRepository,
) {
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
    var showAttendanceMarkedSuccessDialog by rememberSaveable { mutableStateOf(false) }
    var showReopenConfirmation by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
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
        if (session?.session?.state == "DRAFT" && activeStudentIds.isNotEmpty()) {
            raw.filter { it.studentId in activeStudentIds }
        } else {
            raw
        }
    }
    val totals = remember(records) { AttendanceRules.totals(records.map { it.status }) }
    val selectedWing = wings.firstOrNull { it.wingId == selectedWingId }

    LazyColumn(
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
            onConfirm = { showAttendanceMarkedSuccessDialog = false },
            onDismiss = { showAttendanceMarkedSuccessDialog = false },
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
                            blurRadius = 10.dp,
                            refractionHeightPx = 10f,
                            refractionAmountPx = 14f,
                            surfaceColor = Color(0x35231F2E),
                        )
                        .padding(8.dp),
                ) {
                    Column {
                        AnimatedRollingCounter(
                            count = count,
                            style = MaterialTheme.typography.titleMedium,
                            color = countColor,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
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
            .animateItem()
            .liquidGlassEffect(
                backdrop = backdrop,
                density = density,
                cornerRadius = 18.dp,
                blurRadius = 14.dp,
                refractionHeightPx = 14f,
                refractionAmountPx = 20f,
                surfaceColor = Color(0x351E1A29),
            )
            .padding(14.dp),
    ) {
        Column {
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
                Text(
                    text = record.roomNumberSnapshot,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Normal,
                )
            }
            Text(
                text = "#${record.rollNumberSnapshot}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
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
                            statusAccent.copy(alpha = 0.22f)
                        } else {
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
                        },
                        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
                        label = "${status.name}StatusColor",
                    )
                    val borderColor by animateColorAsState(
                        targetValue = if (selected) statusAccent.copy(alpha = 0.85f) else Color(0x28FFFFFF),
                        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
                        label = "${status.name}BorderColor",
                    )
                    val scale by animateFloatAsState(
                        targetValue = if (selected) 1.05f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium,
                        ),
                        label = "${status.name}Scale",
                    )
                    OutlinedButton(
                        onClick = { onStatusSelected(status) },
                        enabled = !saved,
                        interactionSource = statusInteractionSource,
                        border = BorderStroke(if (selected) 1.5.dp else 1.dp, borderColor),
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
    totals: AttendanceTotals,
    selectedDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val today = remember { AttendanceRules.today() }
    val incomplete = session.records.filter { it.status == AttendanceStatus.NOT_MARKED.name }
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
