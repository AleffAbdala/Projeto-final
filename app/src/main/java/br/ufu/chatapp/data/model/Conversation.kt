package br.ufu.chatapp.data.model

data class Conversation(
    val id: String = "",
    val name: String = "",
    val photoUrl: String = "",
    val isGroup: Boolean = false,
    val participants: List<String> = emptyList(),
    val adminIds: List<String> = emptyList(),
    val lastMessage: String = "",
    val lastMessageTs: Long = 0L,
    val pinnedMessageId: String? = null,
    val unreadCountByUser: Map<String, Int> = emptyMap()
)
