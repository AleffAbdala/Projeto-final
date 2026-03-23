package br.ufu.chatapp.data.repository

import android.net.Uri
import br.ufu.chatapp.data.local.MessageDao
import br.ufu.chatapp.data.local.MessageEntity
import br.ufu.chatapp.data.model.ChatUser
import br.ufu.chatapp.data.model.Conversation
import br.ufu.chatapp.data.model.Message
import br.ufu.chatapp.data.model.MessageStatus
import br.ufu.chatapp.data.model.MessageType
import br.ufu.chatapp.data.remote.SupabaseStorageService
import br.ufu.chatapp.util.Crypto
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.io.IOException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val supabaseStorageService: SupabaseStorageService,
    private val auth: FirebaseAuth,
    private val messageDao: MessageDao
) {
    private fun uid(): String = auth.currentUser?.uid.orEmpty()
    private fun requireUid(): String = uid().ifBlank {
        throw IOException("Usuario nao autenticado. Entre novamente para continuar")
    }

    fun currentUserId(): String = uid()

    fun observeConversations(): Flow<List<Conversation>> = callbackFlow {
        val userId = uid()
        val reg = firestore.collection("conversations")
            .whereArrayContains("participants", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents.orEmpty()
                    .mapNotNull { d -> d.toObject(Conversation::class.java)?.copy(id = d.id) }
                    .sortedByDescending { it.lastMessageTs }
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    fun observeConversation(conversationId: String): Flow<Conversation?> = callbackFlow {
        if (conversationId.isBlank()) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val reg = firestore.collection("conversations").document(conversationId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObject(Conversation::class.java)?.copy(id = snapshot.id))
            }
        awaitClose { reg.remove() }
    }

    fun observeUser(userId: String): Flow<ChatUser?> = callbackFlow {
        if (userId.isBlank()) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val reg = firestore.collection("users").document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObject(ChatUser::class.java)?.copy(id = snapshot.id))
            }
        awaitClose { reg.remove() }
    }

    suspend fun createConversation(name: String, participants: List<String>, isGroup: Boolean, photoUrl: String = ""): String {
        val me = requireUid()
        val allParticipants = (participants + me).distinct()
        val ref = firestore.collection("conversations").document()
        val conversation = Conversation(
            id = ref.id,
            name = if (isGroup) name else name.ifBlank { "Conversa" },
            photoUrl = photoUrl,
            isGroup = isGroup,
            participants = allParticipants,
            adminIds = listOf(me),
            lastMessage = if (isGroup) "Grupo criado" else "Conversa iniciada",
            lastMessageTs = System.currentTimeMillis(),
            unreadCountByUser = allParticipants.associateWith { 0 }
        )
        ref.set(conversation).await()
        return ref.id
    }

    suspend fun getOrCreateDirectConversation(otherUserId: String): String {
        val me = requireUid()
        if (me.isBlank() || otherUserId.isBlank()) return ""

        val existing = firestore.collection("conversations")
            .whereArrayContains("participants", me)
            .get()
            .await()
            .documents
            .firstOrNull { doc ->
                val conversation = doc.toObject(Conversation::class.java)
                conversation != null &&
                    !conversation.isGroup &&
                    conversation.participants.toSet() == setOf(me, otherUserId)
            }

        if (existing != null) return existing.id

        return createConversation(
            name = "Conversa",
            participants = listOf(otherUserId),
            isGroup = false
        )
    }

    suspend fun updateGroup(conversationId: String, name: String, participants: List<String>, photoUrl: String? = null) {
        val me = requireUid()
        val finalParticipants = (participants + me).distinct()
        val updates = mutableMapOf<String, Any>(
            "name" to name,
            "participants" to finalParticipants,
            "unreadCountByUser" to finalParticipants.associateWith { 0 }
        )
        if (photoUrl != null) {
            updates["photoUrl"] = photoUrl
        }
        firestore.collection("conversations").document(conversationId)
            .update(updates)
            .await()
    }

    suspend fun deleteConversation(conversationId: String) {
        val convRef = firestore.collection("conversations").document(conversationId)
        val messages = convRef.collection("messages").get().await()
        messages.documents.forEach { it.reference.delete().await() }
        convRef.delete().await()
        messageDao.clearConversation(conversationId)
    }

    suspend fun deleteMessages(conversationId: String, messageIds: List<String>) {
        if (messageIds.isEmpty()) return
        val convRef = firestore.collection("conversations").document(conversationId)
        val currentPinnedId = convRef.get().await().getString("pinnedMessageId")
        messageIds.forEach { id ->
            convRef.collection("messages").document(id).delete().await()
        }
        if (currentPinnedId in messageIds) {
            convRef.update("pinnedMessageId", null).await()
        }
        messageDao.deleteMessages(conversationId, messageIds)
        refreshConversationPreview(conversationId)
    }

    fun observeMessages(conversationId: String): Flow<List<Message>> = callbackFlow {
        val reg = firestore.collection("conversations").document(conversationId)
            .collection("messages")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val messages = snapshot?.documents.orEmpty()
                    .mapNotNull { d ->
                        d.toObject(Message::class.java)?.copy(
                            id = d.id,
                            content = Crypto.decrypt(d.getString("content").orEmpty())
                        )
                    }
                    .sortedWith(compareBy<Message>({ effectiveMessageTimestamp(it) }, { it.timestamp }, { it.id }))

                trySend(messages)
            }
        awaitClose { reg.remove() }
    }

    fun observeMessagesOffline(conversationId: String): Flow<List<MessageEntity>> {
        return messageDao.observeMessages(conversationId)
    }

    suspend fun sendText(conversationId: String, text: String) {
        val me = requireUid()
        val senderName = senderName(me)
        val participants = participantsForConversation(conversationId)
        val ref = firestore.collection("conversations").document(conversationId)
            .collection("messages").document()

        val message = Message(
            id = ref.id,
            conversationId = conversationId,
            senderId = me,
            senderName = senderName,
            content = Crypto.encrypt(text),
            previewText = text.take(140),
            timestamp = System.currentTimeMillis(),
            type = MessageType.TEXT.name,
            statusByUser = statusMapForParticipants(participants)
        )

        ref.set(messagePayload(message), com.google.firebase.firestore.SetOptions.merge()).await()
        updateConversationAfterSend(conversationId, text, participants)
    }

    suspend fun sendSticker(conversationId: String, sticker: String) {
        val me = requireUid()
        val participants = participantsForConversation(conversationId)
        val ref = firestore.collection("conversations").document(conversationId)
            .collection("messages").document()
        val message = Message(
            id = ref.id,
            conversationId = conversationId,
            senderId = me,
            senderName = senderName(me),
            content = Crypto.encrypt(sticker),
            previewText = sticker.take(40),
            timestamp = System.currentTimeMillis(),
            type = MessageType.STICKER.name,
            statusByUser = statusMapForParticipants(participants)
        )
        ref.set(messagePayload(message), com.google.firebase.firestore.SetOptions.merge()).await()
        updateConversationAfterSend(conversationId, "Sticker", participants)
    }

    suspend fun sendLocation(conversationId: String, lat: Double, lng: Double) {
        val me = requireUid()
        val participants = participantsForConversation(conversationId)
        val ref = firestore.collection("conversations").document(conversationId)
            .collection("messages").document()
        val msg = Message(
            id = ref.id,
            conversationId = conversationId,
            senderId = me,
            senderName = senderName(me),
            content = Crypto.encrypt("Localização compartilhada"),
            previewText = "Localização compartilhada",
            latitude = lat,
            longitude = lng,
            timestamp = System.currentTimeMillis(),
            type = MessageType.LOCATION.name,
            statusByUser = statusMapForParticipants(participants)
        )
        ref.set(messagePayload(msg), com.google.firebase.firestore.SetOptions.merge()).await()
        updateConversationAfterSend(conversationId, "Localização", participants)
    }

    suspend fun sendMedia(conversationId: String, uri: Uri, type: MessageType, mediaName: String = "") {
        val me = requireUid()
        val participants = participantsForConversation(conversationId)
        val downloadUrl = supabaseStorageService.upload(uri, conversationId, type, mediaName)

        val ref = firestore.collection("conversations").document(conversationId)
            .collection("messages").document()

        val msg = Message(
            id = ref.id,
            conversationId = conversationId,
            senderId = me,
            senderName = senderName(me),
            content = Crypto.encrypt(mediaName.ifBlank { type.name }),
            mediaUrl = downloadUrl,
            mediaName = mediaName,
            previewText = previewLabel(type, mediaName),
            timestamp = System.currentTimeMillis(),
            type = type.name,
            statusByUser = statusMapForParticipants(participants)
        )
        ref.set(messagePayload(msg), com.google.firebase.firestore.SetOptions.merge()).await()
        updateConversationAfterSend(conversationId, previewLabel(type, mediaName), participants)
    }

    suspend fun pinMessage(conversationId: String, messageId: String?) {
        val convRef = firestore.collection("conversations").document(conversationId)
        val currentPinnedId = convRef.get().await().getString("pinnedMessageId")

        if (!currentPinnedId.isNullOrBlank()) {
            runCatching {
                convRef.collection("messages").document(currentPinnedId).update("isPinned", false).await()
            }
        }

        convRef.update("pinnedMessageId", messageId).await()

        if (!messageId.isNullOrBlank()) {
            convRef.collection("messages").document(messageId)
                .set(mapOf("isPinned" to true), com.google.firebase.firestore.SetOptions.merge())
                .await()
        }
    }

    suspend fun markAsDelivered(conversationId: String, messages: List<Message>) {
        val me = uid()
        val batch = firestore.batch()
        var hasUpdates = false
        messages.filter { it.senderId != me && (it.statusByUser[me] == null || it.statusByUser[me] == MessageStatus.SENT.name) }
            .forEach { msg ->
                val ref = firestore.collection("conversations").document(conversationId)
                    .collection("messages").document(msg.id)
                batch.update(ref, "statusByUser.$me", MessageStatus.DELIVERED.name)
                hasUpdates = true
            }
        if (hasUpdates) batch.commit().await()
    }

    suspend fun markConversationRead(conversationId: String, messages: List<Message>) {
        val me = uid()
        val batch = firestore.batch()
        var hasUpdates = false
        messages.filter { it.senderId != me && it.statusByUser[me] != MessageStatus.READ.name }
            .forEach { msg ->
                val ref = firestore.collection("conversations").document(conversationId)
                    .collection("messages").document(msg.id)
                batch.update(ref, "statusByUser.$me", MessageStatus.READ.name)
                hasUpdates = true
            }
        val convRef = firestore.collection("conversations").document(conversationId)
        batch.update(convRef, "unreadCountByUser.$me", 0)
        if (hasUpdates || messages.isNotEmpty()) batch.commit().await()
    }

    suspend fun cacheMessages(conversationId: String, messages: List<Message>) {
        val entities = messages.map {
            MessageEntity(
                id = it.id,
                conversationId = conversationId,
                senderId = it.senderId,
                senderName = it.senderName,
                content = it.content,
                mediaUrl = it.mediaUrl,
                mediaName = it.mediaName,
                latitude = it.latitude,
                longitude = it.longitude,
                timestamp = it.timestamp,
                status = aggregateStatus(it),
                type = it.type,
                isPinned = it.isPinned
            )
        }
        messageDao.upsert(entities)
    }

    private suspend fun updateConversationAfterSend(
        conversationId: String,
        preview: String,
        participants: List<String>
    ) {
        val convRef = firestore.collection("conversations").document(conversationId)
        val me = requireUid()
        val updates = mutableMapOf<String, Any>(
            "lastMessage" to preview,
            "lastMessageTs" to System.currentTimeMillis(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        participants.forEach { participant ->
            updates["unreadCountByUser.$participant"] = if (participant == me) {
                0
            } else {
                FieldValue.increment(1)
            }
        }
        convRef.set(updates, com.google.firebase.firestore.SetOptions.merge()).await()
    }

    private suspend fun refreshConversationPreview(conversationId: String) {
        val convRef = firestore.collection("conversations").document(conversationId)
        val conversation = convRef.get().await().toObject(Conversation::class.java)
        val participants = conversation?.participants?.ifEmpty { listOf(requireUid()) } ?: listOf(requireUid())
        val latestMessage = convRef.collection("messages")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .await()
            .documents
            .firstOrNull()
            ?.toObject(Message::class.java)

        val preview = when {
            latestMessage == null && conversation?.isGroup == true -> "Grupo criado"
            latestMessage == null -> "Conversa iniciada"
            latestMessage.type == MessageType.FILE.name -> latestMessage.mediaName.ifBlank { "Arquivo" }
            latestMessage.type == MessageType.IMAGE.name -> "Imagem"
            latestMessage.type == MessageType.VIDEO.name -> "Vídeo"
            latestMessage.type == MessageType.AUDIO.name -> "Áudio"
            latestMessage.type == MessageType.LOCATION.name -> "Localização"
            else -> Crypto.decrypt(latestMessage.content).ifBlank { latestMessage.content }
        }

        convRef.set(
            mapOf(
                "lastMessage" to preview,
                "lastMessageTs" to (latestMessage?.timestamp ?: System.currentTimeMillis()),
                "unreadCountByUser" to participants.associateWith { 0 }
            ),
            com.google.firebase.firestore.SetOptions.merge()
        ).await()
    }

    private suspend fun participantsForConversation(conversationId: String): List<String> {
        val snapshot = firestore.collection("conversations").document(conversationId).get().await()
        val participants = (snapshot.get("participants") as? List<*>)
            ?.mapNotNull { it as? String }
            ?.takeIf { it.isNotEmpty() }
        return participants ?: listOf(requireUid())
    }

    private fun statusMapForParticipants(participants: List<String>): Map<String, String> {
        val me = uid()
        return participants.associateWith { if (it == me) MessageStatus.READ.name else MessageStatus.SENT.name }
    }

    private suspend fun senderName(uid: String): String {
        return runCatching {
            firestore.collection("users").document(uid).get().await().getString("name").orEmpty()
        }.getOrDefault("").ifBlank { "Usuario" }
    }

    private fun aggregateStatus(message: Message): String {
        val me = uid()
        if (message.senderId != me) return message.statusByUser[me] ?: MessageStatus.SENT.name
        val others = message.statusByUser.filterKeys { it != me }.values
        return when {
            others.any { it == MessageStatus.READ.name } -> MessageStatus.READ.name
            others.any { it == MessageStatus.DELIVERED.name } -> MessageStatus.DELIVERED.name
            else -> MessageStatus.SENT.name
        }
    }

    private fun effectiveMessageTimestamp(message: Message): Long {
        return message.serverTimestamp?.toDate()?.time ?: message.timestamp
    }

    private fun messagePayload(message: Message): Map<String, Any?> {
        return mapOf(
            "id" to message.id,
            "conversationId" to message.conversationId,
            "senderId" to message.senderId,
            "senderName" to message.senderName,
            "content" to message.content,
            "mediaUrl" to message.mediaUrl,
            "mediaName" to message.mediaName,
            "previewText" to message.previewText,
            "latitude" to message.latitude,
            "longitude" to message.longitude,
            "timestamp" to message.timestamp,
            "serverTimestamp" to FieldValue.serverTimestamp(),
            "statusByUser" to message.statusByUser,
            "type" to message.type,
            "isPinned" to message.isPinned
        )
    }

    private fun previewLabel(type: MessageType, mediaName: String): String {
        return when (type) {
            MessageType.IMAGE -> "Imagem"
            MessageType.VIDEO -> "Vídeo"
            MessageType.AUDIO -> "Áudio"
            MessageType.FILE -> mediaName.ifBlank { "Arquivo" }
            else -> type.name
        }
    }
}
