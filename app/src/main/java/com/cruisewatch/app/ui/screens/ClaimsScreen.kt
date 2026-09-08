package com.cruisewatch.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cruisewatch.app.data.CruiseLinePolicy

/** Browse every covered line's price-protection policy, even without an active alert. */
@Composable
fun ClaimsScreen(policies: List<CruiseLinePolicy>) {
    LazyColumn {
        items(policies, key = { it.id }) { policy ->
            PolicyCard(policy)
        }
    }
}

@Composable
private fun PolicyCard(policy: CruiseLinePolicy) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(policy.displayName, style = MaterialTheme.typography.titleMedium)
            Text(
                policy.monitoring,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )

            policy.policies.forEach { rule ->
                Text(rule.name, style = MaterialTheme.typography.labelLarge)
                rule.notes?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 6.dp))
                }
                rule.outcomeDetail?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 6.dp))
                }
            }

            if (policy.exclusions.isNotEmpty()) {
                Text(
                    "Excludes: ${policy.exclusions.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Text(
                "Eligibility: ${policy.eligibility}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                "Claim: ${policy.claimChannel}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
