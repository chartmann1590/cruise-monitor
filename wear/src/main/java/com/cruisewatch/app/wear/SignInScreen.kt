package com.cruisewatch.app.wear

import android.app.RemoteInput
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sailing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.input.RemoteInputIntentHelper
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.firebase.auth.FirebaseAuth

@Composable
fun WearSignInScreen(
    auth: FirebaseAuth = FirebaseAuth.getInstance(),
    onSignedIn: () -> Unit,
    onGoogleSignInClick: () -> Unit = {},
    googleSignInError: String? = null,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val emailLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data ?: return@rememberLauncherForActivityResult
        val results = RemoteInput.getResultsFromIntent(data)
        val input = results?.getCharSequence("email")?.toString()?.trim()
        if (!input.isNullOrBlank()) {
            email = input
        }
    }

    val passwordLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data ?: return@rememberLauncherForActivityResult
        val results = RemoteInput.getResultsFromIntent(data)
        val input = results?.getCharSequence("password")?.toString()
        if (!input.isNullOrBlank()) {
            password = input
        }
    }

    fun launchEmailInput() {
        val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
        val remoteInputs = listOf(
            RemoteInput.Builder("email")
                .setLabel("Enter email")
                .build(),
        )
        RemoteInputIntentHelper.putRemoteInputsExtra(intent, remoteInputs)
        emailLauncher.launch(intent)
    }

    fun launchPasswordInput() {
        val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
        val remoteInputs = listOf(
            RemoteInput.Builder("password")
                .setLabel("Enter password")
                .build(),
        )
        RemoteInputIntentHelper.putRemoteInputsExtra(intent, remoteInputs)
        passwordLauncher.launch(intent)
    }

    val listState = rememberScalingLazyListState()

    Scaffold(
        positionIndicator = {
            PositionIndicator(scalingLazyListState = listState)
        },
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            autoCentering = AutoCenteringParams(itemIndex = 0),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Icon(
                    Icons.Filled.Sailing,
                    contentDescription = null,
                    tint = Teal,
                    modifier = Modifier.size(24.dp),
                )
            }
            item {
                Text(
                    "Sign in to CruiseWatch",
                    style = MaterialTheme.typography.title3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
            item {
                Chip(
                    onClick = onGoogleSignInClick,
                    label = {
                        Text(
                            "Continue with Google",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    colors = ChipDefaults.chipColors(backgroundColor = Color.White.copy(alpha = 0.14f)),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                )
            }
            if (googleSignInError != null) {
                item {
                    Text(
                        googleSignInError,
                        color = MaterialTheme.colors.error,
                        style = MaterialTheme.typography.caption3,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                    )
                }
            }
            item {
                Text(
                    "or",
                    style = MaterialTheme.typography.caption3,
                    color = Color.LightGray,
                )
            }
            item {
                Chip(
                    onClick = { launchEmailInput() },
                    label = {
                        Text(
                            if (email.isBlank()) "Enter email" else email,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    secondaryLabel = { Text("Email") },
                    icon = { Icon(Icons.Filled.Email, contentDescription = null) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                )
            }
            item {
                Chip(
                    onClick = { launchPasswordInput() },
                    label = {
                        Text(
                            if (password.isBlank()) "Enter password" else "••••••••",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    secondaryLabel = { Text("Password") },
                    icon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                )
            }
            if (error != null) {
                item {
                    Text(
                        error ?: "",
                        color = MaterialTheme.colors.error,
                        style = MaterialTheme.typography.caption2,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                    )
                }
            }
            item {
                Chip(
                    onClick = {
                        if (email.isBlank() || password.isBlank()) {
                            error = "Enter email and password"
                            return@Chip
                        }
                        isLoading = true
                        error = null
                        auth.signInWithEmailAndPassword(email, password)
                            .addOnSuccessListener(OnSuccessListener {
                                isLoading = false
                                onSignedIn()
                            })
                            .addOnFailureListener(OnFailureListener { e ->
                                isLoading = false
                                error = e.message ?: "Sign-in failed"
                            })
                    },
                    label = { Text(if (isLoading) "Signing in…" else "Sign in") },
                    colors = ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                )
            }
        }
    }
}
