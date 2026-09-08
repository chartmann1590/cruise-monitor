package com.cruisewatch.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Loads the static per-cruise-line policy reference data bundled at
 * assets/cruise-line-policies.json — a copy of docs/cruise-line-policies.json
 * from the repo root. Keep them in sync manually when a line's policy
 * changes; there's no build-time sync step yet (see plan/03-phase2-android-app.md).
 */
class PolicyRepository(private val context: Context) {
    private val policies: List<CruiseLinePolicy> by lazy { loadPolicies() }

    fun all(): List<CruiseLinePolicy> = policies

    fun forLine(lineId: String): CruiseLinePolicy? = policies.find { it.id == lineId }

    private fun loadPolicies(): List<CruiseLinePolicy> {
        val json = context.assets.open("cruise-line-policies.json")
            .bufferedReader()
            .use { it.readText() }
        val root = JSONObject(json)
        val lines = root.getJSONArray("lines")
        return (0 until lines.length()).map { i -> parseLine(lines.getJSONObject(i)) }
    }

    private fun parseLine(obj: JSONObject): CruiseLinePolicy = CruiseLinePolicy(
        id = obj.getString("id"),
        displayName = obj.getString("displayName"),
        monitoring = obj.getString("monitoring"),
        phone = obj.optString("phone", ""),
        policies = parsePolicies(obj.getJSONArray("policies")),
        exclusions = parseStringArray(obj.getJSONArray("exclusions")),
        eligibility = obj.getString("eligibility"),
        claimChannel = obj.getString("claimChannel"),
        howToClaim = if (obj.has("howToClaim")) parseStringArray(obj.getJSONArray("howToClaim")) else emptyList(),
    )

    private fun parsePolicies(arr: JSONArray): List<PolicyRule> = (0 until arr.length()).map { i ->
        val p = arr.getJSONObject(i)
        PolicyRule(
            name = p.getString("name"),
            window = p.getString("window"),
            outcome = p.getString("outcome"),
            outcomeDetail = p.optString("outcomeDetail", null.toString()).takeIf { p.has("outcomeDetail") },
            notes = p.optString("notes", null.toString()).takeIf { p.has("notes") },
        )
    }

    private fun parseStringArray(arr: JSONArray): List<String> = (0 until arr.length()).map { arr.getString(it) }
}
