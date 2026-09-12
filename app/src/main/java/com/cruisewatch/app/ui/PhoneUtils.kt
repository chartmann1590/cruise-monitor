package com.cruisewatch.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cruisewatch.app.R
import com.cruisewatch.app.i18n.tr

/** A "Call {phone}" button that opens the phone dialer pre-filled with the number. */
@Composable
fun CallButton(phone: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Button(
        onClick = {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
            context.startActivity(intent)
        },
        modifier = modifier,
    ) {
        Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
        Text(tr(R.string.phone_call_button, phone))
    }
}
