package com.cruisewatch.app.data

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import java.util.Date

/** Mirrors docs/firestore-schema.md's trackedCruises/{cruiseId}. */
data class TrackedCruise(
    @DocumentId val id: String = "",
    val userId: String = "",
    val line: String = "",
    val ship: String = "",
    val sailDate: String = "",
    val cabinCategory: String = "",
    /**
     * True if this is a Guarantee (GTY) stateroom — you don't pick an exact
     * room, and pricing/eligibility for price-drop policies works
     * differently for these. Check your confirmation for a code ending in
     * "GTY" (e.g. Royal Caribbean's "ZI GTY") to tell.
     *
     * Explicit @PropertyName is required here: Firestore's POJO mapper
     * strips a leading "is" off Boolean property names by Java Beans
     * convention (Kotlin generates an `isGuarantee()` getter for a Boolean
     * named `isGuarantee`, which the mapper then treats as a property
     * called "guarantee") — without this annotation, reads/writes silently
     * target the wrong field name and the checkbox has no effect.
     */
    @get:PropertyName("isGuarantee") val isGuarantee: Boolean = false,
    val farePaid: Double = 0.0,
    val currency: String = "USD",
    val finalPaymentDate: String = "",
    val bookingChannel: String = "direct",
    val active: Boolean = true,
)

/** Mirrors trackedCruises/{cruiseId}/priceSnapshots/{snapshotId}. */
data class PriceSnapshot(
    @DocumentId val id: String = "",
    val timestamp: Date? = null,
    val fare: Double = 0.0,
    val source: String = "",
)

/** Mirrors alerts/{alertId}. */
data class Alert(
    @DocumentId val id: String = "",
    val userId: String = "",
    val cruiseId: String = "",
    /** Denormalized from the tracked cruise, so the app can show that line's claim instructions without a join. */
    val line: String = "",
    val policyId: String = "",
    val currentFare: Double = 0.0,
    val farePaid: Double = 0.0,
    val dropAmount: Double = 0.0,
    val detectedAt: Date? = null,
    val claimed: Boolean = false,
    val claimedAt: Date? = null,
)

/** Mirrors docs/cruise-line-policies.json, bundled in assets/cruise-line-policies.json. */
data class CruiseLinePolicy(
    val id: String = "",
    val displayName: String = "",
    val monitoring: String = "",
    val phone: String = "",
    val policies: List<PolicyRule> = emptyList(),
    val exclusions: List<String> = emptyList(),
    val eligibility: String = "",
    @get:PropertyName("claimChannel") val claimChannel: String = "",
    val howToClaim: List<String> = emptyList(),
)

data class PolicyRule(
    val name: String = "",
    val window: String = "",
    val outcome: String = "",
    val outcomeDetail: String? = null,
    val notes: String? = null,
)

/** The 5 cruise lines covered — id must match docs/cruise-line-policies.json / the scraper's registry. */
enum class CruiseLine(val id: String, val displayName: String) {
    ROYAL_CARIBBEAN("royal_caribbean", "Royal Caribbean International"),
    CARNIVAL("carnival", "Carnival Cruise Line"),
    PRINCESS("princess", "Princess Cruises"),
    CELEBRITY("celebrity", "Celebrity Cruises"),
    NORWEGIAN("norwegian", "Norwegian Cruise Line (NCL)"),
}

/** Cabin categories our scraper understands (see scraper/src/lines/rccl-shared.ts::toCabinClassType). */
val CABIN_CATEGORIES = listOf("Interior", "Ocean View", "Balcony", "Concierge", "Aqua", "Suite")
