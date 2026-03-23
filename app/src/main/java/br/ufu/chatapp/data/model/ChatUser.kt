package br.ufu.chatapp.data.model

data class ChatUser(
    val id: String = "",
    val email: String = "",
    val name: String = "",
    val photoUrl: String = "",
    val status: String = "offline",
    val phone: String = "",
    val lastSeen: Long = System.currentTimeMillis(),
    val contacts: List<String> = emptyList(),
    val pushToken: String = ""
)
