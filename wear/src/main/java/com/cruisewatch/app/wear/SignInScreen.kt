package com.cruisewatch.app.wear

import android.graphics.Color as AndroidColor
import android.widget.EditText
import android.text.InputType
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
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

    LazyColumn(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    Icons.Filled.Sailing,
                    contentDescription = null,
                    tint = Teal,
                    modifier = Modifier.padding(top = 20.dp).size(22.dp),
                )
                Text(
                    "Sign in to CruiseWatch",
                    style = MaterialTheme.typography.title3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp),
                )
                Chip(
                    onClick = onGoogleSignInClick,
                    label = { Text("Continue with Google") },
                    colors = ChipDefaults.chipColors(backgroundColor = Color.White.copy(alpha = 0.14f)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                )
                if (googleSignInError != null) {
                    Text(
                        googleSignInError,
                        color = MaterialTheme.colors.error,
                        style = MaterialTheme.typography.caption3,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    )
                }
                Text(
                    "or",
                    style = MaterialTheme.typography.caption3,
                    color = Color.LightGray,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                AndroidView(
                    factory = { context ->
                        EditText(context).apply {
                            hint = "Email"
                            setTextColor(AndroidColor.WHITE)
                            setHintTextColor(AndroidColor.LTGRAY)
                            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                            addTextChangedListener { email = it?.toString() ?: "" }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                )
                AndroidView(
                    factory = { context ->
                        EditText(context).apply {
                            hint = "Password"
                            setTextColor(AndroidColor.WHITE)
                            setHintTextColor(AndroidColor.LTGRAY)
                            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                            addTextChangedListener { password = it?.toString() ?: "" }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
                if (error != null) {
                    Text(
                        error ?: "",
                        color = MaterialTheme.colors.error,
                        style = MaterialTheme.typography.caption2,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    )
                }
                Chip(
                    onClick = {
                        isLoading = true
                        error = null
                        auth.signInWithEmailAndPassword(email, password)
                            .addOnSuccessListener(OnSuccessListener { isLoading = false; onSignedIn() })
                            .addOnFailureListener(OnFailureListener { e -> isLoading = false; error = e.message ?: "Sign-in failed" })
                    },
                    label = { Text(if (isLoading) "Signing in…" else "Sign in") },
                    colors = ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                )
            }
        }
    }
}

private fun EditText.addTextChangedListener(onChange: (CharSequence?) -> Unit) {
    addTextChangedListener(object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { onChange(s) }
        override fun afterTextChanged(s: android.text.Editable?) {}
    })
}
