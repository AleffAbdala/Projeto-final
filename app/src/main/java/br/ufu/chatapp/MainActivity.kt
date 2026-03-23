package br.ufu.chatapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import br.ufu.chatapp.ui.ChatAppRoot
import br.ufu.chatapp.ui.theme.ChatTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    companion object {
        private val _pendingConversationId = MutableStateFlow<String?>(null)
        val pendingConversationId = _pendingConversationId.asStateFlow()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumeNotificationIntent()
        setContent {
            ChatTheme {
                ChatAppRoot()
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeNotificationIntent()
    }

    private fun consumeNotificationIntent() {
        _pendingConversationId.value = intent?.getStringExtra("conversationId")
    }

    fun clearPendingConversationId() {
        _pendingConversationId.value = null
    }
}
