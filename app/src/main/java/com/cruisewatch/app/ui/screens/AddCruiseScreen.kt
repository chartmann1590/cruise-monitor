package com.cruisewatch.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cruisewatch.app.data.CABIN_CATEGORIES
import com.cruisewatch.app.data.CruiseLine
import com.cruisewatch.app.data.TrackedCruise

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCruiseScreen(onSave: (TrackedCruise) -> Unit) {
    var line by remember { mutableStateOf(CruiseLine.ROYAL_CARIBBEAN) }
    var ship by remember { mutableStateOf("") }
    var sailDate by remember { mutableStateOf("") }
    var cabinCategory by remember { mutableStateOf(CABIN_CATEGORIES.first()) }
    var isGuarantee by remember { mutableStateOf(false) }
    var farePaid by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("USD") }
    var finalPaymentDate by remember { mutableStateOf("") }

    var lineMenuExpanded by remember { mutableStateOf(false) }
    var cabinMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("Add a cruise you've already booked", style = MaterialTheme.typography.titleLarge)
        Text(
            "We'll watch the public fare and alert you if it drops within your line's policy window.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )

        ExposedDropdownMenuBox(expanded = lineMenuExpanded, onExpandedChange = { lineMenuExpanded = it }) {
            OutlinedTextField(
                value = line.displayName,
                onValueChange = {},
                readOnly = true,
                label = { Text("Cruise line") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = lineMenuExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
            )
            DropdownMenu(
                expanded = lineMenuExpanded,
                onDismissRequest = { lineMenuExpanded = false },
            ) {
                CruiseLine.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.displayName) },
                        onClick = { line = option; lineMenuExpanded = false },
                    )
                }
            }
        }

        OutlinedTextField(
            value = ship,
            onValueChange = { ship = it },
            label = { Text("Ship name (as shown on the line's website)") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )

        OutlinedTextField(
            value = sailDate,
            onValueChange = { sailDate = it },
            label = { Text("Sail date (YYYY-MM-DD)") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )

        ExposedDropdownMenuBox(expanded = cabinMenuExpanded, onExpandedChange = { cabinMenuExpanded = it }) {
            OutlinedTextField(
                value = cabinCategory,
                onValueChange = {},
                readOnly = true,
                label = { Text("Cabin category") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = cabinMenuExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor().padding(top = 8.dp),
            )
            DropdownMenu(
                expanded = cabinMenuExpanded,
                onDismissRequest = { cabinMenuExpanded = false },
            ) {
                CABIN_CATEGORIES.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = { cabinCategory = option; cabinMenuExpanded = false },
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        ) {
            Checkbox(checked = isGuarantee, onCheckedChange = { isGuarantee = it })
            Column {
                Text("This is a Guarantee (GTY) stateroom", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "You didn't pick an exact room — check your confirmation for a code ending in \"GTY\".",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        OutlinedTextField(
            value = farePaid,
            onValueChange = { farePaid = it },
            label = { Text("Fare paid (cruise fare only, no taxes/fees)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )

        OutlinedTextField(
            value = currency,
            onValueChange = { currency = it.uppercase() },
            label = { Text("Currency (e.g. USD)") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )

        OutlinedTextField(
            value = finalPaymentDate,
            onValueChange = { finalPaymentDate = it },
            label = { Text("Final payment date (YYYY-MM-DD)") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )

        val fareValue = farePaid.toDoubleOrNull()
        val isValid = ship.isNotBlank() && sailDate.isNotBlank() && finalPaymentDate.isNotBlank() && fareValue != null

        Button(
            onClick = {
                onSave(
                    TrackedCruise(
                        line = line.id,
                        ship = ship,
                        sailDate = sailDate,
                        cabinCategory = cabinCategory,
                        isGuarantee = isGuarantee,
                        farePaid = fareValue ?: 0.0,
                        currency = currency,
                        finalPaymentDate = finalPaymentDate,
                    ),
                )
            },
            enabled = isValid,
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        ) {
            Text("Start tracking")
        }
    }
}
