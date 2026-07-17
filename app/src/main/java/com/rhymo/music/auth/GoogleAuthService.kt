package com.rhymo.music.auth

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.rhymo.music.R
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** Credential Manager → Google ID token → Firebase Authentication. */
class GoogleAuthService(private val activity: Activity) {
    suspend fun signIn(): Result<FirebaseUser> = runCatching {
        if (FirebaseApp.getApps(activity).isEmpty()) {
            error("Firebase setup pending: add app/google-services.json")
        }

        val serverClientId = activity.getString(R.string.default_web_client_id)

        // This is an explicit button flow, so always open Google's account
        // chooser instead of querying only credentials already available to
        // Credential Manager.
        val googleIdOption = GetSignInWithGoogleOption.Builder(
            serverClientId = serverClientId
        )
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        val credential = CredentialManager.create(activity)
            .getCredential(activity, request)
            .credential
        check(credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "The selected credential is not a Google account."
        }
        val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
        val firebaseCredential = GoogleAuthProvider.getCredential(googleCredential.idToken, null)

        suspendCancellableCoroutine { continuation ->
            FirebaseAuth.getInstance().signInWithCredential(firebaseCredential)
                .addOnCompleteListener { task ->
                    if (!continuation.isActive) return@addOnCompleteListener
                    val user = task.result?.user
                    if (task.isSuccessful && user != null) continuation.resume(user)
                    else continuation.resumeWith(Result.failure(task.exception ?: IllegalStateException("Google sign-in failed")))
                }
        }
    }.recoverCatching { failure ->
        if (failure is NoCredentialException) {
            error(
                "No Google account is available. Add a Google account in phone Settings, " +
                    "update Google Play services, then try again."
            )
        }
        throw failure
    }

    fun currentUser(): FirebaseUser? =
        if (FirebaseApp.getApps(activity).isEmpty()) null else FirebaseAuth.getInstance().currentUser

    fun signOut() {
        if (FirebaseApp.getApps(activity).isNotEmpty()) FirebaseAuth.getInstance().signOut()
    }
}
