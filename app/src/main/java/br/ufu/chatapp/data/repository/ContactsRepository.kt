package br.ufu.chatapp.data.repository

import android.content.ContentResolver
import android.provider.ContactsContract
import br.ufu.chatapp.data.model.ChatUser
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactsRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {
    private fun requireUid(): String = auth.currentUser?.uid.orEmpty().ifBlank {
        throw IOException("Usuario nao autenticado")
    }

    suspend fun addContact(contactUserId: String) {
        val uid = requireUid()
        if (contactUserId.isBlank() || contactUserId == uid) {
            throw IOException("Contato invalido")
        }

        firestore.runBatch { batch ->
            val meRef = firestore.collection("users").document(uid)
            val otherRef = firestore.collection("users").document(contactUserId)
            batch.set(meRef, mapOf("contacts" to FieldValue.arrayUnion(contactUserId)), SetOptions.merge())
            batch.set(otherRef, mapOf("contacts" to FieldValue.arrayUnion(uid)), SetOptions.merge())
        }.await()
    }

    suspend fun removeContact(contactUserId: String) {
        val uid = requireUid()
        if (contactUserId.isBlank() || contactUserId == uid) return

        firestore.runBatch { batch ->
            val meRef = firestore.collection("users").document(uid)
            val otherRef = firestore.collection("users").document(contactUserId)
            batch.set(meRef, mapOf("contacts" to FieldValue.arrayRemove(contactUserId)), SetOptions.merge())
            batch.set(otherRef, mapOf("contacts" to FieldValue.arrayRemove(uid)), SetOptions.merge())
        }.await()
    }

    suspend fun importContactsFromDevice(resolver: ContentResolver): List<ChatUser> {
        val phones = mutableListOf<String>()
        resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            null,
            null,
            null
        )?.use { cursor ->
            val numberColumn = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                val raw = cursor.getString(numberColumn)
                phones += raw.filter { it.isDigit() || it == '+' }
            }
        }

        val normalized = phones
            .map { normalizePhone(it) }
            .filter { it.isNotBlank() }
            .distinct()
        if (normalized.isEmpty()) return emptyList()

        val myId = auth.currentUser?.uid.orEmpty()
        return normalized.chunked(10).flatMap { chunk ->
            firestore.collection("users")
                .whereIn("phone", chunk)
                .get().await()
                .documents
                .mapNotNull { it.toObject(ChatUser::class.java)?.copy(id = it.id) }
        }.distinctBy { it.id }
            .filter { it.id != myId }
    }

    suspend fun searchUsers(query: String): List<ChatUser> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) return loadCurrentContacts()

        return loadCurrentContacts().filter { user ->
            user.name.contains(normalizedQuery, ignoreCase = true) ||
                user.email.contains(normalizedQuery, ignoreCase = true) ||
                user.phone.contains(normalizedQuery, ignoreCase = true)
        }
    }

    suspend fun searchDirectoryUsers(query: String): List<ChatUser> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) return emptyList()

        val myId = requireUid()
        val snapshot = firestore.collection("users")
            .limit(100)
            .get().await()

        return snapshot.documents
            .mapNotNull { it.toObject(ChatUser::class.java)?.copy(id = it.id) }
            .filter { user ->
                user.id != myId && (
                    user.name.contains(normalizedQuery, ignoreCase = true) ||
                    user.email.contains(normalizedQuery, ignoreCase = true) ||
                    user.phone.contains(normalizedQuery, ignoreCase = true)
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    suspend fun loadCurrentContacts(): List<ChatUser> {
        val uid = auth.currentUser?.uid.orEmpty()
        if (uid.isBlank()) return emptyList()
        val me = firestore.collection("users").document(uid).get().await()
        val contactIds = (me.get("contacts") as? List<*>)
            ?.mapNotNull { it as? String }
            .orEmpty()
        return loadUsersByIds(contactIds)
    }

    suspend fun loadUsersByIds(ids: List<String>): List<ChatUser> {
        if (ids.isEmpty()) return emptyList()
        return ids.distinct().chunked(10).flatMap { chunk ->
            firestore.collection("users")
                .whereIn(com.google.firebase.firestore.FieldPath.documentId(), chunk)
                .get().await()
                .documents
                .mapNotNull { it.toObject(ChatUser::class.java)?.copy(id = it.id) }
        }
    }

    private fun normalizePhone(phone: String): String {
        return phone.filter { it.isDigit() || it == '+' }
    }
}
