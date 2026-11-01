package com.hostelattendance.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.hostelattendance.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

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

    /**
     * Checks Cloud Firestore for a newer version than current BuildConfig.VERSION_CODE
     */
    suspend fun checkForUpdates(): AppUpdateResult = withContext(Dispatchers.IO) {
        val currentCode = BuildConfig.VERSION_CODE
        val currentName = BuildConfig.VERSION_NAME

        try {
            val doc = FirebaseFirestore.getInstance()
                .collection(UPDATES_COLLECTION)
                .document(LATEST_DOC_ID)
                .get()
                .await()

            if (!doc.exists()) {
                return@withContext AppUpdateResult.UpToDate(currentName, currentCode)
            }

            val remoteCode = doc.getLong("versionCode")?.toInt() ?: 0
            val remoteName = doc.getString("versionName") ?: ""
            val apkUrl = doc.getString("apkUrl")?.trim() ?: ""
            val releaseNotes = doc.getString("releaseNotes") ?: ""
            val fileSizeMb = doc.getString("fileSizeMb") ?: ""
            val publishedAt = doc.getLong("publishedAtMillis") ?: 0L

            if (remoteCode > currentCode && apkUrl.isNotBlank()) {
                val updateInfo = AppUpdateInfo(
                    versionCode = remoteCode,
                    versionName = remoteName.ifBlank { "v$remoteCode" },
                    apkUrl = apkUrl,
                    releaseNotes = releaseNotes,
                    fileSizeMb = fileSizeMb,
                    publishedAtMillis = publishedAt,
                )
                AppUpdateResult.UpdateAvailable(
                    updateInfo = updateInfo,
                    currentVersionName = currentName,
                    currentVersionCode = currentCode,
                )
            } else {
                AppUpdateResult.UpToDate(currentName, currentCode)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking for updates: ${e.message}")
            AppUpdateResult.Error(e.message ?: "Failed to check for updates.")
        }
    }

    /**
     * Downloads the APK file from [apkUrl] with real-time progress callbacks.
     */
    suspend fun downloadApk(
        context: Context,
        apkUrl: String,
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
