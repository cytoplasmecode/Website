package com.cytoplasmecode.plantwatering.auth

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope

class GoogleAuthManager(private val context: Context) {

    private val client: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(CALENDAR_SCOPE))
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    fun getSignInIntent(): Intent = client.signInIntent

    fun handleSignInResult(data: Intent?, onResult: (Boolean) -> Unit) {
        GoogleSignIn.getSignedInAccountFromIntent(data)
            .addOnSuccessListener { account -> onResult(hasCalendarPermission(account)) }
            .addOnFailureListener { onResult(false) }
    }

    fun hasCalendarPermission(account: GoogleSignInAccount): Boolean =
        GoogleSignIn.hasPermissions(account, Scope(CALENDAR_SCOPE))

    fun signOut(onComplete: () -> Unit) {
        client.signOut().addOnCompleteListener { onComplete() }
    }

    companion object {
        const val CALENDAR_SCOPE = "https://www.googleapis.com/auth/calendar"
    }
}
