package com.cruisewatch.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cruisewatch.app.R
import com.cruisewatch.app.data.CruiseLinePolicy
import com.cruisewatch.app.ui.CallButton
import com.cruisewatch.app.ui.PhotoHero
import com.cruisewatch.app.ui.theme.brandFor

/** Browse every covered line's price-protection policy, even without an active alert. */
@Composable
fun ClaimsScreen(policies: List<CruiseLinePolicy>) {
    LazyColumn {
        item {
            PhotoHero(photoRes = R.drawable.hero_policies, height = 190.dp) {
                Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Bottom) {
                    Text("Policies", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                    Text(
                        "Every covered line's price-protection rules, in plain English.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.9f),
                    )
                }
            }
        }
        items(policies, key = { it.id }) { policy ->
            PolicyCard(policy)
        }
    }
}

@Composable
private fun PolicyCard(policy: CruiseLinePolicy) {
    val brand = brandFor(policy.id)
    Card(
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().background(brand.gradient).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(brand.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
            Text(
                policy.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                policy.monitoring,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            if (policy.howToClaim.isNotEmpty()) {
                Text("How to get your refund", style = MaterialTheme.typography.titleSmall)
                policy.howToClaim.forEachIndexed { index, step ->
                    Row(modifier = Modifier.padding(top = 8.dp)) {
                        Text(
                            "${index + 1}.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Text(step, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (policy.phone.isNotBlank()) {
                    CallButton(policy.phone, modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
                }
            }

            Text(
                "Policy details",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
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
        }
    }
}
