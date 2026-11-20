package com.hostelattendance.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.hostelattendance.HostelAttendanceApplication
import com.hostelattendance.data.excel.ExcelImportAnalysis
import com.hostelattendance.data.excel.ExcelStudentValidator
import com.hostelattendance.data.export.StudentListPdfGenerator
import com.hostelattendance.data.local.StudentEntity
import com.hostelattendance.data.local.WingEntity
import com.hostelattendance.data.repository.StudentRepository
import com.hostelattendance.domain.StudentRegistrationInput
import com.hostelattendance.domain.StudentRegistrationRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private const val REGISTRATION_HOSTEL_ID = "local-installation"
private val registrationControlShape = RoundedCornerShape(20.dp)

private enum class RegistrationTab(val label: String) {
    MANUAL("Single Entry"),
    EXCEL("Import Excel"),
}

@Composable
fun RegistrationScreen(
    hostelId: String = REGISTRATION_HOSTEL_ID,
    canRegisterStudents: Boolean = true,
    isAdmin: Boolean = true,
) {
    val context = LocalContext.current
    val application = context.applicationContext as HostelAttendanceApplication
    RegistrationScreen(
        hostelId = hostelId,
        canRegisterStudents = canRegisterStudents,
        isAdmin = isAdmin,
        repository = application.studentRepository,
    )
}

