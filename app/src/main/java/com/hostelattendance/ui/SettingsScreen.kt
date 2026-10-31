package com.hostelattendance.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hostelattendance.HostelAttendanceApplication
import com.hostelattendance.data.sync.FirestoreSyncService
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    hostelId: String,
    role: String,
    userEmail: String,
    onSignOut: () -> Unit,
) {
    val context = LocalContext.current
    val application = context.applicationContext as HostelAttendanceApplication
    val scope = rememberCoroutineScope()
    var isSyncing by remember { mutableStateOf(false) }

    val allStudents by application.studentRepository.observeAllStudents(hostelId).collectAsState(initial = emptyList())
    val allWings by application.studentRepository.observeWings(hostelId).collectAsState(initial = emptyList())

    val activeCount = remember(allStudents) { allStudents.count { it.isActive } }
    val inactiveCount = remember(allStudents) { allStudents.count { !it.isActive } }
    val wingsCount = remember(allWings) { allWings.size }

    var showLogoutDialog by rememberSaveable { mutableStateOf(false) }

    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val timeGreeting = when (hour) {
        in 5..11 -> "Good Morning"
        in 12..16 -> "Good Afternoon"
        in 17..21 -> "Good Evening"
        else -> "Good Night"
    }

    val firebaseUser = try {
        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
    } catch (_: Exception) {
        null
    }

    val rawName = firebaseUser?.displayName?.takeIf { it.isNotBlank() }
        ?: userEmail.substringBefore("@").replace(".", " ").takeIf { it.isNotBlank() }
        ?: "Dev"

    val userName = rawName.split(" ").firstOrNull()?.replaceFirstChar {
        if (it.isLowerCase()) it.titlecase() else it.toString()
    } ?: "Dev"

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "settings_header") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Hello $userName, $timeGreeting",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "System configuration and active session",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Top Section: Sign out option
        item(key = "settings_sign_out_section") {
            GlassPanel(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Account Session",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = if (userEmail.isNotBlank()) userEmail else "Authorized Session",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    ) {
                        Text(
                            text = role,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }

                val interactionSource = rememberMotionInteractionSource()
                val backdrop = LocalGlobalGlassBackdrop.current
                val density = androidx.compose.ui.platform.LocalDensity.current

                val signOutBtnModifier = if (backdrop != null) {
                    Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .liquidGlassCapsuleEffect(
                            backdrop = backdrop,
                            density = density,
                            blurRadius = 14.dp,
                            surfaceColor = Color(0xD91E1C22),
                            specular = com.styropyr0.prismal.specular.PrismalSpecular(
                                width = 1.dp,
                                alpha = 0.85f,
                                style = com.styropyr0.prismal.specular.PrismalSpecularStyle.Ambient,
                            ),
                            depthShadow = com.styropyr0.prismal.depth.PrismalDepthShadow(
                                radius = 10.dp,
                                color = Color.Black.copy(alpha = 0.50f),
                            ),
                        )
                        .pressFeedback(interactionSource)
                } else {
                    Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .shadow(8.dp, RoundedCornerShape(25.dp))
                        .clip(RoundedCornerShape(25.dp))
                        .background(Color(0xFF222026))
                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(25.dp))
                        .pressFeedback(interactionSource)
                }

                Button(
                    onClick = { showLogoutDialog = true },
                    interactionSource = interactionSource,
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (backdrop != null) Color.Transparent else Color(0xFF222026),
                        contentColor = Color.White,
                    ),
                    modifier = signOutBtnModifier,
                ) {
                    Text("Sign out", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        // Cloud Synchronization Card
        item(key = "settings_cloud_sync_section") {
            GlassPanel(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Cloud Synchronization",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Cloud Firestore automatic data sync & recovery",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0x3310B981),
                        border = BorderStroke(1.dp, Color(0x6610B981)),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF10B981)),
                            )
                            Text(
                                text = "Synced",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF10B981),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                SettingRow(label = "Hostel Space", value = hostelId)
                SettingRow(label = "Active Students Synced", value = "$activeCount students")
                SettingRow(label = "Inactive Students in Cloud", value = "$inactiveCount students")
                SettingRow(label = "Hostel Wings Synced", value = "$wingsCount wings")

                val syncInteractionSource = rememberMotionInteractionSource()
                val backdrop = LocalGlobalGlassBackdrop.current
                val density = androidx.compose.ui.platform.LocalDensity.current

                val syncBtnModifier = if (backdrop != null) {
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .liquidGlassCapsuleEffect(
                            backdrop = backdrop,
                            density = density,
                            blurRadius = 14.dp,
                            surfaceColor = Color(0xC0182A24),
                            specular = com.styropyr0.prismal.specular.PrismalSpecular(
                                width = 1.dp,
                                alpha = 0.85f,
                                style = com.styropyr0.prismal.specular.PrismalSpecularStyle.Ambient,
                            ),
                            depthShadow = com.styropyr0.prismal.depth.PrismalDepthShadow(
                                radius = 8.dp,
                                color = Color.Black.copy(alpha = 0.40f),
                            ),
                        )
                        .pressFeedback(syncInteractionSource)
                } else {
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .shadow(6.dp, RoundedCornerShape(24.dp))
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF1B2E26))
                        .border(1.dp, Color(0x3310B981), RoundedCornerShape(24.dp))
                        .pressFeedback(syncInteractionSource)
                }

                Button(
                    onClick = {
                        if (!isSyncing) {
                            scope.launch {
                                isSyncing = true
                                try {
                                    val summary = FirestoreSyncService.syncAllData(
                                        hostelId = hostelId,
                                        database = application.database,
                                        isAdmin = (role == "ADMIN"),
                                    )
                                    Toast.makeText(
                                        context,
                                        "Sync complete! ${summary.restoredStudents} students, ${summary.restoredWings} wings, ${summary.restoredRecords} attendance records synced.",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Sync error: ${e.message}", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isSyncing = false
                                }
                            }
                        }
                    },
                    enabled = !isSyncing,
                    interactionSource = syncInteractionSource,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (backdrop != null) Color.Transparent else Color(0xFF1B2E26),
                        contentColor = Color(0xFF34D399),
                    ),
                    modifier = syncBtnModifier,
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color(0xFF34D399),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("☁  Sync Cloud Now", fontWeight = FontWeight.Bold, color = Color(0xFF34D399))
                    }
                }
            }
        }

        // Application Details
        item(key = "settings_app_details") {
            GlassPanel(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Application Information",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )

                SettingRow(label = "Application", value = "Hostel Attendance App")
                SettingRow(label = "Version", value = "2.0.0")
                SettingRow(label = "Designer and Developer", value = "Devendra Vishwakarma")
                SettingRow(label = "Design System", value = "Motorsport Glassmorphism")
            }
        }
    }

    if (showLogoutDialog) {
        AppleGlassDialog(
            title = "Do you want to log out ?",
            confirmText = "Yes",
            dismissText = "No",
            isDestructive = true,
            onConfirm = {
                showLogoutDialog = false
                onSignOut()
            },
            onDismiss = {
                showLogoutDialog = false
            },
        )
    }
}

@Composable
private fun SettingRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
        )
    }
}
