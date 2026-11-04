package com.hostelattendance.ui

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/**
 * Detects device hardware profile to dynamically scale graphics complexity
 * and guarantee 60+ FPS on budget / low-end Android devices.
 */
object DevicePerformanceManager {
    private var isLowEndCached: Boolean? = null

    fun isLowEndDevice(context: Context): Boolean {
        isLowEndCached?.let { return it }
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val isLowRam = am?.isLowRamDevice == true
        val memInfo = ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }
        val totalRamGb = memInfo.totalMem.toDouble() / (1024.0 * 1024.0 * 1024.0)
        val cores = Runtime.getRuntime().availableProcessors()

        // Low-end criteria: low-ram device flag, RAM <= 3.5GB, <= 4 CPU cores, or Android < 13
        val isLowEnd = isLowRam || totalRamGb <= 3.5 || cores <= 4 || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
        isLowEndCached = isLowEnd
        return isLowEnd
    }
}
