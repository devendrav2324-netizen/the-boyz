package com.hostelattendance.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

private const val DESIGNATED_ADMIN_UID = "ZcAp0gt5OVSC2UeiVDqBmAGjhXD2"
private const val DEFAULT_HOSTEL_ID = "cgitr-boys-hostel"

class FirebaseAccountRepository(context: Context) {
    private val appContext = context.applicationContext
    private val credentialManager = CredentialManager.create(appContext)
    private val mutableAccess = MutableStateFlow<AccountAccess>(AccountAccess.Checking)
    val access: StateFlow<AccountAccess> = mutableAccess

    private val firebaseConfigured: Boolean
        get() = FirebaseApp.getApps(appContext).isNotEmpty()

    private val webClientId: String?
        get() {
            val resourceId = appContext.resources.getIdentifier(
                "default_web_client_id",
                "string",
                appContext.packageName,
            )
            if (resourceId == 0) return null
            return appContext.getString(resourceId).takeIf(String::isNotBlank)
        }

    suspend fun refreshAccess() {
        if (!isConfigured()) {
            mutableAccess.value = AccountAccess.SetupRequired
            return
        }
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            mutableAccess.value = AccountAccess.SignedOut
            return
        }
        mutableAccess.value = AccountAccess.Checking

        val isDesignatedAdmin = user.uid == DESIGNATED_ADMIN_UID

