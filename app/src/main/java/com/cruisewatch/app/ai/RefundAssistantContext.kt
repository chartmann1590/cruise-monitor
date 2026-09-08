package com.cruisewatch.app.ai

import com.cruisewatch.app.data.Alert
import com.cruisewatch.app.data.CruiseLinePolicy
import com.cruisewatch.app.data.TrackedCruise

/**
 * Builds the system prompt for the refund assistant, scoped strictly to helping the user
 * claim a price-drop refund — grounded in their real tracked cruises, alerts, and each cruise
 * line's actual claim policy so the model doesn't have to guess or hallucinate steps.
 */
object RefundAssistantContext {
    fun buildSystemPrompt(
        cruises: List<TrackedCruise>,
        alerts: List<Alert>,
        policyFor: (String) -> CruiseLinePolicy?,
    ): String {
        val sb = StringBuilder()
        sb.append(
            "You are the CruiseWatch Refund Assistant, built into a cruise fare price-drop tracking app. " +
                "Your ONLY job is helping this specific user get money back (refund, onboard credit, or upgrade) " +
                "when their cruise fare drops below what they paid, using their cruise line's real price-protection policy. " +
                "Do not answer questions unrelated to their cruises, refunds, or claim process — politely redirect back to " +
                "that topic if asked something else. Be concise, encouraging, and give concrete next steps (what to say, " +
                "who to call, what deadline applies). Use the real data below; never invent a policy detail not given here.\n\n",
        )

        if (cruises.isEmpty()) {
            sb.append("The user has no cruises tracked yet. Encourage them to add one from the Cruises tab.\n")
            return sb.toString()
        }

        sb.append("== Their tracked cruises ==\n")
        cruises.forEach { cruise ->
            sb.append(
                "- ${cruise.ship} (${cruise.line}), ${cruise.cabinCategory}" +
                    (if (cruise.isGuarantee) " Guarantee/GTY" else "") +
                    ", sailing ${cruise.sailDate}, paid ${cruise.currency} ${"%.2f".format(cruise.farePaid)}, " +
                    "final payment due ${cruise.finalPaymentDate}\n",
            )
        }

        val unclaimed = alerts.filter { !it.claimed }
        if (unclaimed.isNotEmpty()) {
            sb.append("\n== Active price drops (not yet claimed) ==\n")
            unclaimed.forEach { alert ->
                val ship = cruises.firstOrNull { it.id == alert.cruiseId }?.ship ?: "their cruise"
                sb.append(
                    "- $ship: price dropped $${"%.2f".format(alert.dropAmount)} " +
                        "(now $${"%.2f".format(alert.currentFare)}, they paid $${"%.2f".format(alert.farePaid)}), " +
                        "applies under policy \"${alert.policyId}\"\n",
                )
            }
        } else {
            sb.append("\nNo active undetected price drops right now — if asked, tell them CruiseWatch is monitoring and will alert them.\n")
        }

        val linesInvolved = (cruises.map { it.line } + unclaimed.map { alert ->
            cruises.firstOrNull { it.id == alert.cruiseId }?.line
        }).filterNotNull().distinct()

        if (linesInvolved.isNotEmpty()) {
            sb.append("\n== Claim process for their cruise line(s) ==\n")
            linesInvolved.forEach { lineId ->
                val policy = policyFor(lineId) ?: return@forEach
                sb.append("${policy.displayName} — call ${policy.phone}. Eligibility: ${policy.eligibility}\n")
                policy.howToClaim.forEachIndexed { i, step -> sb.append("  ${i + 1}. $step\n") }
                if (policy.exclusions.isNotEmpty()) {
                    sb.append("  Excludes: ${policy.exclusions.joinToString(", ")}\n")
                }
            }
        }

        return sb.toString()
    }
}
