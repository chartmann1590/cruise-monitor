package com.cruisewatch.app.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** Thin Firestore access layer matching docs/firestore-schema.md's collections. */
class CruiseRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) {
    private fun requireUserId(): String =
        auth.currentUser?.uid ?: error("Not signed in")

    fun trackedCruises(): Flow<List<TrackedCruise>> = callbackFlow {
        val registration = db.collection("trackedCruises")
            .whereEqualTo("userId", requireUserId())
            .whereEqualTo("active", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObjects(TrackedCruise::class.java) ?: emptyList())
            }
        awaitClose { registration.remove() }
    }

    fun priceHistory(cruiseId: String): Flow<List<PriceSnapshot>> = callbackFlow {
        val registration = db.collection("trackedCruises").document(cruiseId)
            .collection("priceSnapshots")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObjects(PriceSnapshot::class.java) ?: emptyList())
            }
        awaitClose { registration.remove() }
    }

    fun alerts(): Flow<List<Alert>> = callbackFlow {
        val registration = db.collection("alerts")
            .whereEqualTo("userId", requireUserId())
            .orderBy("detectedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObjects(Alert::class.java) ?: emptyList())
            }
        awaitClose { registration.remove() }
    }

    suspend fun addTrackedCruise(cruise: TrackedCruise) {
        val withUser = cruise.copy(userId = requireUserId())
        db.collection("trackedCruises").add(withUser).await()
    }

    suspend fun markAlertClaimed(alertId: String) {
        db.collection("alerts").document(alertId)
            .update(mapOf("claimed" to true, "claimedAt" to com.google.firebase.Timestamp.now()))
            .await()
    }

    suspend fun registerFcmToken(token: String) {
        val userId = requireUserId()
        db.collection("users").document(userId)
            .set(mapOf("fcmTokens" to com.google.firebase.firestore.FieldValue.arrayUnion(token)), com.google.firebase.firestore.SetOptions.merge())
            .await()
    }
}
