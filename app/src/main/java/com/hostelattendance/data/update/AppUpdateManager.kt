package com.hostelattendance.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.hostelattendance.BuildConfig
import com.hostelattendance.data.local.AppUpdateHistoryEntity
import com.hostelattendance.data.local.HostelDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val releaseNotes: String = "",
    val fileSizeMb: String = "",
    val publishedAtMillis: Long = 0L,
)

sealed interface AppUpdateResult {
    data class UpToDate(
        val currentVersionName: String,
        val currentVersionCode: Int,
        val remoteVersionName: String = currentVersionName,
        val latestApkUrl: String = AppUpdateManager.DEFAULT_APK_URL,
    ) : AppUpdateResult

    data class UpdateAvailable(
        val updateInfo: AppUpdateInfo,
        val currentVersionName: String,
        val currentVersionCode: Int,
    ) : AppUpdateResult

    data class Error(val message: String) : AppUpdateResult
}

object AppUpdateManager {

    private const val TAG = "AppUpdateManager"
    private const val UPDATES_COLLECTION = "app_updates"
    private const val LATEST_DOC_ID = "latest"

    const val GITHUB_RAW_VERSION_NAME_URL = "https://raw.githubusercontent.com/devendrav2324-netizen/the-boyz/main/apk/version_name"
    const val GITHUB_FALLBACK_VERSION_NAME_URL = "https://github.com/devendrav2324-netizen/the-boyz/raw/main/apk/version_name"
    const val GITHUB_ROOT_VERSION_NAME_URL = "https://raw.githubusercontent.com/devendrav2324-netizen/the-boyz/main/version_name"

    const val DEFAULT_APK_URL = "https://github.com/devendrav2324-netizen/the-boyz/raw/main/apk/the-boyz-latest.apk"
    const val GITHUB_APK_BASE_URL = "https://github.com/devendrav2324-netizen/the-boyz/raw/main/apk"
    const val DEFAULT_RELEASE_NOTES = "• In-App APK Updater with automatic GitHub version detection\n• Uninstall-proof phone backup in hostel_attendance_local_data\n• Local SQLite download history & timestamp tracking\n• Range-based attendance calculation\n• Liquid glassmorphism UI & Apple spring animations"
    const val DEFAULT_LATEST_VERSION_CODE = 17
    const val DEFAULT_LATEST_VERSION_NAME = "1.13.4"
    const val DEFAULT_FILE_SIZE_MB = "24.9"

    /**
     * Constructs the direct download URL for a specific versioned APK in GitHub.
     * Rule: Each APK in GitHub apk/ folder is named <version_name>.apk (e.g. 1.13.4.apk).
     */
    fun getVersionedApkUrl(versionName: String): String {
        val clean = versionName.trim().removePrefix("v").removePrefix("V")
        return "$GITHUB_APK_BASE_URL/$clean.apk"
    }

    /**
     * Compares two semantic version strings (e.g. "1.13.1" vs "1.13.0").
     * Returns:
     *   > 0 if version1 > version2
     *   < 0 if version1 < version2
     *   0 if version1 == version2
     */
    fun compareVersions(version1: String, version2: String): Int {
        val clean1 = version1.trim().removePrefix("v").removePrefix("V")
        val clean2 = version2.trim().removePrefix("v").removePrefix("V")

        val parts1 = clean1.split(".").map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
        val parts2 = clean2.split(".").map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }

