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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hostelattendance.BuildConfig
import com.hostelattendance.HostelAttendanceApplication
import com.hostelattendance.data.backup.PersistentBackupManager
import com.hostelattendance.data.local.AppUpdateHistoryEntity
import com.hostelattendance.data.sync.FirestoreSyncService
import com.hostelattendance.data.update.AppUpdateManager
import com.hostelattendance.data.update.AppUpdateResult
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

    val latestDownload by application.database.appUpdateDao().getLatestDownloadFlow().collectAsState(initial = null)

    var updateResult by remember { mutableStateOf<AppUpdateResult?>(null) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var downloadProgressFraction by remember { mutableStateOf(0f) }
    var downloadProgressText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        isCheckingUpdate = true
        updateResult = AppUpdateManager.checkForUpdates()
        isCheckingUpdate = false
    }

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
                                    PersistentBackupManager.triggerAutoBackup()
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

        // App Updates Card
        item(key = "settings_app_updates") {
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
                            text = "App Updates",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Installed: v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Status Badge
                    when (updateResult) {
                        is AppUpdateResult.UpdateAvailable -> {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF3B82F6).copy(alpha = 0.22f))
                                    .border(1.dp, Color(0xFF60A5FA).copy(alpha = 0.70f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(RoundedCornerShape(percent = 50))
                                            .background(Color(0xFF60A5FA)),
                                    )
                                    Text(
                                        text = "Updates Available to download",
                                        color = Color(0xFF60A5FA),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                        is AppUpdateResult.UpToDate -> {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF10B981).copy(alpha = 0.18f))
                                    .border(1.dp, Color(0xFF34D399).copy(alpha = 0.60f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(RoundedCornerShape(percent = 50))
                                            .background(Color(0xFF34D399)),
                                    )
                                    Text(
                                        text = "No updates available",
                                        color = Color(0xFF34D399),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                        else -> {}
                    }
                }

                SettingRow(label = "Installed Version", value = "v${BuildConfig.VERSION_NAME}")
                latestDownload?.let { dl ->
                    SettingRow(label = "Last Downloaded", value = "v${dl.versionName} (${dl.formattedDate})")
                }

                // If Update Available: Details & Download Button
                val available = updateResult as? AppUpdateResult.UpdateAvailable
                if (available != null) {
                    val info = available.updateInfo
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF1E293B).copy(alpha = 0.6f))
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(14.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "New Version: ${info.versionName}",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                            )
                            if (info.fileSizeMb.isNotBlank()) {
                                Text(
                                    text = "${info.fileSizeMb} MB",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF94A3B8),
                                )
                            }
                        }

                        if (info.releaseNotes.isNotBlank()) {
                            Text(
                                text = info.releaseNotes,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFCBD5E1),
                            )
                        }

                        if (isDownloadingUpdate) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (downloadProgressFraction >= 0f) {
                                    LinearProgressIndicator(
                                        progress = { downloadProgressFraction },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = Color(0xFF3B82F6),
                                        trackColor = Color(0xFF334155),
                                    )
                                } else {
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = Color(0xFF3B82F6),
                                        trackColor = Color(0xFF334155),
                                    )
                                }
                                Text(
                                    text = downloadProgressText.ifBlank { "Downloading update..." },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF94A3B8),
                                )
                            }
                        } else {
                            Button(
                                onClick = {
                                    scope.launch {
                                        isDownloadingUpdate = true
                                        downloadProgressFraction = 0f
                                        downloadProgressText = "Connecting..."
                                        try {
                                            val file = AppUpdateManager.downloadApk(
                                                context = context,
                                                apkUrl = info.apkUrl,
                                                versionName = info.versionName,
                                                versionCode = info.versionCode,
                                                database = application.database,
                                                onProgress = { fraction, downloaded, total ->
                                                    downloadProgressFraction = fraction
                                                    val downMb = String.format(java.util.Locale.US, "%.1f", downloaded / (1024f * 1024f))
                                                    downloadProgressText = if (total > 0) {
                                                        val totalMb = String.format(java.util.Locale.US, "%.1f", total / (1024f * 1024f))
                                                        "${(fraction * 100).toInt()}% ($downMb MB / $totalMb MB)"
                                                    } else {
                                                        "$downMb MB downloaded"
                                                    }
                                                }
                                            )
                                            downloadProgressText = "Installing update..."
                                            AppUpdateManager.installApk(context, file)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
                                        } finally {
                                            isDownloadingUpdate = false
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF2563EB),
                                    contentColor = Color.White,
                                ),
                            ) {
                                Text("⬇  Download & Install Update", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    // Check for Updates Button
                    Button(
                        onClick = {
                            scope.launch {
                                isCheckingUpdate = true
                                val res = AppUpdateManager.checkForUpdates()
                                updateResult = res
                                isCheckingUpdate = false
                                when (res) {
                                    is AppUpdateResult.UpToDate -> {
                                        Toast.makeText(context, "No updates available (v${res.currentVersionName})", Toast.LENGTH_SHORT).show()
                                    }
                                    is AppUpdateResult.Error -> {
                                        Toast.makeText(context, "Update check: ${res.message}", Toast.LENGTH_SHORT).show()
                                    }
                                    is AppUpdateResult.UpdateAvailable -> {
                                        Toast.makeText(context, "Updates Available to download: v${res.updateInfo.versionName}!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        enabled = !isCheckingUpdate,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1E293B),
                            contentColor = Color(0xFF93C5FD),
                        ),
                        border = BorderStroke(1.dp, Color(0xFF3B82F6).copy(alpha = 0.4f)),
                    ) {
                        if (isCheckingUpdate) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color(0xFF93C5FD),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("🔄  Check for Updates", fontWeight = FontWeight.SemiBold)
                        }
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
                SettingRow(label = "Version", value = BuildConfig.VERSION_NAME)
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
