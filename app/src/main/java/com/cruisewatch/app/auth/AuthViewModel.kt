package com.cruisewatch.app.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cruisewatch.app.data.CruiseRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthViewModel(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val repository: CruiseRepository = CruiseRepository(),
    private val messaging: FirebaseMessaging = FirebaseMessaging.getInstance(),
) : ViewModel() {

    private val _isSignedIn = MutableStateFlow(auth.currentUser != null)
    val isSignedIn: StateFlow<Boolean> = _isSignedIn.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun signIn(email: String, password: String) = runAuthAction(
        // Deliberately generic: Firebase distinguishes "no such user" from
        // "wrong password" in its own exception message, which lets an
        // attacker enumerate registered emails if surfaced verbatim.
        errorFor = { e ->
            if (e is FirebaseAuthInvalidUserException || e is FirebaseAuthInvalidCredentialsException) {
                "Invalid email or password"
            } else {
                e.message ?: "Something went wrong"
            }
        },
        block = { auth.signInWithEmailAndPassword(email, password).await() },
    )

    fun signUp(email: String, password: String) = runAuthAction {
        auth.createUserWithEmailAndPassword(email, password).await()
    }

    fun signInWithGoogle(idToken: String) = runAuthAction {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).await()
    }

    fun setGoogleSignInError(message: String) {
        _error.value = message
    }

    /** Called when switching between sign-in and create-account mode, so an error from the mode you left doesn't linger in the one you're entering. */
    fun clearError() {
        _error.value = null
    }

    val currentUserEmail: String?
        get() = auth.currentUser?.email

    fun signOut() {
        auth.signOut()
        _isSignedIn.value = false
    }

    fun deleteAccount(onResult: (Result<Unit>) -> Unit) {
        val user = auth.currentUser
        if (user == null) {
            onResult(Result.failure(IllegalStateException("Not signed in")))
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.deleteAllUserData()
                user.delete().await()
                _isSignedIn.value = false
                onResult(Result.success(Unit))
            } catch (e: Exception) {
                onResult(Result.failure(e))
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Called from MainActivity whenever a fresh token arrives (initial
     * fetch, or FCM rotating it). Only succeeds once actually signed in;
     * silently no-ops otherwise (registerFcmTokenAfterSignIn covers the
     * "just signed in" case instead, since a token fetched before sign-in
     * would otherwise never get registered).
     */
    fun registerFcmTokenIfAvailable(token: String?) {
        if (token == null) return
        viewModelScope.launch {
            runCatching { repository.registerFcmToken(token) }
        }
    }

    /**
     * Fetches the current FCM token and registers it, called right after a
     * successful sign-in. Needed because MainActivity's own token fetch
     * (on app launch) can complete before the user has finished signing
     * in — that attempt silently fails ("Not signed in") and is never
     * retried, so a fresh sign-in would otherwise register no token and
     * never receive push notifications until the token happens to rotate.
     */
    private fun registerFcmTokenAfterSignIn() {
        viewModelScope.launch {
            runCatching {
                val token = messaging.token.await()
                repository.registerFcmToken(token)
            }
        }
    }

    private fun runAuthAction(
        errorFor: (Exception) -> String = { it.message ?: "Something went wrong" },
        block: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                block()
                _isSignedIn.value = true
                registerFcmTokenAfterSignIn()
            } catch (e: Exception) {
                _error.value = errorFor(e)
            } finally {
                _isLoading.value = false
            }
        }
    }
}