        val maxLength = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLength) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) {
                return p1.compareTo(p2)
            }
        }
        return 0
    }

    /**
     * Directly fetches the latest published version name from the GitHub repository.
     */
    suspend fun fetchGitHubVersionName(): String? = withContext(Dispatchers.IO) {
        val urls = listOf(
            GITHUB_RAW_VERSION_NAME_URL,
            GITHUB_FALLBACK_VERSION_NAME_URL,
            GITHUB_ROOT_VERSION_NAME_URL,
        )

        for (urlString in urls) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL(urlString)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    instanceFollowRedirects = true
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "HostelAttendance-Android")
                }

                if (connection.responseCode in 200..299) {
                    val content = connection.inputStream.bufferedReader().use { it.readText() }.trim()
                    if (content.isNotBlank() && (content[0].isDigit() || content.startsWith("v", ignoreCase = true))) {
                        Log.d(TAG, "Fetched GitHub version_name: '$content' from $urlString")
                        return@withContext content
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch version_name from $urlString: ${e.message}")
            } finally {
                connection?.disconnect()
            }
        }
        null
    }

    /**
     * Publishes or updates the latest release metadata in Cloud Firestore.
     */
    suspend fun publishUpdateMetadata(
        versionCode: Int = DEFAULT_LATEST_VERSION_CODE,
        versionName: String = DEFAULT_LATEST_VERSION_NAME,
        apkUrl: String = DEFAULT_APK_URL,
        releaseNotes: String = DEFAULT_RELEASE_NOTES,
        fileSizeMb: String = DEFAULT_FILE_SIZE_MB,
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            FirebaseFirestore.getInstance()
                .collection(UPDATES_COLLECTION)
                .document(LATEST_DOC_ID)
                .set(
                    mapOf(
                        "versionCode" to versionCode,
                        "versionName" to versionName,
                        "apkUrl" to apkUrl,
                        "releaseNotes" to releaseNotes,
                        "fileSizeMb" to fileSizeMb,
                        "publishedAtMillis" to System.currentTimeMillis(),
                    ),
                    SetOptions.merge()
                )
                .await()
            Log.d(TAG, "Published app update metadata to Firestore: v$versionName (code $versionCode)")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to publish app update metadata: ${e.message}")
            false
        }
    }

    /**
     * Checks GitHub for the latest version_name and compares it against BuildConfig.VERSION_NAME.
     * If GitHub remoteVersion > currentVersion: returns UpdateAvailable ("Updates Available to download")
     * If GitHub remoteVersion == currentVersion: returns UpToDate ("No updates available")
     * Gracefully falls back to Firestore when GitHub is unreachable.
     */
    suspend fun checkForUpdates(): AppUpdateResult = withContext(Dispatchers.IO) {
        val currentCode = BuildConfig.VERSION_CODE
        val currentName = BuildConfig.VERSION_NAME

        // 1. Direct GitHub version_name check
        val githubVersion = fetchGitHubVersionName()
        if (!githubVersion.isNullOrBlank()) {
            val comparison = compareVersions(githubVersion, currentName)
            val versionedApkUrl = getVersionedApkUrl(githubVersion)
            return@withContext if (comparison > 0) {
                AppUpdateResult.UpdateAvailable(
                    updateInfo = AppUpdateInfo(
                        versionCode = currentCode + 1,
                        versionName = githubVersion,
                        apkUrl = versionedApkUrl,
                        releaseNotes = "• New version $githubVersion available on GitHub repository\n$DEFAULT_RELEASE_NOTES",
                        fileSizeMb = DEFAULT_FILE_SIZE_MB,
                        publishedAtMillis = System.currentTimeMillis(),
                    ),
                    currentVersionName = currentName,
                    currentVersionCode = currentCode,
                )
            } else {
                AppUpdateResult.UpToDate(
                    currentVersionName = currentName,
                    currentVersionCode = currentCode,
                    remoteVersionName = githubVersion,
                    latestApkUrl = versionedApkUrl,
                )
            }
        }

        // 2. Fallback: Check Cloud Firestore
        try {
            val doc = try {
                FirebaseFirestore.getInstance()
                    .collection(UPDATES_COLLECTION)
                    .document(LATEST_DOC_ID)
                    .get()
                    .await()
            } catch (e: Exception) {
                null
            }

            val remoteCode = doc?.getLong("versionCode")?.toInt() ?: DEFAULT_LATEST_VERSION_CODE
            val remoteName = doc?.getString("versionName")?.takeIf { it.isNotBlank() } ?: DEFAULT_LATEST_VERSION_NAME
            val apkUrl = doc?.getString("apkUrl")?.trim()?.takeIf { it.isNotBlank() } ?: getVersionedApkUrl(remoteName)
            val releaseNotes = doc?.getString("releaseNotes")?.takeIf { it.isNotBlank() } ?: DEFAULT_RELEASE_NOTES
            val fileSizeMb = doc?.getString("fileSizeMb")?.takeIf { it.isNotBlank() } ?: DEFAULT_FILE_SIZE_MB
            val publishedAt = doc?.getLong("publishedAtMillis") ?: System.currentTimeMillis()

            val comparison = compareVersions(remoteName, currentName)
            if (comparison > 0 || remoteCode > currentCode) {
                AppUpdateResult.UpdateAvailable(
                    updateInfo = AppUpdateInfo(
                        versionCode = remoteCode,
                        versionName = remoteName,
                        apkUrl = apkUrl,
                        releaseNotes = releaseNotes,
                        fileSizeMb = fileSizeMb,
                        publishedAtMillis = publishedAt,
                    ),
                    currentVersionName = currentName,
                    currentVersionCode = currentCode,
                )
            } else {
                AppUpdateResult.UpToDate(
                    currentVersionName = currentName,
                    currentVersionCode = currentCode,
                    remoteVersionName = remoteName,
                    latestApkUrl = apkUrl,
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking for updates in Firestore: ${e.message}")
            AppUpdateResult.UpToDate(
                currentVersionName = currentName,
                currentVersionCode = currentCode,
                remoteVersionName = currentName,
                latestApkUrl = getVersionedApkUrl(currentName),
            )
        }
    }

    /**
     * Downloads the APK file from [apkUrl] with real-time progress callbacks.
     * Records download metadata into the Room database upon successful completion.
     */
    suspend fun downloadApk(
        context: Context,
        apkUrl: String,
        versionName: String = DEFAULT_LATEST_VERSION_NAME,
        versionCode: Int = BuildConfig.VERSION_CODE,
        database: HostelDatabase? = null,
        onProgress: (progressFraction: Float, downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val updatesDir = File(context.cacheDir, "updates")
        if (!updatesDir.exists()) {
            updatesDir.mkdirs()
        }
        val targetFile = File(updatesDir, "app-update.apk")
        if (targetFile.exists()) {
            targetFile.delete()
        }

        var connection: HttpURLConnection? = null
        try {
            val url = URL(apkUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty("User-Agent", "HostelAttendance-Android")
            }
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                error("Server returned HTTP $responseCode: ${connection.responseMessage}")
            }

            val totalBytes = connection.contentLength.toLong().takeIf { it > 0 } ?: -1L
            var downloadedBytes = 0L

            connection.inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        val fraction = if (totalBytes > 0) {
                            (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                        } else {
                            -1f
                        }
                        onProgress(fraction, downloadedBytes, totalBytes)
                    }
                    output.flush()
                }
            }

            // Record download event in local SQLite Room database
            if (database != null) {
                try {
                    val formattedDate = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
                    val record = AppUpdateHistoryEntity(
                        versionName = versionName,
                        versionCode = versionCode,
                        downloadedAtMillis = System.currentTimeMillis(),
                        formattedDate = formattedDate,
                        fileSizeBytes = targetFile.length(),
                        apkSourceUrl = apkUrl,
                        status = "DOWNLOADED",
                    )
                    database.appUpdateDao().insertDownloadRecord(record)
                    Log.d(TAG, "Recorded download into local SQLite database: $versionName on $formattedDate")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to insert download history: ${e.message}")
                }
            }

            targetFile
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Installs the downloaded APK by launching Android package installer via FileProvider.
     */
    fun installApk(context: Context, apkFile: File) {
        require(apkFile.exists()) { "Update APK file does not exist: ${apkFile.absolutePath}" }

        // On Android 8.0+, verify unknown app install permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val settingsIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(settingsIntent)
                return
            }
        }

        val authority = "${context.packageName}.fileprovider"
        val apkUri = FileProvider.getUriForFile(context, authority, apkFile)

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(installIntent)
    }
}
