package com.hostelattendance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.hostelattendance.ui.HostelAttendanceApp
import com.hostelattendance.ui.theme.HostelAttendanceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HostelAttendanceTheme {
                com.hostelattendance.ui.AppRootScaffold()
            }
        }
    }
}
