package br.ufu.chatapp.data.model

import com.google.firebase.Timestamp

enum class MessageStatus {
    SENT,
    DELIVERED,
    READ
}

enum class MessageType {
    TEXT,
    IMAGE,
    VIDEO,
    AUDIO,
    FILE,
    LOCATION,
    STICKER,
    SYSTEM
}

data class Message(
    val id: String = "",
    val conversationId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val content: String = "",
    val mediaUrl: String = "",
    val mediaName: String = "",
    val previewText: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val serverTimestamp: Timestamp? = null,
    val statusByUser: Map<String, String> = emptyMap(),
    val type: String = MessageType.TEXT.name,
    val isPinned: Boolean = false
)
