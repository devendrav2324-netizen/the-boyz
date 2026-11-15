package com.hostelattendance.data.local

data class AttendanceDisputeItem(
    val disputeId: String,
    val hostelId: String,
    val studentId: String,
    val studentName: String,
    val rollNumber: String = "",
    val roomNumber: String = "",
    val localDate: String,
    val markedStatus: String,
    val claimedStatus: String,
    val reason: String,
    val createdAtMillis: Long,
    val status: String = "PENDING", // PENDING, RESOLVED, REJECTED
    val resolvedStatus: String? = null,
    val resolvedAtMillis: Long? = null,
)
