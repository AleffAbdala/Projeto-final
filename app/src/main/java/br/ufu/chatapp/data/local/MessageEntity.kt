package br.ufu.chatapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val senderId: String,
    val senderName: String,
    val content: String,
    val mediaUrl: String,
    val mediaName: String,
    val latitude: Double?,
    val longitude: Double?,
    val timestamp: Long,
    val status: String,
    val type: String,
    val isPinned: Boolean
)
