package com.cruisewatch.app.wear

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.tasks.await

/** Same Firestore collections the phone app reads — the watch is just another authenticated client. */
class WearRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) {
    fun trackedCruises(): Flow<List<TrackedCruise>> = callbackFlow<List<TrackedCruise>> {
        val userId = auth.currentUser?.uid ?: run { close(); return@callbackFlow }
        val registration = db.collection("trackedCruises")
            .whereEqualTo("userId", userId)
            .whereEqualTo("active", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                trySend(snapshot?.toObjects(TrackedCruise::class.java) ?: emptyList())
            }
        awaitClose { registration.remove() }
    }.catch { emit(emptyList()) }

    fun unclaimedAlerts(): Flow<List<Alert>> = callbackFlow<List<Alert>> {
        val userId = auth.currentUser?.uid ?: run { close(); return@callbackFlow }
        val registration = db.collection("alerts")
            .whereEqualTo("userId", userId)
            .whereEqualTo("claimed", false)
            .orderBy("detectedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                trySend(snapshot?.toObjects(Alert::class.java) ?: emptyList())
            }
        awaitClose { registration.remove() }
    }.catch { emit(emptyList()) }

    /** Full alert history (claimed + unclaimed) for one cruise, shown when you tap it. */
    fun alertHistory(cruiseId: String): Flow<List<Alert>> = callbackFlow<List<Alert>> {
        val userId = auth.currentUser?.uid ?: run { close(); return@callbackFlow }
        val registration = db.collection("alerts")
            .whereEqualTo("userId", userId)
            .whereEqualTo("cruiseId", cruiseId)
            .orderBy("detectedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                trySend(snapshot?.toObjects(Alert::class.java) ?: emptyList())
            }
        awaitClose { registration.remove() }
    }.catch { emit(emptyList()) }

    suspend fun registerFcmToken(token: String) {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId)
            .set(mapOf("fcmTokens" to FieldValue.arrayUnion(token)), SetOptions.merge())
            .await()
    }
}
