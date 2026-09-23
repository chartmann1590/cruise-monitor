package com.cruisewatch.crosspromo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CrosspromoApp(
    val packageName: String,
    val name: String? = null,
    val iconUrl: String? = null,
    val storeUrl: String? = null,
    val shortDescription: String? = null,
    val rating: Double? = null,
    val ratingCount: Int? = null,
    val installText: String? = null,
    val estimatedInstalls: Long? = null,
    val category: String? = null,
    val priceText: String? = null,
    val isFree: Boolean? = null,
    val developer: String? = null,
    val firstDiscoveredAt: String? = null,
    val lastSeenAt: String? = null,
    val enabled: Boolean? = null,
    val promotionMultiplier: Double? = null,
    val selectionType: String? = null,
    val rankPosition: Int? = null,
)

@Serializable
data class RecommendationResponse(
    val version: Int,
    val requestId: String,
    val generatedAt: String? = null,
    val expiresAt: String? = null,
    val apps: List<CrosspromoApp> = emptyList(),
)

@Serializable
data class HealthResponse(
    val status: String,
    val catalogApps: Int? = null,
    @SerialName("lastCatalogRefresh") val lastCatalogRefresh: String? = null,
    @SerialName("lastSuccessfulRefresh") val lastSuccessfulRefresh: String? = null,
    @SerialName("cacheStatus") val cacheStatus: String? = null,
    @SerialName("lastDiscoverySource") val lastDiscoverySource: String? = null,
)

@Serializable
data class EventPayload(
    val event: String,
    val sourcePackage: String,
    val targetPackage: String,
    val placement: String,
    val rankPosition: Int? = null,
    val selectionType: String? = null,
    val sessionId: String? = null,
    val recommendationRequestId: String? = null,
    val sdkVersion: String? = null,
)

@Serializable
data class EventResponse(
    val received: Int,
    val rejected: Int,
)
