package com.hostelattendance.auth

data class ApprovedAccount(
    val uid: String,
    val hostelId: String,
    val role: String,
    val allowedWingIds: Set<String>,
    val canRegisterStudents: Boolean,
)

sealed interface AccountAccess {
    data object Checking : AccountAccess
    data object SetupRequired : AccountAccess
    data object SignedOut : AccountAccess
    data object PendingApproval : AccountAccess
    data object Revoked : AccountAccess
    data class Approved(val account: ApprovedAccount) : AccountAccess
    data class Error(val message: String) : AccountAccess
}
