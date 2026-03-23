package br.ufu.chatapp.data.repository

import android.content.Context
import android.os.Build
import android.provider.Settings
import br.ufu.chatapp.data.model.ChatUser
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.ktx.userProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

    fun currentUserId(): String? = auth.currentUser?.uid

    suspend fun login(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password).await()
        ensureUserDocument()
        setUserStatus("online")
        upsertCurrentSession()
    }

    suspend fun register(name: String, email: String, password: String) {
        auth.createUserWithEmailAndPassword(email, password).await()
        auth.currentUser?.updateProfile(userProfileChangeRequest { displayName = name })?.await()
        ensureUserDocument(defaultName = name, defaultEmail = email)
        setUserStatus("online")
        upsertCurrentSession()
    }

    suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email).await()
    }

    suspend fun loginWithGoogleIdToken(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).await()
        ensureUserDocument()
        setUserStatus("online")
        upsertCurrentSession()
    }

    suspend fun loginWithPhoneCredential(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential).await()
        ensureUserDocument()
        setUserStatus("online")
        upsertCurrentSession()
    }

    suspend fun loginWithPhone(verificationId: String, code: String) {
        val credential: PhoneAuthCredential = PhoneAuthProvider.getCredential(verificationId, code)
        loginWithPhoneCredential(credential)
    }

    suspend fun logout() {
        setCurrentSessionActive(false)
        setUserStatus("offline")
        auth.signOut()
    }

    suspend fun logoutOtherSessions() {
        val uid = auth.currentUser?.uid.orEmpty()
        if (uid.isBlank()) return
        val currentSessionId = currentSessionId()
        val sessions = firestore.collection("users").document(uid).collection("sessions").get().await()
        val batch = firestore.batch()
        sessions.documents.filter { it.id != currentSessionId }.forEach { doc ->
            batch.update(doc.reference, mapOf("isActive" to false, "endedAt" to System.currentTimeMillis()))
        }
        batch.commit().await()
    }

    suspend fun updateProfile(name: String, photoUrl: String, status: String) {
        val uid = auth.currentUser?.uid.orEmpty()
        if (uid.isBlank()) return
        firestore.collection("users").document(uid)
            .set(
                mapOf(
                    "name" to name,
                    "photoUrl" to photoUrl,
                    "status" to status,
                    "lastSeen" to System.currentTimeMillis()
                ),
                com.google.firebase.firestore.SetOptions.merge()
            ).await()
    }

    suspend fun currentUserProfile(): ChatUser? {
        val uid = auth.currentUser?.uid.orEmpty()
        if (uid.isBlank()) return null
        return runCatching {
            firestore.collection("users").document(uid)
                .get()
                .await()
                .toObject(ChatUser::class.java)
                ?.copy(id = uid)
        }.getOrNull()
    }

    suspend fun setUserStatus(status: String) {
        val uid = auth.currentUser?.uid.orEmpty()
        if (uid.isBlank()) return
        firestore.collection("users").document(uid)
            .set(
                mapOf("status" to status, "lastSeen" to System.currentTimeMillis()),
                com.google.firebase.firestore.SetOptions.merge()
            )
            .await()
    }

    suspend fun refreshCurrentSession() {
        if (auth.currentUser != null) {
            runCatching { ensureUserDocument() }
            runCatching { upsertCurrentSession() }
        }
    }

    fun observeCurrentSessionActive(): Flow<Boolean> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(true)
            close()
            return@callbackFlow
        }
        val reg = firestore.collection("users").document(uid)
            .collection("sessions")
            .document(currentSessionId())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.getBoolean("isActive") != false)
            }
        awaitClose { reg.remove() }
    }

    suspend fun activeSessionsCount(): Int {
        val uid = auth.currentUser?.uid.orEmpty()
        if (uid.isBlank()) return 0
        return runCatching {
            firestore.collection("users").document(uid)
                .collection("sessions")
                .whereEqualTo("isActive", true)
                .get()
                .await()
                .size()
        }.getOrDefault(0)
    }

    private suspend fun ensureUserDocument(defaultName: String = "", defaultEmail: String = "") {
        val current = auth.currentUser ?: return
        val uid = current.uid
        val ref = firestore.collection("users").document(uid)
        val snapshot = ref.get().await()
        val existingContacts = snapshot.get("contacts") as? List<*> ?: emptyList<Any>()
        val existingPhone = snapshot.getString("phone").orEmpty()
        val existingPhoto = snapshot.getString("photoUrl").orEmpty()
        val existingToken = snapshot.getString("pushToken").orEmpty()
        val currentToken = runCatching { FirebaseMessaging.getInstance().token.await() }.getOrDefault(existingToken)

        ref.set(
            ChatUser(
                id = uid,
                email = current.email ?: defaultEmail,
                name = current.displayName ?: defaultName.ifBlank { current.phoneNumber ?: "Usuário" },
                photoUrl = current.photoUrl?.toString() ?: existingPhoto,
                status = "online",
                phone = current.phoneNumber ?: existingPhone,
                lastSeen = System.currentTimeMillis(),
                contacts = existingContacts.filterIsInstance<String>(),
                pushToken = currentToken
            )
        ).await()
    }

    private suspend fun upsertCurrentSession() {
        val uid = auth.currentUser?.uid.orEmpty()
        if (uid.isBlank()) return
        firestore.collection("users").document(uid)
            .collection("sessions")
            .document(currentSessionId())
            .set(
                mapOf(
                    "sessionId" to currentSessionId(),
                    "deviceName" to "${Build.MANUFACTURER} ${Build.MODEL}",
                    "deviceId" to Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID),
                    "createdAt" to System.currentTimeMillis(),
                    "lastActive" to System.currentTimeMillis(),
                    "isActive" to true
                ),
                com.google.firebase.firestore.SetOptions.merge()
            )
            .await()
    }

    private suspend fun setCurrentSessionActive(active: Boolean) {
        val uid = auth.currentUser?.uid.orEmpty()
        if (uid.isBlank()) return
        firestore.collection("users").document(uid)
            .collection("sessions")
            .document(currentSessionId())
            .set(
                mapOf(
                    "isActive" to active,
                    "endedAt" to if (active) null else System.currentTimeMillis(),
                    "lastActive" to System.currentTimeMillis()
                ),
                com.google.firebase.firestore.SetOptions.merge()
            )
            .await()
    }

    private fun currentSessionId(): String {
        val existing = prefs.getString("session_id", null)
        if (!existing.isNullOrBlank()) return existing
        val created = UUID.randomUUID().toString()
        prefs.edit().putString("session_id", created).apply()
        return created
    }
}