@Composable
private fun RegistrationScreen(
    hostelId: String,
    canRegisterStudents: Boolean,
    isAdmin: Boolean,
    repository: StudentRepository,
) {
    val context = LocalContext.current
    val wings by repository.observeWings(hostelId).collectAsState(initial = emptyList())
    val allStudents by repository.observeAllStudents(hostelId).collectAsState(initial = emptyList())

    var activeTabName by rememberSaveable { mutableStateOf(RegistrationTab.MANUAL.name) }
    val activeTab = remember(activeTabName) {
        RegistrationTab.entries.find { it.name == activeTabName } ?: RegistrationTab.MANUAL
    }

    // Manual registration form states
    var selectedWingId by rememberSaveable { mutableStateOf("") }
    var rollNumber by rememberSaveable { mutableStateOf("") }
    var roomNumber by rememberSaveable { mutableStateOf("") }
    var studentName by rememberSaveable { mutableStateOf("") }
    var branch by rememberSaveable { mutableStateOf("") }
    var year by rememberSaveable { mutableStateOf("") }
    var wingMenuExpanded by remember { mutableStateOf(false) }
    var showAddWingDialog by remember { mutableStateOf(false) }
    var newWingName by remember { mutableStateOf("") }
    var showRemoveWingDialog by remember { mutableStateOf(false) }
    var wingToRemove by remember { mutableStateOf<WingEntity?>(null) }
    var showRemoveStudentDialog by remember { mutableStateOf(false) }
    var studentToRemove by remember { mutableStateOf<StudentEntity?>(null) }
    var removeStudentSearchQuery by rememberSaveable { mutableStateOf("") }
    var isDeletingData by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var studentToEdit by remember { mutableStateOf<StudentEntity?>(null) }
    val activeStudents = remember(allStudents) { allStudents.filter { it.isActive } }

    // Edit student search query (Name or Roll Number or Room Number)
    var editSearchQuery by rememberSaveable { mutableStateOf("") }
    var rosterFilterQuery by rememberSaveable { mutableStateOf("") }

    val fieldErrors = remember { mutableStateMapOf<String, String>() }
    val scope = rememberCoroutineScope()

    // Excel import states
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var isParsingExcel by remember { mutableStateOf(false) }
    var isCommittingExcel by remember { mutableStateOf(false) }
    var excelAnalysis by remember { mutableStateOf<ExcelImportAnalysis?>(null) }
    var updateExistingDetails by remember { mutableStateOf(false) }
    var showCommitSuccessDialog by remember { mutableStateOf<String?>(null) }
    var showSingleRegistrationSuccessDialog by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(hostelId) {
        try {
            repository.syncWithCloud(hostelId)
        } catch (_: Exception) {}
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            selectedFileUri = uri
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            val name = cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) it.getString(nameIndex) else null
                } else null
            } ?: "student_roster.xlsx"
            selectedFileName = name
            excelAnalysis = null
            errorMessage = null
            successMessage = null

            scope.launch {
                isParsingExcel = true
                try {
                    val analysis = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            ExcelStudentValidator().validate(
                                inputStream = stream,
                                activeWings = wings,
                                existingStudents = allStudents,
                                hostelId = hostelId,
                            )
                        } ?: error("Could not open selected spreadsheet.")
                    }
                    excelAnalysis = analysis
                } catch (e: Exception) {
                    errorMessage = e.message ?: "Could not read the Excel spreadsheet."
                } finally {
                    isParsingExcel = false
                }
            }
        }
    }

    LaunchedEffect(wings) {
        if (wings.none { it.wingId == selectedWingId }) {
            selectedWingId = wings.firstOrNull()?.wingId.orEmpty()
        }
    }

    val studentsFlow = remember(repository, hostelId, selectedWingId) {
        if (selectedWingId.isBlank()) {
            kotlinx.coroutines.flow.flowOf(emptyList())
        } else {
            repository.observeStudents(hostelId, selectedWingId)
        }
    }
    val rawStudents by studentsFlow.collectAsState(initial = emptyList())
    val students = remember(rawStudents, rosterFilterQuery) {
        if (rosterFilterQuery.isBlank()) rawStudents
        else rawStudents.filter {
            it.rollNumber.contains(rosterFilterQuery.trim(), ignoreCase = true) ||
                it.studentName.contains(rosterFilterQuery.trim(), ignoreCase = true) ||
                it.currentRoomNumber.contains(rosterFilterQuery.trim(), ignoreCase = true)
        }
    }
    val selectedWing = wings.firstOrNull { it.wingId == selectedWingId }

    // Quick matching for "Edit student details" section
    val editSearchResults = remember(allStudents, editSearchQuery) {
        if (editSearchQuery.trim().isEmpty()) emptyList()
        else {
            val q = editSearchQuery.trim().lowercase()
            allStudents.filter { student ->
                student.rollNumber.lowercase().contains(q) ||
                    student.studentName.lowercase().contains(q) ||
                    student.currentRoomNumber.lowercase().contains(q)
            }.take(10)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "Student Registration",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (canRegisterStudents) "Register residents manually, batch-import using Excel, or print full student sheets"
                               else "View-only student directory · Registration restricted to administrators",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (canRegisterStudents) {
            // Mode Segmented Bar - Premium Gliding Black Pill
            item(key = "mode_switch") {
                val tabTrackShape = RoundedCornerShape(24.dp)
                val pillShape = RoundedCornerShape(20.dp)

                val tabBackdrop = LocalGlobalGlassBackdrop.current
                val tabDensity = androidx.compose.ui.platform.LocalDensity.current

                val trackModifier = if (tabBackdrop != null) {
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .liquidGlassCapsuleEffect(
                            backdrop = tabBackdrop,
                            density = tabDensity,
                            blurRadius = 14.dp,
                            surfaceColor = Color(0x35E0E0E6),
                            specular = com.styropyr0.prismal.specular.PrismalSpecular(
                                width = 1.dp,
                                alpha = 0.85f,
                                style = com.styropyr0.prismal.specular.PrismalSpecularStyle.Ambient,
                            ),
                            depthShadow = com.styropyr0.prismal.depth.PrismalDepthShadow(
                                radius = 12.dp,
                                color = Color.Black.copy(alpha = 0.28f),
                            ),
                        )
                        .padding(4.dp)
                } else {
                    Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .shadow(
                            elevation = 10.dp,
                            shape = tabTrackShape,
                            ambientColor = Color.Black.copy(alpha = 0.08f),
                            spotColor = Color.Black.copy(alpha = 0.12f),
                        )
                        .clip(tabTrackShape)
                        .background(Color(0xFFF1F1F1))
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.90f),
                                    Color.White.copy(alpha = 0.30f),
                                    Color(0x0A000000),
                                ),
                            ),
                            shape = tabTrackShape,
                        )
                        .padding(4.dp)
                }

                Box(modifier = trackModifier) {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val totalWidth = maxWidth
                        val tabCount = RegistrationTab.entries.size
                        val tabWidth = totalWidth / tabCount
                        val selectedTabIdx = RegistrationTab.entries.indexOf(activeTab).coerceAtLeast(0)
                        val targetOffset = tabWidth * selectedTabIdx

                        val indicatorOffset by animateDpAsState(
                            targetValue = targetOffset,
                            animationSpec = spring(
                                dampingRatio = 0.80f,
                                stiffness = 650f, // Apple-style snappy glide
                            ),
                            label = "regTabIndicatorOffset",
                        )

                        // Floating Black Pill with Liquid Glass
                        val pillModifier = if (tabBackdrop != null) {
                            Modifier
                                .offset(x = indicatorOffset)
                                .width(tabWidth)
                                .fillMaxHeight()
                                .padding(horizontal = 2.dp, vertical = 2.dp)
                                .liquidGlassCapsuleEffect(
                                    backdrop = tabBackdrop,
                                    density = tabDensity,
                                    blurRadius = 12.dp,
                                    surfaceColor = Color(0xD916141D),
                                    specular = com.styropyr0.prismal.specular.PrismalSpecular(
                                        width = 1.dp,
                                        alpha = 0.90f,
                                        style = com.styropyr0.prismal.specular.PrismalSpecularStyle.Ambient,
                                    ),
                                    depthShadow = com.styropyr0.prismal.depth.PrismalDepthShadow(
                                        radius = 8.dp,
                                        color = Color.Black.copy(alpha = 0.50f),
                                    ),
                                )
                        } else {
                            Modifier
                                .offset(x = indicatorOffset)
                                .width(tabWidth)
                                .fillMaxHeight()
                                .padding(horizontal = 2.dp, vertical = 2.dp)
                                .shadow(
                                    elevation = 8.dp,
                                    shape = pillShape,
                                    ambientColor = Color.Black.copy(alpha = 0.35f),
                                    spotColor = Color.Black.copy(alpha = 0.55f),
                                )
                                .clip(pillShape)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0xFF282828),
                                            Color(0xFF141414),
                                            Color(0xFF050505),
                                        ),
                                    ),
                                )
                                .border(
                                    width = 1.dp,
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.30f),
                                            Color.White.copy(alpha = 0.05f),
                                            Color.Transparent,
                                        ),
                                    ),
                                    shape = pillShape,
                                )
                        }

                        Box(modifier = pillModifier) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(11.dp)
                                    .clip(pillShape)
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.White.copy(alpha = 0.16f),
                                                Color.Transparent,
                                            ),
                                        ),
                                    ),
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RegistrationTab.entries.forEach { tab ->
                                val isSelected = activeTab == tab
                                val tabInteraction = rememberMotionInteractionSource()
                                val textColor by animateColorAsState(
                                    targetValue = if (isSelected) Color.White else Color(0xFF777777),
                                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                                    label = "regTabTextColor_${tab.name}",
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(pillShape)
                                        .pressFeedback(tabInteraction, pressedScale = 0.95f)
                                        .clickable(
                                            interactionSource = tabInteraction,
                                            indication = null,
                                        ) {
                                            activeTabName = tab.name
                                            errorMessage = null
                                            successMessage = null
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = tab.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                        color = textColor,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Tab 1: Single Entry
            if (activeTab == RegistrationTab.MANUAL) {
                item(key = "manual_entry_form") {
                    GlassPanel(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = "Add New Resident",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold,
                            )
                            OutlinedTextField(
                                value = rollNumber,
                                onValueChange = {
                                    rollNumber = it
                                    fieldErrors.remove("rollNumber")
                                },
                                label = { Text("Roll number * (Permanent ID)") },
                                shape = registrationControlShape,
                                singleLine = true,
                                isError = fieldErrors.containsKey("rollNumber"),
                                supportingText = fieldErrors["rollNumber"]?.let { message -> { Text(message) } },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = roomNumber,
                                onValueChange = {
                                    roomNumber = it
                                    fieldErrors.remove("roomNumber")
                                },
                                label = { Text("Room number *") },
                                shape = registrationControlShape,
                                singleLine = true,
                                isError = fieldErrors.containsKey("roomNumber"),
                                supportingText = fieldErrors["roomNumber"]?.let { message -> { Text(message) } },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    val wingInteractionSource = rememberMotionInteractionSource()
                                    OutlinedButton(
                                        onClick = { wingMenuExpanded = true },
                                        shape = registrationControlShape,
                                        enabled = wings.isNotEmpty(),
                                        interactionSource = wingInteractionSource,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 56.dp)
                                            .pressFeedback(wingInteractionSource),
                                    ) {
                                        Text(selectedWing?.name ?: "Select wing *", maxLines = 1)
                                    }
                                    fieldErrors["wingId"]?.let {
                                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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
                                                    fieldErrors.remove("wingId")
                                                    wingMenuExpanded = false
                                                },
                                            )
                                        }
                                    }
                                }
                                if (isAdmin) {
                                    val addWingInteractionSource = rememberMotionInteractionSource()
                                    OutlinedButton(
                                        onClick = {
                                            newWingName = ""
                                            showAddWingDialog = true
                                        },
                                        shape = registrationControlShape,
                                        interactionSource = addWingInteractionSource,
                                        modifier = Modifier
                                            .heightIn(min = 56.dp)
                                            .pressFeedback(addWingInteractionSource),
                                    ) {
                                        Text("Add wing")
                                    }

                                    val removeWingInteractionSource = rememberMotionInteractionSource()
                                    OutlinedButton(
                                        onClick = {
                                            showRemoveWingDialog = true
                                        },
                                        shape = registrationControlShape,
                                        enabled = wings.isNotEmpty(),
                                        border = BorderStroke(1.dp, Color(0xFFF43F5E).copy(alpha = 0.6f)),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = Color(0xFFFB7185),
                                        ),
                                        interactionSource = removeWingInteractionSource,
                                        modifier = Modifier
                                            .heightIn(min = 56.dp)
                                            .pressFeedback(removeWingInteractionSource),
                                    ) {
                                        Text("Remove wing")
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = studentName,
                                onValueChange = {
                                    studentName = it
                                    fieldErrors.remove("studentName")
                                },
                                label = { Text("Student name *") },
                                shape = registrationControlShape,
                                singleLine = true,
                                isError = fieldErrors.containsKey("studentName"),
                                supportingText = fieldErrors["studentName"]?.let { message -> { Text(message) } },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedTextField(
                                    value = branch,
                                    onValueChange = { branch = it },
                                    label = { Text("Branch (optional)") },
                                    shape = registrationControlShape,
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedTextField(
                                    value = year,
                                    onValueChange = { year = it },
                                    label = { Text("Year (optional)") },
                                    shape = registrationControlShape,
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                            }

                            val saveStudentInteractionSource = rememberMotionInteractionSource()
                            Button(
                                onClick = {
                                    val validation = StudentRegistrationRules.validate(
                                        StudentRegistrationInput(
                                            rollNumber = rollNumber,
                                            wingId = selectedWingId,
                                            roomNumber = roomNumber,
                                            studentName = studentName,
                                            branch = branch,
                                            year = year,
                                        ),
                                    )
                                    fieldErrors.clear()
                                    fieldErrors.putAll(validation.errors)
                                    errorMessage = null
                                    successMessage = null
                                    val valid = validation.value
                                    if (valid != null) {
                                        isSaving = true
                                        scope.launch {
                                            try {
                                                repository.registerStudent(
                                                    hostelId,
                                                    StudentRegistrationInput(
                                                        rollNumber = valid.rollNumber,
                                                        wingId = valid.wingId,
                                                        roomNumber = valid.roomNumber,
                                                        studentName = valid.studentName,
                                                        branch = valid.branch.orEmpty(),
                                                        year = valid.year.orEmpty(),
                                                    ),
                                                )
                                                val regName = valid.studentName
                                                val regRoll = valid.rollNumber
                                                rollNumber = ""
                                                roomNumber = ""
                                                studentName = ""
                                                branch = ""
                                                year = ""
                                                successMessage = "Student registered successfully."
                                                showSingleRegistrationSuccessDialog = "Student $regName (#$regRoll) has been registered successfully." 
                                            } catch (exception: Exception) {
                                                errorMessage = exception.message ?: "Student could not be registered."
                                            } finally {
                                                isSaving = false
                                            }
                                        }
                                    }
                                },
                                shape = registrationControlShape,
                                enabled = !isSaving && wings.isNotEmpty(),
                                interactionSource = saveStudentInteractionSource,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .pressFeedback(saveStudentInteractionSource),
                            ) {
                                if (isSaving) {
                                    AnimatedSweepSpinner(
                                        size = 22.dp,
                                        strokeWidth = 2.5.dp,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                } else {
                                    Text("Register Student", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            } else {
                // Tab 2: Excel Import Section
                item(key = "excel_import_panel") {
                    ExcelImportPanel(
                        context = context,
                        selectedFileName = selectedFileName,
                        isParsing = isParsingExcel,
                        isCommitting = isCommittingExcel,
                        analysis = excelAnalysis,
                        updateExistingDetails = updateExistingDetails,
                        onUpdateExistingChange = { updateExistingDetails = it },
                        onPickFile = {
                            filePickerLauncher.launch(
                                arrayOf(
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                    "application/vnd.ms-excel",
                                    "text/csv",
                                    "text/comma-separated-values",
                                    "text/plain",
                                    "application/octet-stream",
                                    "*/*",
                                ),
                            )
                        },
                        onCommit = {
                            val analysis = excelAnalysis ?: return@ExcelImportPanel
                            scope.launch {
                                isCommittingExcel = true
                                errorMessage = null
                                try {
                                    val result = repository.commitExcelImport(
                                        hostelId = hostelId,
                                        analysis = analysis,
                                        updateExistingDetails = updateExistingDetails,
                                    )
                                    showCommitSuccessDialog = "Import Complete: Added ${result.insertedCount} new student(s), updated ${result.updatedCount} existing resident(s), skipped ${result.skippedCount}."
                                    selectedFileUri = null
                                    selectedFileName = null
                                    excelAnalysis = null
                                } catch (e: Exception) {
                                    errorMessage = e.message ?: "Failed to import students from spreadsheet."
                                } finally {
                                    isCommittingExcel = false
                                }
                            }
                        },
                    )
                }
            }

            // SECTION: Edit student details
            item(key = "edit_student_search_section") {
                EditStudentSearchSection(
                    searchQuery = editSearchQuery,
                    onQueryChange = { editSearchQuery = it },
                    matchingStudents = editSearchResults,
                    wings = wings,
                    onSelectStudent = { student ->
                        studentToEdit = student
                    },
                )
            }

            // SECTION: Permanent Removal Tools (Remove Student Data & Remove Wing)
            if (isAdmin) {
                item(key = "removal_management_tools_section") {
                    AdminRemovalToolsSection(
                        onOpenRemoveStudent = {
                            removeStudentSearchQuery = ""
                            showRemoveStudentDialog = true
                        },
                        onOpenRemoveWing = {
                            showRemoveWingDialog = true
                        },
                    )
                }
            }

            // SECTION: Print all student sheet (Excel-style PDF) - Active residents only
            item(key = "print_student_sheet_section") {
                PrintStudentSheetSection(
                    hostelId = hostelId,
                    totalCount = activeStudents.size,
                    onPrint = {
                        StudentListPdfGenerator().generateAndSharePdf(
                            context = context,
                            hostelId = hostelId,
                            students = activeStudents,
                            wings = wings,
                        )
                    },
                )
            }
        }

        errorMessage?.let { msg ->
            item(key = "error_banner") {
                RegistrationNotice(msg, isError = true)
            }
        }
        successMessage?.let { msg ->
            item(key = "success_banner") {
                RegistrationNotice(msg)
            }
        }

        // Student Roster header with search
        item(key = "roster_header") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (selectedWing == null) "Registered Active Students (${activeStudents.size})" else "${selectedWing.name} (${students.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                OutlinedTextField(
                    value = rosterFilterQuery,
                    onValueChange = { rosterFilterQuery = it },
                    placeholder = { Text("Filter roster list by roll, name, room") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = registrationControlShape,
                )
            }
        }

        if (selectedWing == null) {
            item(key = "empty_wing") {
                RegistrationNotice("Create or select a wing to view students.")
            }
        } else if (students.isEmpty()) {
            item(key = "empty_students") {
                RegistrationNotice("No students registered in this wing yet.")
            }
        } else {
            items(
                students,
                key = StudentEntity::studentId,
                contentType = { "student_roster_item" },
            ) { student ->
                StudentListItem(
                    student = student,
                    isAdmin = isAdmin,
                    modifier = Modifier.animateItem(),
                    onClick = {
                        if (isAdmin) studentToEdit = student
                    },
                )
            }
        }

        // Bottom spacer to ensure bottom-most item clears the bottom navigation bar smoothly
        item(key = "bottom_spacer") {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showAddWingDialog) {
        AlertDialog(
            onDismissRequest = { showAddWingDialog = false },
            title = { Text("Add Wing") },
            text = {
                OutlinedTextField(
                    value = newWingName,
                    onValueChange = { newWingName = it },
                    label = { Text("Wing name (e.g. Wing C)") },
                    shape = registrationControlShape,
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        errorMessage = null
                        scope.launch {
                            try {
                                repository.createWing(hostelId, newWingName)
                                showAddWingDialog = false
                                successMessage = "Wing added successfully."
                            } catch (exception: Exception) {
                                errorMessage = exception.message ?: "Wing could not be added."
                                showAddWingDialog = false
                            }
                        }
                    },
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddWingDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    // Success dialog for Excel import
    showCommitSuccessDialog?.let { message ->
        AppleGlassDialog(
            title = "Registration Successfully Completed",
            message = message,
            confirmText = "OK",
            dismissText = null,
            showConfetti = true,
            onConfirm = { showCommitSuccessDialog = null },
            onDismiss = { showCommitSuccessDialog = null },
        )
    }

    // Success dialog for single student registration
    showSingleRegistrationSuccessDialog?.let { message ->
        AppleGlassDialog(
            title = "Registration Successfully Completed",
            message = message,
            confirmText = "OK",
            dismissText = null,
            showConfetti = true,
            onConfirm = { showSingleRegistrationSuccessDialog = null },
            onDismiss = { showSingleRegistrationSuccessDialog = null },
        )
    }

    // Edit Student Dialog
    // Reactivate Student ("Add this student") Dialog
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
                    Text("Roll Number: ${student.rollNumber} (Permanent ID)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)

                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Student Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = editRoom,
                        onValueChange = { editRoom = it },
                        label = { Text("Room number") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Box {
                        OutlinedButton(
                            onClick = { editWingMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
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

                    if (isAdmin) {
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = {
                                val s = student
                                studentToEdit = null
                                studentToRemove = s
                            },
                            shape = registrationControlShape,
                            border = BorderStroke(1.dp, Color(0xFFF43F5E)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFFFB7185),
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = Color(0xFFFB7185),
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Remove Student Permanently", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            try {
                                repository.updateStudent(
                                    studentId = student.studentId,
                                    studentName = editName,
                                    roomNumber = editRoom,
                                    wingId = editWingId,
                                    branch = editBranch,
                                    year = editYear,
                                )
                                if (editIsActive != student.isActive) {
                                    repository.setStudentActive(student.studentId, editIsActive)
                                }
                                successMessage = "Updated resident $editName."
                                studentToEdit = null
                            } catch (e: Exception) {
                                errorMessage = e.message ?: "Could not update student."
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

    // Remove Student Data Picker Dialog
    if (showRemoveStudentDialog) {
        val wingMap = remember(wings) { wings.associateBy { it.wingId } }
        val matchingRemovalStudents = remember(allStudents, removeStudentSearchQuery) {
            val q = removeStudentSearchQuery.trim().lowercase()
            if (q.isBlank()) allStudents.take(25)
            else {
                allStudents.filter { student ->
                    student.studentName.lowercase().contains(q) ||
                    student.rollNumber.lowercase().contains(q) ||
                    student.currentRoomNumber.lowercase().contains(q)
                }
            }
        }

        AlertDialog(
            onDismissRequest = { showRemoveStudentDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = Color(0xFFFB7185),
                    )
                    Text("Remove Student Data", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Select a student to permanently remove from the database:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    OutlinedTextField(
                        value = removeStudentSearchQuery,
                        onValueChange = { removeStudentSearchQuery = it },
                        placeholder = { Text("Search by name, roll, or room...") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            if (removeStudentSearchQuery.isNotBlank()) {
                                IconButton(onClick = { removeStudentSearchQuery = "" }) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        shape = registrationControlShape,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (matchingRemovalStudents.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "No students match your search.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(matchingRemovalStudents, key = { it.studentId }) { student ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0x1F2B2836),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = student.studentName,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 14.sp,
                                            )
                                            val wingName = wingMap[student.currentWingId]?.name ?: "Wing"
                                            Text(
                                                text = "Roll: ${student.rollNumber} · $wingName · Room ${student.currentRoomNumber}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                showRemoveStudentDialog = false
                                                studentToRemove = student
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0x35F43F5E),
                                                contentColor = Color(0xFFFDA4AF),
                                            ),
                                            border = BorderStroke(1.dp, Color(0xFFFB7185)),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.height(34.dp),
                                        ) {
                                            Text("Remove", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showRemoveStudentDialog = false }) {
                    Text("Close")
                }
            },
        )
    }

    // Student Permanent Removal Confirmation Dialog
    studentToRemove?.let { student ->
        AlertDialog(
            onDismissRequest = {
                if (!isDeletingData) studentToRemove = null
            },
            title = {
                Text(
                    text = "Permanently Remove Student?",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFB7185),
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Are you sure you want to permanently remove this student from the database?",
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text("• Name: ${student.studentName}")
                    Text("• Roll Number: ${student.rollNumber}")
                    Text("• Room Number: ${student.currentRoomNumber}")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "⚠️ This will permanently delete this student, their attendance history, and cloud bindings from both device storage and Cloud Firestore. This action cannot be undone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFDA4AF),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isDeletingData) return@Button
                        isDeletingData = true
                        scope.launch {
                            try {
                                repository.deleteStudent(hostelId, student.studentId)
                                successMessage = "Permanently removed resident ${student.studentName} from database."
                                errorMessage = null
                                studentToRemove = null
                            } catch (e: Exception) {
                                errorMessage = e.message ?: "Failed to delete student from database."
                            } finally {
                                isDeletingData = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE11D48),
                        contentColor = Color.White,
                    ),
                    enabled = !isDeletingData,
                ) {
                    if (isDeletingData) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Delete Permanently", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { studentToRemove = null },
                    enabled = !isDeletingData,
                ) {
                    Text("Cancel")
                }
            },
        )
    }

    // Remove Wing Picker Dialog
    if (showRemoveWingDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveWingDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFB7185),
                    )
                    Text("Remove Wing", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Select a wing to permanently remove from the database:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (wings.isEmpty()) {
                        Text(
                            text = "No wings available to remove.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(wings, key = { it.wingId }) { wing ->
                                val count = allStudents.count { it.currentWingId == wing.wingId }
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0x1F2B2836),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = wing.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                            )
                                            Text(
                                                text = if (count == 0) "No registered students" else "$count registered resident(s)",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (count > 0) Color(0xFFFDA4AF) else MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                showRemoveWingDialog = false
                                                wingToRemove = wing
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0x35F43F5E),
                                                contentColor = Color(0xFFFDA4AF),
                                            ),
                                            border = BorderStroke(1.dp, Color(0xFFFB7185)),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.height(34.dp),
                                        ) {
                                            Text("Remove", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showRemoveWingDialog = false }) {
                    Text("Close")
                }
            },
        )
    }

    // Wing Permanent Removal Confirmation Dialog
    wingToRemove?.let { wing ->
        val count = remember(wing, allStudents) { allStudents.count { it.currentWingId == wing.wingId } }
        AlertDialog(
            onDismissRequest = {
                if (!isDeletingData) wingToRemove = null
            },
            title = {
                Text(
                    text = "Permanently Remove Wing ${wing.name}?",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFB7185),
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (count > 0) {
                        Text(
                            text = "⚠️ Warning: This wing currently contains $count registered student(s).",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFB7185),
                        )
                        Text(
                            text = "Removing this wing will permanently delete the wing AND ALL $count registered student(s) along with their attendance records from both the device and Cloud Firestore.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        Text(
                            text = "Are you sure you want to permanently remove ${wing.name} from the database? This cannot be undone.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isDeletingData) return@Button
                        isDeletingData = true
                        scope.launch {
                            try {
                                repository.deleteWing(hostelId, wing.wingId)
                                if (selectedWingId == wing.wingId) {
                                    selectedWingId = ""
                                }
                                successMessage = "Permanently removed wing ${wing.name} from database."
                                errorMessage = null
                                wingToRemove = null
                            } catch (e: Exception) {
                                errorMessage = e.message ?: "Failed to remove wing from database."
                            } finally {
                                isDeletingData = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE11D48),
                        contentColor = Color.White,
                    ),
                    enabled = !isDeletingData,
                ) {
                    if (isDeletingData) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Delete Wing Permanently", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { wingToRemove = null },
                    enabled = !isDeletingData,
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun EditStudentSearchSection(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    matchingStudents: List<StudentEntity>,
    wings: List<WingEntity>,
    onSelectStudent: (StudentEntity) -> Unit,
) {
    val wingMap = remember(wings) { wings.associateBy { it.wingId } }

    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Edit student details",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Search by Name, Roll Number, or Room Number to inspect and edit details",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onQueryChange,
            placeholder = { Text("e.g. 2401, Aarav, or Room 101") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = registrationControlShape,
        )

        AnimatedVisibility(
            visible = searchQuery.isNotBlank(),
            enter = expandVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)) + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            if (matchingStudents.isEmpty()) {
                Text(
                    text = "No registered resident matching '$searchQuery'",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    matchingStudents.forEach { student ->
                        val wingName = wingMap[student.currentWingId]?.name ?: "Wing"
                        val resultInteraction = rememberMotionInteractionSource()
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0x35000000),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x25FFFFFF)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .pressFeedback(resultInteraction, pressedScale = 0.97f)
                                .clickable(
                                    interactionSource = resultInteraction,
                                    indication = null,
                                ) { onSelectStudent(student) },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
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
                                        text = "Roll: #${student.rollNumber}  ·  $wingName (Room ${student.currentRoomNumber})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    text = "Edit ✏️",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrintStudentSheetSection(
    hostelId: String,
    totalCount: Int,
    onPrint: () -> Unit,
) {
    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Print all student sheet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Export all $totalCount registered residents in standard Excel-style grid format (Landscape A4 PDF)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.size(8.dp))
            Button(
                onClick = onPrint,
                enabled = totalCount > 0,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                ),
            ) {
                Text("Print / Share PDF", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ExcelImportPanel(
    context: Context,
    selectedFileName: String?,
    isParsing: Boolean,
    isCommitting: Boolean,
    analysis: ExcelImportAnalysis?,
    updateExistingDetails: Boolean,
    onUpdateExistingChange: (Boolean) -> Unit,
    onPickFile: () -> Unit,
    onCommit: () -> Unit,
) {
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
            Column {
                Text(
                    text = "Excel Sheet Bulk Import",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Template Columns: S.No. | ROOM NUMBER | STUDENT NAME | BRANCH | YEAR",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { downloadSampleTemplate(context) }) {
                Text("Get Template")
            }
        }

        // File drop/picker box
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0x22FFFFFF),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !isParsing && !isCommitting, onClick = onPickFile),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "📄",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedFileName ?: "Select Excel (.xlsx) or CSV file",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (selectedFileName != null) "Tap to change file" else "Tap to browse local device files",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isParsing) {
                    AnimatedSweepSpinner(size = 24.dp, strokeWidth = 2.5.dp)
                } else {
                    OutlinedButton(
                        onClick = onPickFile,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("Browse")
                    }
                }
            }
        }

        // Analysis Summary & Validation Preview
        AnimatedVisibility(
            visible = analysis != null,
            enter = expandVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)) + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            analysis?.let { currentAnalysis ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0x35000000),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Validation Preview",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "✓ ${currentAnalysis.validNewRows.size} New Student(s)",
                                color = Color(0xFF4CAF50),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "⚡ ${currentAnalysis.existingRows.size} Existing",
                                color = Color(0xFFFFA000),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "✕ ${currentAnalysis.errorRows.size} Error(s)",
                                color = if (currentAnalysis.errorRows.isNotEmpty()) MaterialTheme.colorScheme.error else Color.Gray,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }

                        if (currentAnalysis.existingRows.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                            ) {
                                Checkbox(
                                    checked = updateExistingDetails,
                                    onCheckedChange = onUpdateExistingChange,
                                )
                                Text(
                                    text = "Update room/wing details for existing students",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }

                        if (currentAnalysis.errorRows.isNotEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                currentAnalysis.errorRows.forEach { error ->
                                    Text(
                                        text = "Row ${error.rowNumber}: ${error.message}",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (analysis != null) {
            Button(
                onClick = onCommit,
                enabled = !isCommitting && analysis.canCommit,
                shape = registrationControlShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                if (isCommitting) {
                    AnimatedSweepSpinner(
                        size = 20.dp,
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    val count = analysis.validNewRows.size + if (updateExistingDetails) analysis.existingRows.size else 0
                    Text("Commit Import ($count Residents)", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun downloadSampleTemplate(context: Context) {
    try {
        val assetManager = context.assets
        val inputStream = assetManager.open("Student_Import_Template.xlsx")
        val cacheFile = File(context.cacheDir, "Hostel_Student_Import_Template.xlsx")
        FileOutputStream(cacheFile).use { out ->
            inputStream.copyTo(out)
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            cacheFile,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Share Student Import Template")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    } catch (_: Exception) {
    }
}

@Composable
private fun StudentListItem(
    student: StudentEntity,
    isAdmin: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backdrop = LocalGlobalGlassBackdrop.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val itemInteraction = rememberMotionInteractionSource()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlassEffect(
                backdrop = backdrop,
                density = density,
                cornerRadius = 16.dp,
                blurRadius = 12.dp,
                refractionHeightPx = 12f,
                refractionAmountPx = 18f,
                surfaceColor = Color(0x4C161324),
            )
            .pressFeedback(itemInteraction, pressedScale = 0.98f)
            .clickable(
                enabled = isAdmin,
                interactionSource = itemInteraction,
                indication = null,
                onClick = onClick,
            )
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val initials = remember(student.studentName) {
                student.studentName.split(" ")
                    .mapNotNull { it.firstOrNull()?.toString() }
                    .take(2)
                    .joinToString("")
                    .uppercase()
                    .ifBlank { "ST" }
            }
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x24FFFFFF))
                    .border(1.dp, Color(0x1EFFFFFF), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = student.studentName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "#${student.rollNumber} · Room ${student.currentRoomNumber}${student.branch?.let { " · $it" } ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!student.isActive) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                ) {
                    Text(
                        text = "Inactive",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            } else if (isAdmin) {
                Text(
                    text = "Edit →",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun RegistrationNotice(message: String, isError: Boolean = false) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        ),
        shape = RoundedCornerShape(14.dp),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(12.dp),
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun AdminRemovalToolsSection(
    onOpenRemoveStudent: () -> Unit,
    onOpenRemoveWing: () -> Unit,
) {
    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = Color(0xFFFB7185),
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "Permanent Removal Tools",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = "Permanently purge invalid resident entries, duplicate records, or obsolete wings from both device storage and Cloud Firestore.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val removeStudentInteraction = rememberMotionInteractionSource()
                OutlinedButton(
                    onClick = onOpenRemoveStudent,
                    shape = registrationControlShape,
                    border = BorderStroke(1.2.dp, Color(0xFFF43F5E).copy(alpha = 0.7f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFFDA4AF),
                        containerColor = Color(0x15F43F5E),
                    ),
                    interactionSource = removeStudentInteraction,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 50.dp)
                        .pressFeedback(removeStudentInteraction),
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Remove Student Data",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                    )
                }

                val removeWingInteraction = rememberMotionInteractionSource()
                OutlinedButton(
                    onClick = onOpenRemoveWing,
                    shape = registrationControlShape,
                    border = BorderStroke(1.2.dp, Color(0xFFF43F5E).copy(alpha = 0.7f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFFDA4AF),
                        containerColor = Color(0x15F43F5E),
                    ),
                    interactionSource = removeWingInteraction,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 50.dp)
                        .pressFeedback(removeWingInteraction),
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Remove Wing",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}