        try {
            user.getIdToken(true).await()

            val snapshot = try {
                FirebaseFirestore.getInstance()
                    .collection("user_profiles")
                    .document(user.uid)
                    .get()
                    .await()
            } catch (e: Exception) {
                null
            }

            if (isDesignatedAdmin) {
                grantDesignatedAdmin(user.uid)
                return
            }

            // Check if Firestore specifies a role
            var role = snapshot?.getString("role")?.trim()
            var hostelId = snapshot?.getString("hostelId")?.trim() ?: DEFAULT_HOSTEL_ID
            if (hostelId == "cgitr-boys") hostelId = DEFAULT_HOSTEL_ID

            var canRegisterStudents = snapshot?.getBoolean("canRegisterStudents")
            val isActive = snapshot?.getBoolean("isActive") ?: true

            if (!isActive) {
                mutableAccess.value = AccountAccess.Revoked
                return
            }

            // Fallback check: If user profile in user_profiles is missing role, check hostel doc
            if (role == null || !role.equals("ADMIN", ignoreCase = true)) {
                try {
                    val hostelSnap = FirebaseFirestore.getInstance()
                        .collection("hostels")
                        .document(hostelId)
                        .get()
                        .await()
                    val hostelRole = hostelSnap?.getString("role")?.trim()
                    if (hostelRole.equals("ADMIN", ignoreCase = true) || hostelSnap?.getBoolean("approved") == true) {
                        role = "ADMIN"
                        if (canRegisterStudents == null) {
                            canRegisterStudents = hostelSnap?.getBoolean("canRegisterStudents") ?: true
                        }
                    }
                } catch (_: Exception) {}
            }

            val finalRole = role ?: "USER"
            val isAdminRole = finalRole.equals("ADMIN", ignoreCase = true)
            val wings = snapshot?.get("allowedWingIds") as? List<*> ?: emptyList<Any>()
            val effectiveCanRegister = if (isAdminRole) (canRegisterStudents != false) else false

            // Save or sync user profile to Firestore
            if (snapshot == null || !snapshot.exists() || (isAdminRole && snapshot.getString("role") != "ADMIN")) {
                try {
                    FirebaseFirestore.getInstance()
                        .collection("user_profiles")
                        .document(user.uid)
                        .set(
                            mapOf(
                                "hostelId" to hostelId,
                                "role" to if (isAdminRole) "ADMIN" else "USER",
                                "email" to (user.email ?: ""),
                                "displayName" to (user.displayName ?: ""),
                                "approved" to true,
                                "isActive" to true,
                                "allowedWingIds" to emptyList<String>(),
                                "canRegisterStudents" to effectiveCanRegister,
                                "createdAtMillis" to System.currentTimeMillis(),
                            ),
                            com.google.firebase.firestore.SetOptions.merge(),
                        )
                } catch (_: Exception) {}
            }

            mutableAccess.value = AccountAccess.Approved(
                ApprovedAccount(
                    uid = user.uid,
                    hostelId = hostelId,
                    role = if (isAdminRole) "ADMIN" else "USER",
                    allowedWingIds = wings.filterIsInstance<String>().toSet(),
                    canRegisterStudents = effectiveCanRegister,
                ),
            )
        } catch (exception: Exception) {
            if (isDesignatedAdmin) {
                grantDesignatedAdmin(user.uid)
            } else {
                // If offline / network error, allow access as standard USER
                mutableAccess.value = AccountAccess.Approved(
                    ApprovedAccount(
                        uid = user.uid,
                        hostelId = DEFAULT_HOSTEL_ID,
                        role = "USER",
                        allowedWingIds = emptySet(),
                        canRegisterStudents = false,
                    ),
                )
            }
        }
    }

    private fun grantDesignatedAdmin(uid: String) {
        val adminAccount = ApprovedAccount(
            uid = uid,
            hostelId = DEFAULT_HOSTEL_ID,
            role = "ADMIN",
            allowedWingIds = emptySet(),
            canRegisterStudents = true,
        )
        mutableAccess.value = AccountAccess.Approved(adminAccount)

        try {
            FirebaseFirestore.getInstance()
                .collection("user_profiles")
                .document(uid)
                .set(
                    mapOf(
                        "hostelId" to DEFAULT_HOSTEL_ID,
                        "role" to "ADMIN",
                        "approved" to true,
                        "allowedWingIds" to emptyList<String>(),
                        "canRegisterStudents" to true,
                        "isActive" to true,
                    ),
                    com.google.firebase.firestore.SetOptions.merge(),
                )
        } catch (_: Exception) {}
    }

    fun isConfigured(): Boolean = firebaseConfigured && webClientId != null

    suspend fun signInWithGoogle(presentationContext: Context): AccountAccess {
        val clientId = webClientId
        if (!firebaseConfigured || clientId == null) {
            mutableAccess.value = AccountAccess.SetupRequired
            return mutableAccess.value
        }
        mutableAccess.value = AccountAccess.Checking
        return try {
            val googleOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(false)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleOption)
                .build()
            val activityContext = presentationContext.findActivity()
            val result = credentialManager.getCredential(activityContext, request)
            val credential = result.credential
            if (credential !is CustomCredential
                || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                error("Google sign-in returned an unsupported credential.")
            }
            val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val firebaseCredential = GoogleAuthProvider.getCredential(googleCredential.idToken, null)
            FirebaseAuth.getInstance().signInWithCredential(firebaseCredential).await()
            refreshAccess()
            mutableAccess.value
        } catch (exception: GetCredentialException) {
            val access = AccountAccess.Error(
                exception.localizedMessage ?: "Google sign-in was cancelled or could not start.",
            )
            mutableAccess.value = access
            access
        } catch (exception: Exception) {
            val access = AccountAccess.Error(
                exception.localizedMessage ?: "Google sign-in failed.",
            )
            mutableAccess.value = access
            access
        }
    }

    suspend fun signOut() {
        try {
            if (firebaseConfigured) FirebaseAuth.getInstance().signOut()
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
            mutableAccess.value = if (isConfigured()) AccountAccess.SignedOut else AccountAccess.SetupRequired
        } catch (exception: Exception) {
            mutableAccess.value = AccountAccess.Error(
                exception.localizedMessage ?: "Could not complete sign-out.",
            )
        }
    }

    private fun Context.findActivity(): Context {
        var cur = this
        while (cur is android.content.ContextWrapper) {
            if (cur is android.app.Activity) return cur
            cur = cur.baseContext
        }
        return this
    }
}
