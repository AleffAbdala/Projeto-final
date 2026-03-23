package br.ufu.chatapp.ui

sealed class Nav(val route: String) {
    data object Login : Nav("login")
    data object Register : Nav("register")
    data object Conversations : Nav("conversations")
    data object Chat : Nav("chat/{conversationId}") {
        fun create(conversationId: String) = "chat/$conversationId"
    }
    data object Contacts : Nav("contacts")
    data object Profile : Nav("profile")
}
