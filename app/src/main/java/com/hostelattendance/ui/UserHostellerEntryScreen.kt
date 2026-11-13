package com.hostelattendance.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hostelattendance.HostelAttendanceApplication
import com.hostelattendance.data.local.StudentEntity
import com.hostelattendance.data.local.UserSessionManager
import com.hostelattendance.domain.UserSessionPrivacyRules
import com.hostelattendance.domain.StudentIdentityResolver
import kotlinx.coroutines.launch
import java.time.LocalTime

@Composable
fun UserHostellerEntryScreen(
    hostelId: String,
    uid: String,
    userEmail: String? = null,
    initialName: String = "",
    initialRoom: String = "",
    onSignOut: (() -> Unit)? = null,
    onEnter: (hostellerName: String, roomNumber: String, studentId: String?, rollNumber: String?) -> Unit,
) {
    val context = LocalContext.current
    val application = context.applicationContext as HostelAttendanceApplication
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val localBoundName = remember(userEmail) {
        if (!userEmail.isNullOrBlank()) UserSessionManager.getBoundHostellerName(context, userEmail) else null
    }
    val localBoundRoom = remember(userEmail) {
        if (!userEmail.isNullOrBlank()) UserSessionManager.getBoundRoomNumber(context, userEmail) else null
    }

    var nameInput by rememberSaveable(userEmail) {
        mutableStateOf(if (initialName.isNotBlank()) initialName else (localBoundName ?: ""))
    }
    var roomInput by rememberSaveable(userEmail) {
        mutableStateOf(if (initialRoom.isNotBlank()) initialRoom else (localBoundRoom ?: ""))
    }
    var selectedStudent by remember { mutableStateOf<StudentEntity?>(null) }
    var isTrayDismissed by remember { mutableStateOf(false) }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isVerifying by remember { mutableStateOf(false) }
    var showErrorDialog by remember { mutableStateOf(false) }

    val allStudents by application.studentRepository.observeAllStudents(hostelId).collectAsState(initial = emptyList())

    // Auto-restore roster from Firestore if local database is empty
    LaunchedEffect(hostelId, allStudents.isEmpty()) {
        if (allStudents.isEmpty()) {
            scope.launch {
                try {
                    com.hostelattendance.data.sync.FirestoreSyncService.restoreWingsAndStudentsFromFirestore(
                        hostelId,
                        application.database.wingDao(),
                        application.database.studentDao(),
                    )
                } catch (_: Exception) {}
            }
        }
    }

    // Auto-fetch cloud bindings for this email if not populated
    LaunchedEffect(userEmail) {
        if (!userEmail.isNullOrBlank()) {
            val cloudBinding = UserSessionManager.fetchBindingFromCloud(context, userEmail, uid)
            if (cloudBinding != null) {
                if (nameInput.isBlank()) nameInput = cloudBinding.first
                if (roomInput.isBlank()) roomInput = cloudBinding.second
            }
        }
    }

    // Filter students for the real-time related name tray bar
    val nameSuggestions = remember(nameInput, allStudents) {
        val q = nameInput.trim()
        if (q.isBlank()) {
            emptyList()
        } else {
            allStudents.filter { student ->
                student.studentName.contains(q, ignoreCase = true) ||
                student.currentRoomNumber.contains(q, ignoreCase = true) ||
                student.rollNumber.contains(q, ignoreCase = true)
            }.sortedWith(
                compareBy(
                    { !it.studentName.startsWith(q, ignoreCase = true) },
                    { it.studentName }
                )
            ).take(5)
        }
    }

    val greeting = remember { UserSessionPrivacyRules.getGreetingForTime(LocalTime.now()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item(key = "header") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = greeting,
                        style = TextStyle(
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Normal,
                            fontStyle = FontStyle.Italic,
                            fontFamily = FontFamily.Serif,
                            color = Color(0xFFE9D5FF),
                        ),
                    )
                    Text(
                        text = "Hosteller Sign In",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        ),
                    )
                    Text(
                        text = "Enter your registered hosteller name and room number.",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.72f),
                    )

                    // Signed-in email indicator badge
                    if (!userEmail.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x256366F1))
                                .border(BorderStroke(1.dp, Color(0x40818CF8)), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color(0xFFC084FC),
                                modifier = Modifier.size(13.dp),
                            )
                            Text(
                                text = "Account: $userEmail",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFDDD6FE),
                            )
                        }
                    }
                }
            }

            // Error Message Banner (Prominently displayed when "Do not Enter other's Credential" is triggered)
            if (errorMessage != null) {
                item(key = "error_banner") {
                    AnimatedVisibility(
                        visible = errorMessage != null,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0x3DF43F5E),
                            border = BorderStroke(1.5.dp, Color(0xFFFB7185)),
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFFDA4AF),
                                    modifier = Modifier.size(24.dp),
                                )
                                Column {
                                    Text(
                                        text = "Verification Blocked",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color(0xFFFFF1F2),
                                    )
                                    Text(
                                        text = errorMessage ?: "",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFFDA4AF),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item(key = "input_card") {
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
                        .padding(20.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // Hosteller Name Input
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Hosteller Name",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.9f),
                            )

                            OutlinedTextField(
                                value = nameInput,
                                onValueChange = {
                                    nameInput = it
                                    errorMessage = null
                                    isTrayDismissed = false
                                },
                                placeholder = {
                                    Text(
                                        "Type your name (e.g. Devendra)",
                                        color = Color.White.copy(alpha = 0.45f)
                                    )
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFF818CF8),
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                                    focusedContainerColor = Color(0x201E1B4B),
                                    unfocusedContainerColor = Color(0x151E1B4B),
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                modifier = Modifier.fillMaxWidth(),
                            )

                            // Related Name Tray Bar
                            AnimatedVisibility(
                                visible = !isTrayDismissed && nameSuggestions.isNotEmpty() && nameInput.trim().isNotEmpty(),
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut(),
                            ) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color(0xF2181528),
                                    border = BorderStroke(1.dp, Color(0x50818CF8)),
                                    shadowElevation = 10.dp,
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                    ) {
                                        Text(
                                            text = "Matching Hostellers (Tap to select):",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFFC084FC),
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                        )

                                        nameSuggestions.forEach { student ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        nameInput = student.studentName
                                                        roomInput = student.currentRoomNumber
                                                        selectedStudent = student
                                                        isTrayDismissed = true
                                                        errorMessage = null
                                                        focusManager.clearFocus()
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
                                                        color = Color.White,
                                                    )
                                                    Text(
                                                        text = "Room ${student.currentRoomNumber} · Roll #${student.rollNumber}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color.White.copy(alpha = 0.65f),
                                                    )
                                                }

                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(Color(0x356366F1))
                                                        .border(
                                                            BorderStroke(1.dp, Color(0x60818CF8)),
                                                            RoundedCornerShape(8.dp)
                                                        )
                                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                                ) {
                                                    Text(
                                                        text = "Select",
                                                        fontSize = 12.sp,
                                                        color = Color(0xFFC084FC),
                                                        fontWeight = FontWeight.Bold,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Room Number Input
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Room Number",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.9f),
                            )

                            OutlinedTextField(
                                value = roomInput,
                                onValueChange = {
                                    roomInput = it
                                    errorMessage = null
                                },
                                placeholder = {
                                    Text(
                                        "e.g. 102",
                                        color = Color.White.copy(alpha = 0.45f)
                                    )
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFF818CF8),
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                                    focusedContainerColor = Color(0x201E1B4B),
                                    unfocusedContainerColor = Color(0x151E1B4B),
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(
                                    onDone = { focusManager.clearFocus() }
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            // Bottom "Enter" Button & Validation Action
            item(key = "enter_button") {
                val canEnter = nameInput.isNotBlank() && roomInput.isNotBlank() && !isVerifying

                Button(
                    onClick = {
                        if (!canEnter) return@Button
                        focusManager.clearFocus()
                        errorMessage = null
                        isVerifying = true

                        scope.launch {
                            try {
                                var roster = allStudents
                                if (roster.isEmpty()) {
                                    roster = application.database.studentDao().getAllStudents(hostelId)
                                }

                                val resolvedStudent = selectedStudent ?: StudentIdentityResolver.resolveStudent(
                                    roster = roster,
                                    targetStudentId = null,
                                    targetRollNumber = null,
                                    targetName = nameInput.trim(),
                                    targetRoomNumber = roomInput.trim(),
                                )

                                val sId = resolvedStudent?.studentId
                                val roll = resolvedStudent?.rollNumber

                                // Validate against email-credential binding
                                val (isValid, errorMsg) = UserSessionManager.validateAndBind(
                                    context = context,
                                    email = userEmail.orEmpty(),
                                    uid = uid,
                                    enteredName = nameInput.trim(),
                                    enteredRoom = roomInput.trim(),
                                    studentId = sId,
                                    rollNumber = roll,
                                )

                                if (!isValid) {
                                    errorMessage = errorMsg ?: UserSessionManager.ERROR_OTHER_CREDENTIALS
                                    showErrorDialog = true
                                    isVerifying = false
                                    return@launch
                                }

                                isVerifying = false
                                onEnter(nameInput.trim(), roomInput.trim(), sId, roll)
                            } catch (_: Exception) {
                                isVerifying = false
                                errorMessage = "Verification failed. Please check connection and try again."
                            }
                        }
                    },
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (canEnter) Color(0xFF6366F1) else Color(0x406366F1),
                        contentColor = Color.White,
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (canEnter) Color(0xFF818CF8) else Color.White.copy(alpha = 0.15f)
                    ),
                    enabled = canEnter,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                ) {
                    if (isVerifying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.5.dp,
                        )
                    } else {
                        Text(
                            text = "Enter  ➔",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            // Optional Switch Account / Sign Out button
            if (onSignOut != null) {
                item(key = "switch_account") {
                    TextButton(
                        onClick = onSignOut,
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Text(
                            text = "Sign in with a different Google account",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }
    }

    // Access Denied Alert Dialog: stops the user in the login page
    if (showErrorDialog) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = false },
            title = {
                Text(
                    text = "Access Blocked",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            },
            text = {
                Text(
                    text = errorMessage ?: UserSessionManager.ERROR_OTHER_CREDENTIALS,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 15.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = { showErrorDialog = false }) {
                    Text("OK", color = Color(0xFFC084FC), fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF1E1B4B),
            shape = RoundedCornerShape(20.dp),
        )
    }
}
