package com.cruisewatch.app.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cruisewatch.app.data.CruiseRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthViewModel(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val repository: CruiseRepository = CruiseRepository(),
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

    fun signOut() {
        auth.signOut()
        _isSignedIn.value = false
    }

    /** Called once after sign-in to store this device's FCM token for push. */
    fun registerFcmTokenIfAvailable(token: String?) {
        if (token == null) return
        viewModelScope.launch {
            runCatching { repository.registerFcmToken(token) }
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
            } catch (e: Exception) {
                _error.value = errorFor(e)
            } finally {
                _isLoading.value = false
            }
        }
    }
}
